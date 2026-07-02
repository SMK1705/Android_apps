package com.rajasudhan.taskmind.data.source.understanding

import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Suggestion
import com.rajasudhan.taskmind.data.source.PhoneUtil
import com.rajasudhan.taskmind.data.source.RejectionLearner
import com.rajasudhan.taskmind.data.source.SuggestionNotifier
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.first
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
    private val rejectionLearner: RejectionLearner
) {
    // Serializes the check-then-insert in addCallback so two missed-call notifications arriving at
    // once (a chat app re-posting its missed-call notification) can't both pass the dedup and create
    // twin "Call back" cards.
    private val callbackMutex = Mutex()

    private companion object {
        // Truncate runaway inputs (a long email/transcript) so the prompt + JSON output stay under
        // the on-device model's 2048-token KV-cache. ~4 chars/token leaves ample headroom.
        const val MAX_INPUT_CHARS = 4000
    }

    suspend fun processText(source: String, text: String) {
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

        val items = parsedResult?.items ?: return

        // Down-rank items from senders the user keeps rejecting (on-device learning).
        val penalty = rejectionLearner.confidencePenalty(source)

        var insertedAny = false
        for (item in items) {
            val scored = if (penalty > 0) item.copy(confidence = (item.confidence - penalty).coerceAtLeast(0.0)) else item
            if (ExtractionHeuristics.isAcceptable(scored) && !isDuplicate(item)) {
                val suggestion = Suggestion(
                    source = source,
                    rawSnippet = text,
                    extractedTitle = item.title,
                    summary = item.notes.trim(),
                    dueDate = ExtractionHeuristics.sanitizeDate(item.dueDate),
                    dueTime = ExtractionHeuristics.sanitizeTime(item.dueTime),
                    type = item.type,
                    confidence = scored.confidence,
                    status = "pending",
                    location = item.location?.trim()?.ifBlank { null },
                    recurrence = ExtractionHeuristics.sanitizeRecurrence(item.recurrence),
                    priority = ExtractionHeuristics.sanitizePriority(item.priority)
                )
                dao.insertSuggestion(suggestion)
                insertedAny = true
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

        callbackMutex.withLock {
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
            null
        }
    }

    private suspend fun isDuplicate(item: LlmItem): Boolean {
        // Compare (title, dueDate) against existing pending suggestions and approved notes.
        val pending = dao.getPendingSuggestions().first().map { it.extractedTitle to it.dueDate }
        val notes = dao.getAllNotes().first().map { it.title to it.dueDate }
        return ExtractionHeuristics.isDuplicate(item.title, item.dueDate, pending + notes)
    }
}
