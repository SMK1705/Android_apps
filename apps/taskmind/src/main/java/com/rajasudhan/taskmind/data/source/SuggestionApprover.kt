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
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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
    @ApplicationContext private val context: Context
) {
    /** Persists [suggestion] as approved, creates the Note, and schedules alarm/calendar if dated. */
    suspend fun approve(suggestion: Suggestion) {
        dao.updateSuggestion(suggestion.copy(status = "approved"))

        val isReminder = suggestion.type == "reminder" && suggestion.dueTime != null
        val noteType = if (isReminder) "reminder" else suggestion.type

        val note = Note(
            title = suggestion.extractedTitle,
            summary = suggestion.summary,
            body = "Extracted from ${suggestion.source}:\n\n${suggestion.rawSnippet}",
            dueDate = suggestion.dueDate,
            dueTime = suggestion.dueTime,
            source = suggestion.source,
            createdDate = System.currentTimeMillis(),
            type = noteType
        )
        dao.insertNote(note)

        if (isReminder) {
            scheduleAlarm(suggestion.extractedTitle, suggestion.dueDate, suggestion.dueTime)
            addToCalendar(suggestion.extractedTitle, note.body, suggestion.dueDate, suggestion.dueTime)
        } else if (suggestion.type == "todo" && suggestion.dueDate != null) {
            addToCalendar(suggestion.extractedTitle, note.body, suggestion.dueDate, null)
        }
    }

    private fun scheduleAlarm(title: String, dueDate: String?, dueTime: String?) {
        if (dueDate == null || dueTime == null) return
        try {
            val dateTimeStr = "${dueDate}T$dueTime"
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
            val localDateTime = LocalDateTime.parse(dateTimeStr, formatter)
            val timeMillis = localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply { putExtra("title", title) }
            val requestCode = dateTimeStr.hashCode()
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Inserts an event into the user's default calendar. Timed event if [dueTime] is set, otherwise
     * all-day on [dueDate]. No-ops if WRITE_CALENDAR isn't granted or no writable calendar exists.
     */
    private fun addToCalendar(title: String, description: String?, dueDate: String?, dueTime: String?) {
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
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
                startMs = LocalDateTime.parse("${dueDate}T$dueTime", formatter)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                endMs = startMs + settingsManager.eventDurationMinutes.toLong() * 60 * 1000
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
}
