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

@Singleton
class UnderstandingPipeline @Inject constructor(
    private val llmProvider: LlmProvider,
    private val moshi: Moshi,
    private val dao: TaskMindDao,
    private val notifier: SuggestionNotifier
) {
    companion object {
        // Only keep suggestions the model is reasonably sure about.
        private const val MIN_CONFIDENCE = 0.6

        // Drop malformed dates/times (e.g. a datetime stuffed into due_time).
        private val DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")
        private val TIME_REGEX = Regex("""\d{1,2}:\d{2}""")

        // One-time codes, marketing, and opt-out boilerplate are never action items.
        private val NOISE_PATTERNS = listOf(
            Regex("(verification|one[- ]?time|security|login|otp)\\b.{0,20}code", RegexOption.IGNORE_CASE),
            Regex("\\bcode\\b.{0,20}\\b\\d{4,8}\\b", RegexOption.IGNORE_CASE),
            Regex("\\b\\d{4,8}\\b.{0,20}\\bcode\\b", RegexOption.IGNORE_CASE),
            Regex("do not share", RegexOption.IGNORE_CASE),
            Regex("reply stop|opt[- ]?out|unsubscribe", RegexOption.IGNORE_CASE),
            Regex("% off|\\bsale\\b|\\bdeal(s)?\\b|coupon|promo|cashback", RegexOption.IGNORE_CASE)
        )
    }

    suspend fun processText(source: String, text: String) {
        // Cheap pre-filter: skip obvious non-actionable noise (OTPs, promos, opt-outs)
        // before spending battery/LLM cycles on it.
        if (text.isBlank() || isLikelyNoise(text)) return

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
            if (item.title.isNotBlank() && item.confidence >= MIN_CONFIDENCE && !isDuplicate(item)) {
                val suggestion = Suggestion(
                    source = source,
                    rawSnippet = text,
                    extractedTitle = item.title,
                    dueDate = item.dueDate?.takeIf { it.matches(DATE_REGEX) },
                    dueTime = item.dueTime?.takeIf { it.matches(TIME_REGEX) },
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

    private fun isLikelyNoise(text: String): Boolean = NOISE_PATTERNS.any { it.containsMatchIn(text) }

    private fun tryParse(json: String): LlmResponse? {
        // Strip markdown code fences if LLM ignored instructions
        val cleanedJson = json.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return try {
            moshi.adapter(LlmResponse::class.java).fromJson(cleanedJson)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun isDuplicate(item: LlmItem): Boolean {
        // First check existing pending suggestions
        val pending = dao.getPendingSuggestions().first()
        val isPendingDup = pending.any { it.extractedTitle == item.title && it.dueDate == item.dueDate }
        if (isPendingDup) return true

        // Then check approved notes
        val notes = dao.getAllNotes().first()
        val isNoteDup = notes.any { it.title == item.title && it.dueDate == item.dueDate }
        if (isNoteDup) return true

        // Phase 2 requires Calendar checking as well, handled here later
        return false
    }
}
