package com.rajasudhan.taskmind.data.source.understanding

import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Suggestion
import com.rajasudhan.taskmind.data.source.SuggestionNotifier
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.first
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
    private val notifier: SuggestionNotifier
) {
    suspend fun processText(source: String, text: String) {
        // Cheap pre-filter: skip obvious non-actionable noise (OTPs, promos, opt-outs)
        // before spending battery/LLM cycles on it.
        if (text.isBlank() || ExtractionHeuristics.isLikelyNoise(text)) return

        val currentDateTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        val dayOfWeek = currentDateTime.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val dateTimeStr = "${currentDateTime.format(formatter)}. Today is a $dayOfWeek."

        val prompt = SystemPrompt.INSTRUCTION
            .replace("{{CURRENT_DATETIME}}", dateTimeStr)
            .replace("{{SOURCE_TEXT}}", text)

        var jsonResult = llmProvider.generate(prompt, text)
        var parsedResult = tryParse(jsonResult)

        if (parsedResult == null) {
            val retryPrompt = "your previous reply was not valid JSON, return only the JSON object\n$jsonResult"
            jsonResult = llmProvider.generate(prompt, retryPrompt)
            parsedResult = tryParse(jsonResult)
        }

        val items = parsedResult?.items ?: return

        var insertedAny = false
        for (item in items) {
            if (ExtractionHeuristics.isAcceptable(item) && !isDuplicate(item)) {
                val suggestion = Suggestion(
                    source = source,
                    rawSnippet = text,
                    extractedTitle = item.title,
                    dueDate = ExtractionHeuristics.sanitizeDate(item.dueDate),
                    dueTime = ExtractionHeuristics.sanitizeTime(item.dueTime),
                    type = item.type,
                    confidence = item.confidence,
                    status = "pending"
                )
                dao.insertSuggestion(suggestion)
                insertedAny = true
            }
        }

        // Surface a single, self-updating "N suggestions to review" notification.
        if (insertedAny) {
            notifier.notifyPending(dao.getPendingSuggestions().first().size)
        }
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
