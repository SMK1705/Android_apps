package com.rajasudhan.taskmind.data.source

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.data.model.Suggestion
import com.rajasudhan.taskmind.ui.notes.Checklist
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns an approved [Suggestion] into a [Note], and mirrors dated items into an exact alarm and the
 * device calendar. Extracted from the Inbox so the same approval path is reused by the Inbox UI, the
 * notification quick-actions, and share-capture — there must be exactly one definition of "approve".
 */
@Singleton
class SuggestionApprover @Inject constructor(
    private val dao: TaskMindDao,
    private val calendarMirror: CalendarMirror,
    private val alarmScheduler: AlarmScheduler,
    private val placeGeocoder: PlaceGeocoder,
    private val geofenceManager: GeofenceManager,
    private val rejectionLearner: RejectionLearner,
    private val semanticIndex: com.rajasudhan.taskmind.data.source.embedding.SemanticIndex,
    @ApplicationContext private val context: Context
) {
    /**
     * Persists [suggestion] as approved, creates the Note, and schedules alarm/calendar if dated.
     * Returns the new note's id so the caller can offer an undo (delete that note + restore pending).
     */
    suspend fun approve(suggestion: Suggestion, durationMinutes: Int? = null, addCalendar: Boolean = true): Long {
        dao.updateSuggestion(suggestion.copy(status = "approved"))
        // Approving forgives a rejection for this sender, so a previously down-ranked sender can
        // recover once the user starts accepting their items again.
        rejectionLearner.recordApproval(suggestion)

        val isReminder = suggestion.type == "reminder" && suggestion.dueTime != null
        val noteType = if (isReminder) "reminder" else suggestion.type

        // A list-like to-do (e.g. a shopping list whose items the model collected into the summary)
        // becomes a tickable checklist, so the items render as checkboxes and ticks persist — instead
        // of one run-on line. Mirrors how NoteDetail derives a checklist for to-dos.
        val checklist = if (suggestion.type == "todo") {
            Checklist.derive(suggestion.summary).takeIf { it.size >= 2 }?.let { Checklist.encode(it) }
        } else null

        val note = Note(
            title = suggestion.extractedTitle,
            summary = suggestion.summary,
            body = "Extracted from ${suggestion.source}:\n\n${suggestion.rawSnippet}",
            dueDate = suggestion.dueDate,
            dueTime = suggestion.dueTime,
            source = suggestion.source,
            createdDate = System.currentTimeMillis(),
            type = noteType,
            recurrence = suggestion.recurrence,
            checklist = checklist,
            priority = suggestion.priority,
            counterparty = suggestion.counterparty,
            // Auto-tags (#123) carry over verbatim — already sanitised to the closed taxonomy when the
            // suggestion was created, so the note's tag chips + filters see the same values.
            tags = suggestion.tags,
            // Anchor a monthly reminder to its day-of-month so stepping doesn't drift it to the 28th.
            recurrenceAnchorDay = if (suggestion.recurrence?.lowercase() == "monthly")
                RecurrenceUtil.dayOfMonth(suggestion.dueDate) else null,
            // Completion-based recurrence (#124) carries over from the "every!" marker / auto-detected
            // pattern, so completing the created note reschedules it from the finish time.
            repeatFromCompletion = suggestion.repeatFromCompletion
        )
        val noteId = dao.insertNote(note)

        // Index the approved note for semantic search + near-duplicate detection (embed once, here).
        semanticIndex.index(noteId.toInt(), note.title, note.summary)

        // If the model named a place, geocode it so the note's map + Get directions point at the
        // actual venue (not wherever the user happened to be). Best-effort: directions still resolve
        // from the place name even when geocoding returns nothing. A geofence is registered only when
        // background location is granted.
        val place = suggestion.location?.trim()?.ifBlank { null }
        if (place != null) {
            val coords = placeGeocoder.geocode(place)
            dao.updateNoteLocation(
                noteId.toInt(),
                coords?.first,
                coords?.second,
                if (coords != null) LOCATION_RADIUS_METERS else null,
                place
            )
            if (coords != null && hasBackgroundLocation()) {
                geofenceManager.add(noteId.toInt(), coords.first, coords.second, LOCATION_RADIUS_METERS.toFloat())
            }
        }

        if (isReminder) {
            val armed = alarmScheduler.schedule(noteId.toInt(), suggestion.extractedTitle, suggestion.dueDate, suggestion.dueTime, suggestion.recurrence)
            if (!armed.isNullOrBlank() && armed != suggestion.dueDate) dao.updateNoteDueDate(noteId.toInt(), armed)
            // Mirror the calendar event onto the SAME occurrence the alarm + note landed on — schedule()
            // may have advanced a recurring reminder past a stale slot, so using the original (past)
            // dueDate here would put a stale calendar entry on a day the reminder no longer fires. The
            // mirror gates itself on the Calendar source toggle; capture its event id so the note's later
            // reschedule/rename/complete/delete can keep the same event in step (#119).
            val calDate = armed?.takeIf { it.isNotBlank() } ?: suggestion.dueDate
            if (addCalendar) {
                calendarMirror.insert(suggestion.extractedTitle, note.body, calDate, suggestion.dueTime, durationMinutes)
                    ?.let { dao.updateNoteCalendarEventId(noteId.toInt(), it) }
            }
        } else if (suggestion.type == "waiting_on" && suggestion.dueDate != null && suggestion.dueTime != null) {
            // A waiting-on item with a follow-up time gets a nudge to chase it up — no calendar event,
            // since you're not attending anything, just reminding yourself to follow up.
            val armed = alarmScheduler.schedule(noteId.toInt(), suggestion.extractedTitle, suggestion.dueDate, suggestion.dueTime, suggestion.recurrence)
            if (!armed.isNullOrBlank() && armed != suggestion.dueDate) dao.updateNoteDueDate(noteId.toInt(), armed)
        } else if (suggestion.type == "todo" && suggestion.dueDate != null) {
            if (addCalendar) {
                calendarMirror.insert(suggestion.extractedTitle, note.body, suggestion.dueDate, null, durationMinutes)
                    ?.let { dao.updateNoteCalendarEventId(noteId.toInt(), it) }
            }
        }
        return noteId
    }

    private fun hasBackgroundLocation(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val LOCATION_RADIUS_METERS = 150.0
    }
}
