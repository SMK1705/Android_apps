package com.rajasudhan.taskmind.data.source

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one-way calendar mirror (#119). Owns every write to the device calendar so a TaskMind item and
 * its mirrored event stay in step for the item's whole life: [insert] on approval, [update] when it's
 * rescheduled or renamed, [delete] when it's completed or deleted. Extracted from [SuggestionApprover]
 * so the note-lifecycle call sites can maintain the same event by the id stored on the note.
 *
 * Every op self-gates on WRITE_CALENDAR and a writable calendar, and swallows provider errors — a
 * calendar hiccup must never break the note write. [insert] additionally respects the Calendar SOURCE
 * TOGGLE (previously dead); [update]/[delete] always run for an event we already created, so turning
 * the toggle off later can't strand a stale or orphaned event.
 */
@Singleton
class CalendarMirror @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
    private val sourceManager: SourceManager,
) {
    /**
     * Mirror a newly-approved item as a calendar event and return its id (for the caller to persist on
     * the note), or null if nothing was written — the Calendar source is off, WRITE_CALENDAR / a writable
     * calendar is missing, the date is absent, or a same-title event already sits on that slot (dedup).
     */
    suspend fun insert(title: String, description: String?, dueDate: String?, dueTime: String?, durationMinutes: Int? = null): Long? {
        if (dueDate == null || !sourceManager.isCalendarEnabled.first() || !canWrite()) return null
        return try {
            val calendarId = getWritableCalendarId() ?: return null
            val slot = slotFor(dueDate, dueTime, durationMinutes) ?: return null
            if (calendarEventExists(calendarId, title, slot.startMs)) return null
            val values = eventValues(title, description, slot).apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
            }
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
            ContentUris.parseId(uri).takeIf { it > 0 }
        } catch (e: Exception) {
            e.printStackTrace(); null
        }
    }

    /** Re-point an existing mirrored event at the item's current title/date/time; no-op if it's gone. */
    fun update(eventId: Long, title: String, dueDate: String?, dueTime: String?, durationMinutes: Int? = null) {
        if (dueDate == null || !canWrite()) return
        try {
            val slot = slotFor(dueDate, dueTime, durationMinutes) ?: return
            // Leave DESCRIPTION untouched (null → not in the ContentValues), so an update only moves the
            // event's title/time; the ALL_DAY flag is set explicitly so a timed↔all-day edit is honoured.
            val values = eventValues(title, null, slot)
            context.contentResolver.update(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), values, null, null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Remove a mirrored event (its item was completed or deleted); no-op if it's already gone. */
    fun delete(eventId: Long) {
        if (!canWrite()) return
        try {
            context.contentResolver.delete(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ---- internals ----

    private data class Slot(val startMs: Long, val endMs: Long, val timeZone: String, val allDay: Boolean)

    /** Timed event when [dueTime] is set (system zone), otherwise an all-day event on [dueDate] (UTC). */
    private fun slotFor(dueDate: String, dueTime: String?, durationMinutes: Int?): Slot? = runCatching {
        if (dueTime != null) {
            // Tolerant parse (shared with AlarmScheduler): a single-digit hour like "9:30" is valid.
            val time = RecurrenceUtil.parseTime(dueTime) ?: return null
            val start = LocalDateTime.of(LocalDate.parse(dueDate), time)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            Slot(start, start + (durationMinutes ?: settingsManager.eventDurationMinutes).toLong() * 60 * 1000, TimeZone.getDefault().id, false)
        } else {
            val start = LocalDate.parse(dueDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            Slot(start, start + 24 * 60 * 60 * 1000, "UTC", true)
        }
    }.getOrNull()

    private fun eventValues(title: String, description: String?, slot: Slot) = ContentValues().apply {
        put(CalendarContract.Events.TITLE, title)
        if (!description.isNullOrBlank()) put(CalendarContract.Events.DESCRIPTION, description)
        put(CalendarContract.Events.DTSTART, slot.startMs)
        put(CalendarContract.Events.DTEND, slot.endMs)
        put(CalendarContract.Events.EVENT_TIMEZONE, slot.timeZone)
        put(CalendarContract.Events.ALL_DAY, if (slot.allDay) 1 else 0)
    }

    private fun canWrite(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** True if a same-title event already sits within ±1 day of [startMs] on [calendarId] (dedup an insert). */
    private fun calendarEventExists(calendarId: Long, title: String, startMs: Long): Boolean {
        val window = 24 * 60 * 60 * 1000L
        return try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events._ID),
                "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.TITLE} = ? AND " +
                    "${CalendarContract.Events.DTSTART} BETWEEN ? AND ?",
                arrayOf(calendarId.toString(), title, (startMs - window).toString(), (startMs + window).toString()),
                null
            )?.use { it.count > 0 } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * The calendar id to write to: the user's configured choice if still valid/writable, otherwise the
     * primary (or first writable) one. Null if none available.
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
