package com.rajasudhan.taskmind.ui.notes

/**
 * Task Fade (#125): when an item counts as "fading" — a stale backlog to-do the user has neither
 * finished nor scheduled. Pure and timestamp-based (uses [Note.createdDate], already stored), so it's
 * covered by a plain-JVM unit test and the ViewModel and the card share ONE definition.
 *
 * Scope is deliberately narrow: only UNDATED to-dos. A reference note is kept on purpose (never
 * "stale"), and a dated reminder/todo is either scheduled or already surfaced by the Overdue chip —
 * so those never fade. Fading is a visual nudge only; the batch-archive ("bankruptcy") is the user's
 * explicit call and is non-destructive (archived items are recoverable, never deleted).
 */
object TaskFade {

    /** An undated to-do left untouched this long is considered fading. */
    const val FADE_AFTER_DAYS = 21L

    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun isFading(
        type: String,
        dueDate: String?,
        completed: Boolean,
        archived: Boolean,
        createdDate: Long,
        now: Long,
    ): Boolean =
        !completed && !archived && type == "todo" && dueDate == null &&
            now - createdDate >= FADE_AFTER_DAYS * DAY_MS
}
