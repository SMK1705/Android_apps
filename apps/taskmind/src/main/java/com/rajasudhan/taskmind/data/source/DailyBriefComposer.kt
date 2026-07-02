package com.rajasudhan.taskmind.data.source

/** A composed morning brief: a glanceable title and a short multi-line body. */
data class DailyBrief(val title: String, val body: String)

/**
 * Turns the morning's state — overdue, due-today, and freshly-caught items — into a short brief.
 * Pure and deterministic so the wording is unit-testable and the worker stays trivial. Returns null
 * when there's genuinely nothing to surface: a "nothing to do" ping would be noise, and the whole
 * point of the brief is to earn its place, not nag.
 */
object DailyBriefComposer {

    /**
     * @param focus up to a few titles to name explicitly (overdue/today, most-important first).
     */
    fun compose(overdue: Int, dueToday: Int, pending: Int, focus: List<String>): DailyBrief? {
        if (overdue == 0 && dueToday == 0 && pending == 0) return null

        // Title leads with today's real load; falls back to the review queue when the day is clear.
        val todayLoad = overdue + dueToday
        val title = when {
            overdue > 0 -> "Good morning — $overdue overdue"
            dueToday > 0 -> "Good morning — $dueToday due today"
            else -> "Good morning — $pending to review"
        }

        val parts = buildList {
            if (overdue > 0) add("$overdue overdue")
            if (dueToday > 0) add("$dueToday due today")
            if (pending > 0) add("$pending to review")
        }
        val summary = parts.joinToString(" · ")
        val focusLine = focus.filter { it.isNotBlank() }.take(MAX_FOCUS)
            .takeIf { it.isNotEmpty() && todayLoad > 0 }
            ?.let { "\nStart with: ${it.joinToString(", ")}" }
            .orEmpty()

        return DailyBrief(title = title, body = summary + focusLine)
    }

    private const val MAX_FOCUS = 3
}
