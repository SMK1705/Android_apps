package com.rajasudhan.taskmind.data.source

import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Note
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Completion-based recurrence (#124). When a repeating item marked "repeat from completion" is
 * completed, roll the SAME row forward to its next occurrence — computed from WHEN IT WAS FINISHED,
 * not its (possibly overdue) due date — and re-arm its alarm, instead of leaving it done. This is
 * Todoist's "every!" behaviour: finishing early or late reschedules from now, so overdue copies of a
 * habit never pile up.
 *
 * Shared by every completion entry point (the Notes list, the note-detail screen, and the reminder
 * notification's Done action) so they behave identically. A no-op for one-shots and date-based repeats,
 * which keep the existing behaviour (stay completed; the caller cancels the alarm).
 */
@Singleton
class CompletionRecurrence @Inject constructor(
    private val dao: TaskMindDao,
    private val alarmScheduler: AlarmScheduler,
    private val calendarMirror: CalendarMirror,
) {
    /**
     * If [note] is a completion-based repeat, reschedule it from [completedAtMillis] and clear its
     * completed state so it re-appears at the next occurrence; returns true. Returns false (a no-op)
     * for a null note, a one-shot, a date-based repeat, or an archived item — the caller then handles a
     * normal completion. [now] is injectable so the future-slot skip is deterministic under test.
     */
    suspend fun rollForwardIfCompletionBased(
        note: Note?, completedAtMillis: Long, now: LocalDateTime = LocalDateTime.now()
    ): Boolean {
        if (note == null || note.archived) return false
        if (note.recurrence.isNullOrBlank() || !note.repeatFromCompletion) return false

        val completedDate = Instant.ofEpochMilli(completedAtMillis)
            .atZone(ZoneId.systemDefault()).toLocalDate().toString()
        // For a monthly repeat, anchor to the intended day-of-month (falling back to the completion day
        // the first time), so it doesn't drift down to the 28th after a short month.
        val anchor = if (note.recurrence.equals("monthly", ignoreCase = true))
            note.recurrenceAnchorDay ?: RecurrenceUtil.dayOfMonth(completedDate)
        else note.recurrenceAnchorDay
        val next = RecurrenceUtil.nextFromCompletion(completedDate, note.dueTime, note.recurrence, now, anchor)
            ?: return false // unknown recurrence — leave it completed, like a normal one-shot

        // Roll the same row forward: persist the anchor if we just derived it, set the new slot, clear the
        // completion, and re-arm. schedule() may advance a recurring reminder past a stale slot, so persist
        // the armed date when it differs — keeping the stored dueDate in step with when it will next fire.
        if (anchor != null && note.recurrenceAnchorDay == null) dao.updateNoteRecurrenceAnchor(note.id, anchor)
        dao.updateNoteDueDate(note.id, next)
        dao.setNoteCompleted(note.id, false, null)
        val armed = alarmScheduler.schedule(note.id, note.title, next, note.dueTime, note.recurrence)
        val finalDate = armed?.takeIf { it.isNotBlank() } ?: next
        if (finalDate != next) dao.updateNoteDueDate(note.id, finalDate)
        // Move the mirrored calendar event (#119) onto the new occurrence too, so it doesn't drift stale.
        note.calendarEventId?.let { calendarMirror.update(it, note.title, finalDate, note.dueTime) }
        return true
    }
}
