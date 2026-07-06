package com.rajasudhan.taskmind.data.source.understanding

import com.rajasudhan.taskmind.data.model.Suggestion
import com.rajasudhan.taskmind.data.source.NaturalDate
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a plain-language "fix it" instruction into a diffable edit of a [Suggestion] (#115).
 *
 * It makes the same constrained-JSON call the extractor uses — but with an EDIT prompt that returns a
 * PATCH of only the changed fields — then merges the on-device deterministic date parse of the
 * instruction (which OVERRIDES the model for date/time/recurrence, the fields it's least reliable on),
 * and applies the result via the pure [SuggestionEdit]. A parse/LLM failure degrades to "no change"
 * rather than throwing, so the UI can just report that nothing was understood.
 */
@Singleton
class SuggestionEditor @Inject constructor(
    private val llmProvider: LlmProvider,
    private val moshi: Moshi,
) {
    private val mapAdapter by lazy {
        moshi.adapter<Map<String, Any?>>(
            Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        )
    }

    /** [now] is injectable so the deterministic date resolution is testable. */
    suspend fun edit(current: Suggestion, instruction: String, now: LocalDateTime = LocalDateTime.now()): EditResult {
        if (instruction.isBlank()) return EditResult(current, emptyList())

        val system = EditPrompt.INSTRUCTION.replace("{{CURRENT_DATETIME}}", datetimeLine(now))
        val user = buildString {
            append("Current item:\n")
            append("  title: ").append(current.extractedTitle).append('\n')
            append("  type: ").append(current.type).append('\n')
            append("  due_date: ").append(current.dueDate ?: "null").append('\n')
            append("  due_time: ").append(current.dueTime ?: "null").append('\n')
            append("  location: ").append(current.location ?: "null").append('\n')
            append("  recurrence: ").append(current.recurrence ?: "null").append('\n')
            append("  priority: ").append(current.priority).append('\n')
            append("\nInstruction: ").append(instruction.trim())
        }

        val raw = runCatching {
            val json = ExtractionHeuristics.stripJsonFences(llmProvider.generate(system, user))
            mapAdapter.fromJson(json) ?: emptyMap()
        }.getOrDefault(emptyMap())
        // The cloud backend pins the EXTRACTION schema, so it returns the patch wrapped as {"items":[{…}]};
        // on-device is free-form and returns the flat patch. Accept either.
        @Suppress("UNCHECKED_CAST")
        val patch = (raw["items"] as? List<*>)?.firstOrNull() as? Map<String, Any?> ?: raw

        val merged = SuggestionEdit.withDeterministicDates(patch, NaturalDate.parse(instruction, now))
        return SuggestionEdit.apply(current, merged)
    }

    private fun datetimeLine(now: LocalDateTime): String {
        val stamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
        val dow = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        return "$stamp. Today is a $dow."
    }
}
