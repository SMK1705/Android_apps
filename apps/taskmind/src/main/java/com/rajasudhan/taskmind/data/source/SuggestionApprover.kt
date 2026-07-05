package com.rajasudhan.taskmind.data.source

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.data.model.Suggestion
import com.rajasudhan.taskmind.ui.notes.Checklist
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone
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
    private val settingsManager: SettingsManager,
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
            // Anchor a monthly reminder to its day-of-month so stepping doesn't drift it to the 28th.
            recurrenceAnchorDay = if (suggestion.recurrence?.lowercase() == "monthly")
                RecurrenceUtil.dayOfMonth(suggestion.dueDate) else null
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
            // dueDate here would put a stale calendar entry on a day the reminder no longer fires.
            val calDate = armed?.takeIf { it.isNotBlank() } ?: suggestion.dueDate
            if (addCalendar) addToCalendar(suggestion.extractedTitle, note.body, calDate, suggestion.dueTime, durationMinutes)
        } else if (suggestion.type == "waiting_on" && suggestion.dueDate != null && suggestion.dueTime != null) {
            // A waiting-on item with a follow-up time gets a nudge to chase it up — no calendar event,
            // since you're not attending anything, just reminding yourself to follow up.
            val armed = alarmScheduler.schedule(noteId.toInt(), suggestion.extractedTitle, suggestion.dueDate, suggestion.dueTime, suggestion.recurrence)
            if (!armed.isNullOrBlank() && armed != suggestion.dueDate) dao.updateNoteDueDate(noteId.toInt(), armed)
        } else if (suggestion.type == "todo" && suggestion.dueDate != null) {
            if (addCalendar) addToCalendar(suggestion.extractedTitle, note.body, suggestion.dueDate, null, durationMinutes)
        }
        return noteId
    }

    /**
     * Inserts an event into the user's default calendar. Timed event if [dueTime] is set, otherwise
     * all-day on [dueDate]. No-ops if WRITE_CALENDAR isn't granted or no writable calendar exists.
     */
    private fun addToCalendar(title: String, description: String?, dueDate: String?, dueTime: String?, durationMinutes: Int? = null) {
        if (dueDate == null) return
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) return

        try {
            val calendarId = getWritableCalendarId() ?: return
            val isTimed = dueTime != null
            val startMs: Long
            val endMs: Long
            val timeZone: String
            val allDay: Boolean
            if (isTimed) {
                // Tolerant time parse (shared with AlarmScheduler) so a single-digit-hour time like
                // "9:30" still produces a calendar event instead of being silently skipped.
                val time = RecurrenceUtil.parseTime(dueTime) ?: return
                startMs = LocalDateTime.of(LocalDate.parse(dueDate), time)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                endMs = startMs + (durationMinutes ?: settingsManager.eventDurationMinutes).toLong() * 60 * 1000
                timeZone = TimeZone.getDefault().id
                allDay = false
            } else {
                startMs = LocalDate.parse(dueDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                endMs = startMs + 24 * 60 * 60 * 1000
                timeZone = "UTC"
                allDay = true
            }

            if (calendarEventExists(calendarId, title, startMs)) return

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                if (!description.isNullOrBlank()) put(CalendarContract.Events.DESCRIPTION, description)
                put(CalendarContract.Events.DTSTART, startMs)
                put(CalendarContract.Events.DTEND, endMs)
                put(CalendarContract.Events.EVENT_TIMEZONE, timeZone)
                if (allDay) put(CalendarContract.Events.ALL_DAY, 1)
            }
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** True if an event with the same title already exists within ±1 day of [startMs]. */
    private fun calendarEventExists(calendarId: Long, title: String, startMs: Long): Boolean {
        val window = 24 * 60 * 60 * 1000L
        return try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events._ID),
                "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.TITLE} = ? AND " +
                    "${CalendarContract.Events.DTSTART} BETWEEN ? AND ?",
                arrayOf(
                    calendarId.toString(), title,
                    (startMs - window).toString(), (startMs + window).toString()
                ),
                null
            )?.use { it.count > 0 } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * The calendar id to write to: the user's configured choice if still valid/writable, otherwise
     * the primary (or first writable) one. Null if none available.
     */
    private fun getWritableCalendarId(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        val configured = settingsManager.calendarId
        var fallback: Long? = null
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection, null, null,
            "${CalendarContract.Calendars.IS_PRIMARY} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val accessLevel = cursor.getInt(2)
                if (accessLevel < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                if (configured != SettingsManager.CALENDAR_ID_AUTO && id == configured) return id
                if (fallback == null) fallback = id
            }
        }
        return fallback
    }

    private fun hasBackgroundLocation(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val LOCATION_RADIUS_METERS = 150.0
    }
}
