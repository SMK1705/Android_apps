package com.rajasudhan.taskmind.data.source

import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Note
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One home for the item mutations that have side effects beyond the row — completing, rescheduling and
 * mirroring to the calendar all have to keep alarms, completion-recurrence and the mirrored calendar
 * event in step (#119/#124). This used to live inline in [com.rajasudhan.taskmind.ui.notes.NotesViewModel];
 * it was lifted here so the Ask surface (#319) can act on a result with the *same* behaviour instead of a
 * second, drifting copy. Callers own the coroutine scope; every method is a plain `suspend`.
 */
@Singleton
class NoteActions @Inject constructor(
    private val dao: TaskMindDao,
    private val alarmScheduler: AlarmScheduler,
    private val calendarMirror: CalendarMirror,
    private val completionRecurrence: CompletionRecurrence,
    private val settingsManager: SettingsManager,
) {
    /** Mark a note done (or reopen it). Completion may roll a completion-based recurrence forward, or, for
     *  a one-off, remove the mirrored calendar event — the task is finished, it shouldn't linger on a day. */
    suspend fun setCompleted(note: Note, completed: Boolean) {
        val at = if (completed) System.currentTimeMillis() else null
        dao.setNoteCompleted(note.id, completed, at)
        if (at != null) {
            val rolled = completionRecurrence.rollForwardIfCompletionBased(note, at)
            if (!rolled) note.calendarEventId?.let {
                calendarMirror.delete(it)
                dao.updateNoteCalendarEventId(note.id, null)
            }
        }
    }

    /** Bump a dated item to [newDueDate] (keeping its time), re-arm its alarm and move its calendar event —
     *  the one-tap triage used by Notes and now by an Ask result. Returns the date the alarm actually landed
     *  on (a recurring reminder past a stale slot advances), which is what the caller should reflect. */
    suspend fun reschedule(note: Note, newDueDate: String): String {
        dao.updateNoteDueDate(note.id, newDueDate)
        // Moving a monthly reminder re-anchors its intended day-of-month so the recurrence follows the new
        // day rather than drifting (same rule as the note-detail date change).
        val anchor = if (note.recurrence?.lowercase() == "monthly") {
            RecurrenceUtil.dayOfMonth(newDueDate).also { dao.updateNoteRecurrenceAnchor(note.id, it) }
        } else note.recurrenceAnchorDay
        val armed = alarmScheduler.schedule(note.id, note.title, newDueDate, note.dueTime, note.recurrence, anchor)
        val finalDate = if (!armed.isNullOrBlank() && armed != newDueDate) { dao.updateNoteDueDate(note.id, armed); armed } else newDueDate
        note.calendarEventId?.let { calendarMirror.update(it, note.title, finalDate, note.dueTime) }
        // Re-arming cancels any in-flight nag re-fire; clear the now-stale flag so a reboot can't resurrect it.
        dao.setNagFiring(note.id, false)
        return finalDate
    }

    /**
     * Put a dated item on the calendar on demand, mirroring what approval does ([SuggestionApprover]): a
     * reminder becomes a timed event, a dated to-do an all-day one, using the configured event duration.
     * No-ops (returns the existing/absent id) when the item is undated or already mirrored, or when the
     * Calendar source is off / unwritable ([CalendarMirror.insert] gates itself and returns null).
     */
    suspend fun addToCalendar(note: Note): Long? {
        val date = note.dueDate?.takeIf { it.isNotBlank() } ?: return null
        note.calendarEventId?.let { return it } // already mirrored — don't create a duplicate
        val time = if (note.type == "reminder") note.dueTime else null // to-dos are all-day, like approval
        val id = calendarMirror.insert(note.title, note.body, date, time, settingsManager.eventDurationMinutes) ?: return null
        dao.updateNoteCalendarEventId(note.id, id)
        return id
    }

    /** Whether [addToCalendar] would place something new — a dated to-do/reminder not already mirrored. */
    fun canAddToCalendar(note: Note): Boolean =
        !note.dueDate.isNullOrBlank() && note.calendarEventId == null && note.type in CALENDAR_TYPES

    companion object {
        private val CALENDAR_TYPES = setOf("todo", "reminder")
    }
}
