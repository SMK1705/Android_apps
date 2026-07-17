package com.rajasudhan.taskmind.data.source.understanding

import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.data.source.embedding.SemanticIndex
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ask TaskMind (#128): answers a natural-language question or command about the user's saved items,
 * fully on-device. The LLM ONLY classifies the utterance into a small [AskIntent] (query/create);
 * everything else — running it against Room, ranking, phrasing the answer — is deterministic here, so
 * a Gemma-class model can't hallucinate items. A low-confidence/unparseable classification, or a
 * structured query with no hits on a content question, degrades to a keyword+semantic SEARCH so the
 * user always gets a real answer built from real rows.
 */
@Singleton
class AskEngine @Inject constructor(
    private val llmProvider: LlmProvider,
    private val moshi: Moshi,
    private val dao: TaskMindDao,
    private val pipeline: UnderstandingPipeline,
    private val semanticIndex: SemanticIndex,
) {
    private val adapter by lazy { moshi.adapter(AskIntent::class.java) }

    /**
     * [now] is injectable so date-window resolution is testable. [previous] is the intent behind the
     * last answer, if any — handed to the classifier as context so a short follow-up ("what about next
     * week?") refines that query instead of being classified blind.
     */
    suspend fun ask(
        utterance: String,
        now: LocalDateTime = LocalDateTime.now(),
        previous: AskIntent? = null,
    ): AskResult {
        if (utterance.isBlank()) return AskResult("Ask me about your tasks and notes.", kind = AskResultKind.EMPTY)
        val intent = classify(utterance, now, previous)
        return when {
            intent?.action == "create" && !intent.text.isNullOrBlank() -> create(intent.text)
            // A real query intent. A "create" with no text is a failed classification, NOT a match-all
            // query — fall through to search so we never answer "here's everything" to a dropped slot.
            intent != null && intent.action != "create" -> query(intent, now.toLocalDate(), utterance)
            else -> search(utterance) // unparseable reply, model unavailable, or a textless create
        }
    }

    private suspend fun classify(utterance: String, now: LocalDateTime, previous: AskIntent?): AskIntent? {
        val system = AskPrompt.INSTRUCTION.replace("{{CURRENT_DATETIME}}", datetimeLine(now))
        // A follow-up only means anything against the last question, so give the model that intent as
        // context. The prompt decides whether to refine it or treat the new line as a fresh topic —
        // merging here deterministically couldn't tell those apart.
        val user =
            if (previous == null) utterance
            else "Previous question intent: ${adapter.toJson(previous)}\nNew question: $utterance"
        return runCatching {
            val json = ExtractionHeuristics.stripJsonFences(llmProvider.generateIntent(system, user))
            val obj = JSONObject(json)
            // Cloud pins the flat intent schema; on-device is free-form. Tolerate an accidental {items:[…]} wrap.
            val flat = obj.optJSONArray("items")?.optJSONObject(0) ?: obj
            adapter.fromJson(flat.toString())
        }.getOrNull()
    }

    private suspend fun create(text: String): AskResult {
        // Reuse the single capture pipeline; the new item lands in the Inbox for the user to approve.
        pipeline.processText("Ask TaskMind", text, seedSchedule = true)
        return AskResult("Added “${text.trim()}” to your Inbox to review.", kind = AskResultKind.CREATED)
    }

    private suspend fun query(intent: AskIntent, today: LocalDate, utterance: String): AskResult {
        // A query intent with NO slots the filter can use (the model failed to turn a content question
        // like "what did the electrician quote?" into a keyword) would match EVERY note via
        // AskQuery.matches and dump the whole list. It's really a content ask — degrade to keyword+semantic
        // search on the utterance (this is the fall-through RoutingLlmProvider's EMPTY_INTENT relies on).
        if (!AskQuery.hasAnySlot(intent)) return search(utterance)
        val base = if (isDone(intent)) dao.getCompletedNotes().first() else dao.getActiveNotes().first()
        val matched = base.filter { AskQuery.matches(it, intent, today) }
        if (matched.isNotEmpty()) {
            return AskResult(
                answerFor(intent, matched.size) + truncationNote(matched.size),
                order(intent, matched).take(RESULT_LIMIT),
                intent = intent,
            )
        }
        // Nothing matched. If the ask leaned on structured slots, say so plainly ("nothing due today");
        // if it was essentially a content/keyword ask, fall through to search rather than a false "clear".
        val structured = intent.type != null || intent.tag != null || intent.window != null || intent.status != null
        // Carry the intent even on a miss — "nothing due today" -> "what about tomorrow?" must refine it.
        return if (structured) AskResult(emptyAnswerFor(intent), kind = AskResultKind.EMPTY, intent = intent)
        else search(utterance)
    }

    private suspend fun search(utterance: String): AskResult {
        val results = rank(utterance, dao.getActiveNotes().first())
        return when {
            results.isEmpty() -> AskResult("I couldn't find anything matching that.", kind = AskResultKind.EMPTY)
            results.size > RESULT_LIMIT ->
                AskResult("Found ${results.size} matches — showing the closest $RESULT_LIMIT:", results.take(RESULT_LIMIT))
            else -> AskResult("Here's what I found:", results)
        }
    }

    /**
     * Structured hits arrive in DAO (insertion) order, which is meaningless to the user. A content
     * keyword means relevance should win — that's what fuses the slots with ranking, so "overdue Work
     * tasks about the electrician" leads with the electrician. Otherwise a date-shaped ask reads best
     * chronologically, with undated items last.
     */
    private suspend fun order(intent: AskIntent, matched: List<Note>): List<Note> {
        val keyword = intent.keyword?.trim()
        if (!keyword.isNullOrBlank()) {
            // Every row already contains the keyword (AskQuery.matches), so rank() keeps them all and
            // simply sorts by semantic closeness on top of the lexical hit.
            val ranked = rank(keyword, matched)
            if (ranked.size == matched.size) return ranked
        }
        return matched.sortedWith(
            compareBy({ it.dueDate.isNullOrBlank() }, { it.dueDate ?: "" }, { it.title.lowercase() })
        )
    }

    /** The answer states the true count, so say plainly when the cards below are only the first slice. */
    private fun truncationNote(total: Int): String =
        if (total > RESULT_LIMIT) " Showing the first $RESULT_LIMIT." else ""

    /** Keyword (lexical) hits first, then semantically-close notes — the same recipe as Notes search. */
    private suspend fun rank(query: String, base: List<Note>): List<Note> {
        val scores = semanticIndex.scores(query, SemanticIndex.SEARCH_FLOOR)
        val ql = query.trim().lowercase()
        fun lexical(n: Note) =
            n.title.lowercase().contains(ql) || n.summary.lowercase().contains(ql) || n.body.lowercase().contains(ql)
        return base.mapNotNull { n ->
            val sem = scores[n.id] ?: 0f
            when {
                lexical(n) -> n to (1f + sem)
                sem >= SemanticIndex.SEARCH_FLOOR -> n to sem
                else -> null
            }
        }.sortedByDescending { it.second }.map { it.first }
    }

    // ---- answer phrasing (deterministic; never invents content, only counts + the slot labels) ----

    private fun answerFor(intent: AskIntent, count: Int): String =
        "Found $count${doneWord(intent)} ${noun(intent, count)}${windowPhrase(intent)}."

    private fun emptyAnswerFor(intent: AskIntent): String =
        "No${doneWord(intent)} ${noun(intent, 2)}${windowPhrase(intent)} — you're all clear."

    private fun isDone(intent: AskIntent) = intent.status?.trim()?.lowercase() == "done"

    private fun doneWord(intent: AskIntent) = if (isDone(intent)) " finished" else ""

    private fun noun(intent: AskIntent, count: Int): String {
        val plural = count != 1
        return when (AskQuery.canonicalType(intent.type)) {
            "todo" -> if (plural) "tasks" else "task"
            "reminder" -> if (plural) "reminders" else "reminder"
            "note" -> if (plural) "notes" else "note"
            "waiting_on" -> if (plural) "waiting-on items" else "waiting-on item"
            else -> if (plural) "items" else "item"
        }
    }

    private fun windowPhrase(intent: AskIntent): String = when (intent.window?.trim()?.lowercase()) {
        "today" -> " due today"
        "tomorrow" -> " due tomorrow"
        "overdue" -> " overdue"
        "this_week", "week", "this week" -> " due this week"
        "this_weekend", "weekend", "this weekend" -> " due this weekend"
        "upcoming", "future" -> " coming up"
        else -> ""
    }

    private fun datetimeLine(now: LocalDateTime): String {
        val stamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
        val dow = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        return "$stamp. Today is a $dow."
    }

    private companion object {
        const val RESULT_LIMIT = 12
    }
}
