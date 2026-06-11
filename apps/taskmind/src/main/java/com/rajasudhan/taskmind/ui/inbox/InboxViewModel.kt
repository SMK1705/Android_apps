package com.rajasudhan.taskmind.ui.inbox

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.Telephony
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.data.model.Suggestion
import com.rajasudhan.taskmind.data.source.AlarmReceiver
import com.rajasudhan.taskmind.data.source.RecentDataScanner
import com.rajasudhan.taskmind.data.source.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import javax.inject.Inject

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val dao: TaskMindDao,
    private val scanner: RecentDataScanner,
    private val settingsManager: SettingsManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val pendingSuggestions = dao.getPendingSuggestions()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    fun approveSuggestion(suggestion: Suggestion) {
        viewModelScope.launch { approveOne(suggestion) }
    }

    fun approveAll() {
        viewModelScope.launch {
            pendingSuggestions.value.forEach { approveOne(it) }
        }
    }

    fun rejectAll() {
        viewModelScope.launch {
            pendingSuggestions.value.forEach { dao.updateSuggestion(it.copy(status = "rejected")) }
        }
    }

    private suspend fun approveOne(suggestion: Suggestion) {
        val updated = suggestion.copy(status = "approved")
        dao.updateSuggestion(updated)

        val isReminder = suggestion.type == "reminder" && suggestion.dueTime != null
        val noteType = if (isReminder) "reminder" else suggestion.type

        val note = Note(
            title = suggestion.extractedTitle,
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
        }

        // Mirror dated items into the device calendar:
        //  - reminders (date + time) -> timed event
        //  - todos with a due date   -> all-day event
        if (isReminder) {
            addToCalendar(suggestion.extractedTitle, note.body, suggestion.dueDate, suggestion.dueTime)
        } else if (suggestion.type == "todo" && suggestion.dueDate != null) {
            addToCalendar(suggestion.extractedTitle, note.body, suggestion.dueDate, null)
        }
    }

    fun rejectSuggestion(suggestion: Suggestion) {
        viewModelScope.launch {
            val updated = suggestion.copy(status = "rejected")
            dao.updateSuggestion(updated)
        }
    }
    
    fun updateSuggestion(suggestion: Suggestion) {
        viewModelScope.launch {
            dao.updateSuggestion(suggestion)
        }
    }

    fun refreshRecentData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            // Manual refresh scans the last 10 minutes; the periodic worker covers longer gaps.
            scanner.scanSince(System.currentTimeMillis() - 600_000)
            _isRefreshing.value = false
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
            
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("title", title)
            }
            
            val requestCode = dateTimeStr.hashCode()
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    timeMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Inserts an event into the user's default calendar.
     * If [dueTime] is non-null, creates a 1-hour timed event; otherwise an all-day event on [dueDate].
     * Silently no-ops if WRITE_CALENDAR is not granted or no writable calendar exists.
     */
    private fun addToCalendar(title: String, description: String?, dueDate: String?, dueTime: String?) {
        if (dueDate == null) return
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) return

        try {
            val calendarId = getWritableCalendarId() ?: return

            // Compute start/end up front so we can de-duplicate before inserting.
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

            // Don't add the same thing twice: skip if a same-title event already exists nearby.
            if (calendarEventExists(calendarId, title, startMs)) return

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                if (!description.isNullOrBlank()) {
                    put(CalendarContract.Events.DESCRIPTION, description)
                }
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
                    calendarId.toString(),
                    title,
                    (startMs - window).toString(),
                    (startMs + window).toString()
                ),
                null
            )?.use { it.count > 0 } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns the calendar id to write to: the user's configured choice if it is still a valid
     * writable calendar, otherwise the primary (or first writable) one. Null if none available.
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
            projection,
            null,
            null,
            "${CalendarContract.Calendars.IS_PRIMARY} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val accessLevel = cursor.getInt(2)
                if (accessLevel < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                // Exact match on the user's configured calendar wins immediately.
                if (configured != SettingsManager.CALENDAR_ID_AUTO && id == configured) return id
                // Otherwise remember the first writable (primary-first due to sort order).
                if (fallback == null) fallback = id
            }
        }
        return fallback
    }
}
