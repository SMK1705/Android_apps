package com.rajasudhan.taskmind.data.source.appfunctions

import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.source.AlarmScheduler
import com.rajasudhan.taskmind.data.source.CalendarMirror
import com.rajasudhan.taskmind.data.source.RecurrenceUtil
import com.rajasudhan.taskmind.data.source.understanding.AskQuery
import com.rajasudhan.taskmind.data.source.understanding.ExtractionHeuristics
import com.rajasudhan.taskmind.data.source.understanding.UnderstandingPipeline
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The behaviour behind TaskMind's Android AppFunctions (#127): what runs when Gemini calls createTask /
 * getItemsDueToday / snoozeItem for the user. Plain suspend functions over the existing data layer, so
 * they're fully unit-tested here; a GA follow-up (#209) annotates a thin wrapper with `@AppFunction`
 * from the `androidx.appfunctions` library and registers it with the OS (that binding can't be verified
 * until the Android 16 / Gemini preview is GA).
 *
 * Agent-created items go through [UnderstandingPipeline.captureFromAgent] as PENDING suggestions, so the
 * user's Inbox curation + approval flow is preserved — the agent proposes, the user still decides.
 */
@Singleton
class TaskMindAppFunctions @Inject constructor(
    private val pipeline: UnderstandingPipeline,
    private val dao: TaskMindDao,
    private val alarmScheduler: AlarmScheduler,
    private val calendarMirror: CalendarMirror,
) {
    /** "Hey Gemini, remind me to …" — proposes an item into the Inbox for the user to approve. */
    suspend fun createTask(request: CreateTaskRequest): AppFunctionResult {
        if (request.title.isBlank()) return AppFunctionResult(false, "A task needs a title.")
        val created = pipeline.captureFromAgent(
            title = request.title, notes = request.notes,
            dueDate = request.dueDate, dueTime = request.dueTime, type = request.type, source = SOURCE,
        )
        return if (created) AppFunctionResult(true, "Added “${request.title.trim()}” to your Inbox to review.")
        else AppFunctionResult(false, "You already have that — nothing added.")
    }

    /** "What's on today?" — active items due today, soonest-timed first. */
    suspend fun getItemsDueToday(today: LocalDate = LocalDate.now()): DueTodayResult {
        val due = dao.getActiveNotes().first()
            .filter { AskQuery.inWindow(it.dueDate, "today", today) }
            // Sort by the actual time-of-day, not the raw string — stored times can be single-digit-hour
            // ("9:00"), which would sort AFTER "10:00" lexically. Undated-today items sort last.
            .sortedBy { RecurrenceUtil.parseTime(it.dueTime)?.toSecondOfDay() ?: Int.MAX_VALUE }
            .map { DueItem(it.id, it.title, it.dueTime, it.type) }
        return DueTodayResult(due, due.size)
    }

    /**
     * "Snooze the dentist reminder to tomorrow 9am" — moves the item to a new slot and keeps its alarm
     * and mirrored calendar event (#119) in step; a snooze is a reschedule.
     */
    suspend fun snoozeItem(request: SnoozeRequest): AppFunctionResult {
        val note = dao.getNoteByIdNow(request.id)
            ?: return AppFunctionResult(false, "No item with id ${request.id}.")
        val date = ExtractionHeuristics.sanitizeDate(request.dueDate)
            ?: return AppFunctionResult(false, "“${request.dueDate}” isn't a valid date (use YYYY-MM-DD).")
        val time = ExtractionHeuristics.sanitizeTime(request.dueTime)

        dao.updateNote(note.copy(dueDate = date, dueTime = time, nagFiring = false))
        val finalDate: String = if (time != null) {
            // schedule() may advance a recurring reminder past a stale slot; keep the stored date in step.
            val armed = alarmScheduler.schedule(note.id, note.title, date, time, note.recurrence)
            if (!armed.isNullOrBlank() && armed != date) { dao.updateNoteDueDate(note.id, armed); armed } else date
        } else {
            alarmScheduler.cancel(note.id)
            date
        }
        note.calendarEventId?.let { calendarMirror.update(it, note.title, finalDate, time) }
        return AppFunctionResult(true, "Snoozed “${note.title}” to $finalDate${time?.let { " $it" }.orEmpty()}.")
    }

    private companion object {
        const val SOURCE = "Gemini"
    }
}
