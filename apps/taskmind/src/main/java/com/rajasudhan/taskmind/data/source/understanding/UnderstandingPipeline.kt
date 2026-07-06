package com.rajasudhan.taskmind.data.source.understanding

import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Suggestion
import com.rajasudhan.taskmind.data.source.NaturalDate
import com.rajasudhan.taskmind.data.source.ParsedSchedule
import com.rajasudhan.taskmind.data.source.PhoneUtil
import com.rajasudhan.taskmind.data.source.RejectionLearner
import com.rajasudhan.taskmind.data.source.SuggestionNotifier
import com.rajasudhan.taskmind.data.source.embedding.SemanticIndex
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns raw source text (an SMS, a notification, an email) into pending [Suggestion]s via the LLM.
 * The filtering/sanitization heuristics live in [ExtractionHeuristics] (pure, unit-tested); this
 * class wires them to the model call and the DB. Nothing here is written to Notes — only pending
 * suggestions, which the user approves in the Inbox.
 */
@Singleton
class UnderstandingPipeline @Inject constructor(
    private val llmProvider: LlmProvider,
    private val moshi: Moshi,
    private val dao: TaskMindDao,
    private val notifier: SuggestionNotifier,
    private val rejectionLearner: RejectionLearner,
    private val semanticIndex: SemanticIndex
) {
    // Serializes every check-then-insert (the isDuplicate → insertSuggestion region) so two paths
    // handling the same item at once can't both pass the dedup and insert twins. This is real: the
    // live SmsObserver and the periodic RecentDataScanner can hand the same SMS to processText
    // concurrently, and two missed-call notifications (a chat app re-posting) can hit addCallback at
    // once. Held only around the fast DB check+insert — never around the slow LLM call.
    private val insertMutex = Mutex()

    private companion object {
        // Truncate runaway inputs (a long email/transcript) so the prompt + JSON output stay under
        // the on-device model's 2048-token KV-cache. ~4 chars/token leaves ample headroom.
        const val MAX_INPUT_CHARS = 4000

        // Confidence for a reminder synthesized from the deterministic parse alone — well above the
        // acceptance bar, since the user typed it and the schedule is parsed deterministically.
        private const val FALLBACK_CONFIDENCE = 0.9
    }

    suspend fun processText(source: String, text: String, seedSchedule: Boolean = false) {
        // Cheap pre-filter: skip obvious non-actionable noise (OTPs, promos, opt-outs)
        // before spending battery/LLM cycles on it.
        if (text.isBlank() || ExtractionHeuristics.isLikelyNoise(text)) return

        val currentDateTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        val dayOfWeek = currentDateTime.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val dateTimeStr = "${currentDateTime.format(formatter)}. Today is a $dayOfWeek."

        // Static instruction (cacheable); the variable text + its origin go in the user message so
        // the model has context ("Notification from Amma" vs "Voice note") and we don't send the
        // body twice. Cap very long inputs so we stay under the on-device 2048-token KV-cache.
        val systemInstruction = SystemPrompt.INSTRUCTION.replace("{{CURRENT_DATETIME}}", dateTimeStr)
        val body = if (text.length > MAX_INPUT_CHARS) text.take(MAX_INPUT_CHARS) else text
        val userMessage = "Source: $source\n\nText:\n$body"

        var jsonResult = llmProvider.generate(systemInstruction, userMessage)
        var parsedResult = tryParse(jsonResult)

        if (parsedResult == null) {
            // The cloud path enforces a JSON schema, so this mainly catches the on-device model
            // occasionally replying in prose. Re-ask with a stronger nudge (not the bad output).
            val retryMessage = "$userMessage\n\nReturn ONLY the JSON object described above, nothing else."
            jsonResult = llmProvider.generate(systemInstruction, retryMessage)
            parsedResult = tryParse(jsonResult)
        }

        // #116: for a user-typed capture, run the deterministic on-device date parse once.
        val parsed = if (seedSchedule) NaturalDate.parse(text, currentDateTime).takeIf { !it.isEmpty } else null

        // If the extractor produced nothing (it's unavailable — no on-device model and no cloud key — or
        // it saw no task) but the user typed a clear DATE, still stand up a reminder from the parse alone,
        // so capture works with extraction disabled. A date anchors it; a bare time/recurrence is too
        // ambiguous to auto-create. The parse's spans strip the date phrase out for a clean title.
        val llmItems = parsedResult?.items ?: emptyList()
        val items = if (parsed?.date != null && llmItems.isEmpty()) listOf(fallbackItem(text, parsed)) else llmItems
        if (items.isEmpty()) return

        // A deterministic parse OVERRIDES the LLM's date/time/recurrence (the model is unreliable at
        // relative-date math). Gated to a single item so a parsed date isn't smeared across a multi-task
        // brain-dump; the synthesized fallback item is itself a single item already built from the parse.
        val seeded = parsed?.takeIf { items.size == 1 }

        // Down-rank items from senders the user keeps rejecting (on-device learning).
        val penalty = rejectionLearner.confidencePenalty(source)

        // Serialize the dedup-check + insert so a concurrent invocation (the periodic scanner racing
        // the live observer over the same SMS) can't read a twin-free snapshot before this one
        // inserts. The slow LLM call above stays outside the lock.
        var insertedAny = false
        insertMutex.withLock {
            for (item in items) {
                val scored = if (penalty > 0) item.copy(confidence = (item.confidence - penalty).coerceAtLeast(0.0)) else item
                // Reconcile the deterministic seed with the LLM's fields into ONE coherent schedule:
                //  - date: the parsed date wins (deterministic beats the model's shaky relative-date math);
                //  - time: the parsed time only overrides when it came WITH a parsed date, so a stray bare
                //    time can't clobber the model's coherent (date,time) pair or strand a past-due slot;
                //  - recurrence: only seeded onto a schedulable item, so a plain note can't silently repeat.
                val seededDate = seeded?.dueDate()
                val effectiveDate = seededDate ?: ExtractionHeuristics.sanitizeDate(item.dueDate)
                val effectiveTime = if (seededDate != null) seeded?.dueTime() ?: ExtractionHeuristics.sanitizeTime(item.dueTime)
                    else ExtractionHeuristics.sanitizeTime(item.dueTime) ?: seeded?.dueTime()
                val effectiveRecurrence = seeded?.recurrence?.takeIf { item.type == "reminder" || item.type == "todo" }
                    ?: ExtractionHeuristics.sanitizeRecurrence(item.recurrence)
                // Completion-based (#124) rides on the deterministic parse's "!" marker only, and only
                // when a repeat actually stuck onto a schedulable item — never inferred from the LLM.
                val effectiveRepeatFromCompletion = seeded?.repeatFromCompletion == true &&
                    effectiveRecurrence != null && (item.type == "reminder" || item.type == "todo")
                if (ExtractionHeuristics.isAcceptable(scored) && !isDuplicate(item, effectiveDate)) {
                    val suggestion = Suggestion(
                        source = source,
                        rawSnippet = text,
                        extractedTitle = item.title,
                        summary = item.notes.trim(),
                        dueDate = effectiveDate,
                        dueTime = effectiveTime,
                        type = item.type,
                        confidence = scored.confidence,
                        status = "pending",
                        location = item.location?.trim()?.ifBlank { null },
                        recurrence = effectiveRecurrence,
                        priority = ExtractionHeuristics.sanitizePriority(item.priority),
                        counterparty = item.counterparty?.trim()?.ifBlank { null },
                        tags = ExtractionHeuristics.sanitizeTags(item.tags),
                        // Safe dedup (#145): a NEAR-duplicate is kept but flagged for review, never dropped.
                        // Cheap under the lock with the hashing embedder (microseconds); if a slow neural
                        // embedder is ever wired, compute this OUTSIDE insertMutex to keep the lock short.
                        possibleDuplicateOf = possibleDuplicateOf(item, effectiveDate),
                        repeatFromCompletion = effectiveRepeatFromCompletion
                    )
                    dao.insertSuggestion(suggestion)
                    insertedAny = true
                }
            }
        }

        // Surface a single, self-updating review notification (top item + Approve/Reject actions).
        if (insertedAny) {
            notifier.notifyPending()
        }
    }

    /**
     * Creates a "call back" suggestion for a missed call directly, bypassing the LLM — the model
     * tends to drop a bare missed call as non-actionable, which is why these never showed up.
     *
     * Two callers feed this:
     *  - the cellular call log, which has the real [number] (and often a cached contact name); the
     *    Call button dials that number.
     *  - a missed-call notification from a chat app (WhatsApp, Telegram, …), which carries only the
     *    caller's [displayName] and no [number]; the Call button resolves the name via Contacts.
     *
     * Needs at least a dialable number or a name; deduped by title so a re-scan (or a notification
     * the app keeps re-posting) doesn't pile up the same call-back.
     */
    suspend fun addCallback(displayName: String?, number: String?, source: String = "Missed call") {
        // A name we can look up/show: not blank, not itself a number, not an email (some services
        // post missed-call notifications titled with the account email — "Call back you@gmail.com"
        // can't be dialed).
        val named = displayName?.trim()
            ?.takeIf { it.isNotBlank() && PhoneUtil.extractFirst(it) == null && !it.contains('@') }
        val dialable = number?.let(PhoneUtil::normalize)?.takeIf { it.count(Char::isDigit) >= 5 }
        if (named == null && dialable == null) return // nothing to call back

        val who = named ?: dialable!!
        val title = "Call back $who"

        insertMutex.withLock {
            val pending = dao.getPendingSuggestions().first().map { it.extractedTitle to it.dueDate }
            val notes = dao.getAllNotes().first().map { it.title to it.dueDate }
            if (ExtractionHeuristics.isDuplicate(title, null, pending + notes)) return

            dao.insertSuggestion(
                Suggestion(
                    source = source,
                    rawSnippet = buildString {
                        append("Missed call")
                        if (named != null) append(" from $named")
                        if (dialable != null) append(" ($dialable)")
                    },
                    extractedTitle = title,
                    summary = if (dialable != null) "Missed call · $dialable" else "Missed call",
                    dueDate = null,
                    dueTime = null,
                    type = "todo",
                    confidence = 0.95,
                    status = "pending"
                )
            )
        }
        notifier.notifyPending()
    }

    private fun tryParse(json: String): LlmResponse? {
        val cleanedJson = ExtractionHeuristics.stripJsonFences(json)
        return try {
            moshi.adapter(LlmResponse::class.java).fromJson(cleanedJson)
        } catch (e: Exception) {
            // The whole-object parse is all-or-nothing: one malformed element (a null title, a
            // string where a number is expected) makes Moshi throw and would discard every good item
            // in an otherwise-fine multi-item extraction. Salvage the well-formed items individually.
            salvageItems(cleanedJson)
        }
    }

    /**
     * Best-effort recovery when the strict [LlmResponse] parse fails: walk the `items` array and adapt
     * each element on its own, keeping the ones that parse and dropping only the malformed ones.
     *
     * Returns null — so [processText] still falls through to its stronger-nudge retry — unless at
     * least one salvaged item is actually *acceptable*. Gating on acceptability (not mere JSON
     * parseability) preserves the pre-salvage behaviour: a strict-parse failure that yields nothing
     * worth keeping (prose, a broken payload, or only sub-threshold items) must retry, rather than be
     * silently satisfied by an unusable salvage and drop a task the retry would have captured.
     */
    private fun salvageItems(cleanedJson: String): LlmResponse? = try {
        val arr = JSONObject(cleanedJson).optJSONArray("items") ?: return null
        val itemAdapter = moshi.adapter(LlmItem::class.java)
        val items = (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { obj -> runCatching { itemAdapter.fromJson(obj.toString()) }.getOrNull() }
        }
        // Pass the full list on (the insert loop still scores/dedups it); null when nothing's usable.
        if (items.any { ExtractionHeuristics.isAcceptable(it) }) LlmResponse(items) else null
    } catch (e: Exception) {
        null
    }

    /**
     * A reminder synthesized purely from the deterministic parse — used when the extractor yields no
     * items but the user typed a clear date (#116). The parse's spans strip the date phrase out so the
     * title is just the task ("call mom tomorrow 5pm" → "call mom"). High confidence: it's user-typed and
     * deterministic. It still lands as a PENDING suggestion for review, so the user can reject it.
     */
    private fun fallbackItem(text: String, parsed: ParsedSchedule): LlmItem = LlmItem(
        type = "reminder",
        title = NaturalDate.stripSchedule(text, parsed.spans).ifBlank { text.trim() },
        dueDate = parsed.dueDate(),
        dueTime = parsed.dueTime(),
        recurrence = parsed.recurrence,
        confidence = FALLBACK_CONFIDENCE,
    )

    private suspend fun isDuplicate(item: LlmItem, dueDate: String?): Boolean {
        // Compare (title, dueDate) against existing pending suggestions and approved notes on [dueDate] —
        // the EFFECTIVE date the suggestion is actually stored with (seeded parse if any, else the
        // sanitized LLM date). Keying on a different value than it's stored under lets an item re-insert
        // its own twin (the raw-vs-sanitized bug, and — once #116 seeds dates — the LLM-vs-seeded bug).
        val pending = dao.getPendingSuggestions().first().map { it.extractedTitle to it.dueDate }
        val notes = dao.getAllNotes().first().map { it.title to it.dueDate }
        return ExtractionHeuristics.isDuplicate(item.title, dueDate, pending + notes)
    }

    /**
     * Safe semantic dedup (#145): the title of an existing NOTE or pending SUGGESTION this capture is
     * likely a re-capture of, or null. NEVER drops the capture — it only produces the in-Inbox
     * "possible duplicate" flag. Semantic similarity finds candidates ([SemanticIndex]); the strict
     * lexical guard ([NearDuplicate]) confirms them, so a genuinely different-but-similar item ("Call
     * Bob" vs "Call Alice") is never flagged. Only runs on items that already cleared the exact
     * (title, date) dedup above — so a true re-scan is dropped and only a *variant* is ever flagged.
     */
    private suspend fun possibleDuplicateOf(item: LlmItem, dueDate: String?): String? {
        val candidate = semanticIndex.textFor(item.title, item.notes)
        // (a) Against approved notes: semantic pre-filter, then the lexical guard confirms.
        val scored = semanticIndex.scores(candidate, SemanticIndex.SEARCH_FLOOR)
        if (scored.isNotEmpty()) {
            dao.getAllNotes().first()
                .filter { it.id in scored && !it.completed }
                .firstOrNull { NearDuplicate.isLikelyDuplicate(candidate, dueDate, semanticIndex.textFor(it.title, it.summary), it.dueDate) }
                ?.let { return it.title }
        }
        // (b) Against other pending suggestions (not embedded) — a pure lexical check catches two
        //     channels capturing the same thing before either is approved.
        return dao.getPendingSuggestions().first()
            .firstOrNull { NearDuplicate.isLikelyDuplicate(candidate, dueDate, semanticIndex.textFor(it.extractedTitle, it.summary), it.dueDate) }
            ?.extractedTitle
    }
}
