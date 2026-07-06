package com.rajasudhan.taskmind.data.source.understanding

import com.rajasudhan.taskmind.data.model.Suggestion
import com.rajasudhan.taskmind.data.source.ParsedSchedule
import java.time.LocalDate

/** One field's before→after change, for the diff-style confirm shown before an edit is applied. */
data class FieldChange(val label: String, val from: String?, val to: String?)

/** The result of applying an edit patch: the updated item plus the human-readable list of what changed. */
data class EditResult(val updated: Suggestion, val changes: List<FieldChange>) {
    val hasChanges: Boolean get() = changes.isNotEmpty()
}

/**
 * Applies a natural-language edit — expressed as a JSON PATCH of only the changed fields — to a
 * [Suggestion] (#115). Pure and testable: the LLM call and JSON parsing live in [SuggestionEditor]; this
 * takes the already-parsed patch as a `Map` so it can be unit-tested with a plain map.
 *
 * Presence semantics (why a Map, not a data class): Moshi can't tell an omitted key from an explicit
 * null, but a partial edit MUST — "make it high priority" should touch only priority, not blank the date.
 * So the key set carries intent: a key PRESENT with a value sets it, PRESENT with null/blank CLEARS it
 * (nullable fields only), and ABSENT leaves it untouched. Invalid values are rejected (left unchanged)
 * rather than clearing — a garbled date must never wipe a good one.
 */
object SuggestionEdit {

    private val TYPES = setOf("note", "todo", "reminder", "waiting_on")
    private val PRIORITIES = setOf("low", "normal", "high")
    private val RECURRENCES = setOf("daily", "weekly", "monthly")
    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    private val TIME = Regex("""\d{1,2}:\d{2}""") // tolerant like the rest of the app ("9:30"); normalised below

    fun apply(current: Suggestion, patch: Map<String, Any?>): EditResult {
        val changes = mutableListOf<FieldChange>()
        var s = current

        // Non-null fields: set only when present with a valid, non-blank value.
        (patch["title"] as? String)?.trim()?.takeIf { "title" in patch && it.isNotBlank() }?.let {
            if (it != s.extractedTitle) { changes += FieldChange("Title", s.extractedTitle, it); s = s.copy(extractedTitle = it) }
        }
        (patch["type"] as? String)?.trim()?.lowercase()?.takeIf { "type" in patch && it in TYPES }?.let {
            if (it != s.type) { changes += FieldChange("Type", s.type, it); s = s.copy(type = it) }
        }
        (patch["priority"] as? String)?.trim()?.lowercase()?.takeIf { "priority" in patch && it in PRIORITIES }?.let {
            if (it != s.priority) { changes += FieldChange("Priority", s.priority, it); s = s.copy(priority = it) }
        }

        // Nullable fields: present → set-or-clear; absent → unchanged; invalid → unchanged (never clears).
        s = s.copy(
            // Calendar-validate (not just shape) — mirrors ExtractionHeuristics.sanitizeDate so an edit
            // can't set a shape-valid-but-impossible date (2026-02-30) that silently drops the alarm.
            dueDate = editNullable(patch, "due_date", "Date", s.dueDate, changes) {
                it.takeIf { DATE.matches(it) && runCatching { LocalDate.parse(it) }.isSuccess }
            },
            dueTime = editNullable(patch, "due_time", "Time", s.dueTime, changes) { normalizeTime(it) },
            location = editNullable(patch, "location", "Location", s.location, changes) { it },
            recurrence = editNullable(patch, "recurrence", "Repeat", s.recurrence, changes) { v -> v.lowercase().takeIf { it in RECURRENCES } },
        )

        return EditResult(s, changes)
    }

    /** Accepts a 1–2-digit-hour time ("9:30", the app's convention) and normalises to zero-padded "HH:mm". */
    private fun normalizeTime(raw: String): String? {
        if (!TIME.matches(raw)) return null
        val (h, m) = raw.split(":").map { it.toIntOrNull() ?: return null }
        return if (h in 0..23 && m in 0..59) "%02d:%02d".format(h, m) else null
    }

    /**
     * Merges the deterministic on-device date parse of the instruction into [patch] so "make it Friday
     * 6pm" resolves the date/time/recurrence reliably (same reasoning as #116 — the model is shaky at
     * relative dates). The parser's values take precedence for those three fields.
     */
    fun withDeterministicDates(patch: Map<String, Any?>, parsed: ParsedSchedule): Map<String, Any?> {
        if (parsed.isEmpty) return patch
        val merged = patch.toMutableMap()
        // Don't let a stray date token in a CLEARING instruction ("...I did it today") override an
        // explicit clear the model already expressed (present key with null).
        if (!isExplicitClear(patch, "due_date")) parsed.dueDate()?.let { merged["due_date"] = it }
        if (!isExplicitClear(patch, "due_time")) parsed.dueTime()?.let { merged["due_time"] = it }
        if (!isExplicitClear(patch, "recurrence")) parsed.recurrence?.let { merged["recurrence"] = it }
        return merged
    }

    private fun isExplicitClear(patch: Map<String, Any?>, key: String): Boolean = key in patch && patch[key] == null

    private inline fun editNullable(
        patch: Map<String, Any?>, key: String, label: String, current: String?,
        changes: MutableList<FieldChange>, normalize: (String) -> String?,
    ): String? {
        if (key !in patch) return current
        val value = patch[key]
        // Present but not a string (the model returned a number/bool/object, e.g. due_time: 1800) is
        // INVALID — leave the field unchanged; a failed cast must never masquerade as an intentional clear.
        if (value != null && value !is String) return current
        val raw = (value as? String)?.trim()
        val next = if (raw.isNullOrBlank()) null else normalize(raw) ?: return current // invalid → leave as-is
        if (next != current) changes += FieldChange(label, current, next)
        return next
    }
}
