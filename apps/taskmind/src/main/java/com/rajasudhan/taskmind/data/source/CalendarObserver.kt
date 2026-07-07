package com.rajasudhan.taskmind.data.source

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.rajasudhan.taskmind.data.local.TaskMindDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The BACK direction of the calendar mirror (#205, Phase 2 of #119): dragging or renaming a mirrored
 * event in Google Calendar reschedules/renames the matching TaskMind item. A [ContentObserver] on the
 * events provider; for each note carrying a [Note.calendarEventId] it reads the event's current
 * DTSTART/TITLE and, if they differ, writes them back onto the note and re-arms its alarm.
 *
 * [CalendarMirror] owns the FORWARD direction (note → event); this is its inverse and is deliberately
 * built to not chase its own tail:
 *  - **selfChange** deliveries are ignored, and
 *  - the write-back is **idempotent** — [reconcile] returns null when the event already matches the note,
 *    so our own mirror writes (which make the event match) produce no note write and the loop self-terminates.
 * Deliveries are serialised by [mutex] (the observer framework re-delivers and fans out over subtree URIs).
 * Reads mirror [CalendarMirror]'s zone convention exactly (timed → system zone, all-day → UTC) so a value
 * we wrote round-trips unchanged. Deleted events are left alone — delete stays one-way (note → event) so a
 * stray calendar deletion can never silently drop a task.
 */
@Singleton
class CalendarObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TaskMindDao,
    private val alarmScheduler: AlarmScheduler,
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutex = Mutex()

    fun start() {
        // A ROM without a calendar provider throws on register — never let that crash the service.
        try {
            context.contentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, this)
        } catch (e: Exception) {
            Log.w(TAG, "registerContentObserver failed", e)
        }
    }

    fun stop() {
        runCatching { context.contentResolver.unregisterContentObserver(this) }
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        if (selfChange) return
        scope.launch { mutex.withLock { syncBack() } }
    }

    /**
     * For every active note with a mirrored event, pull the event's current start/title and write back any
     * change. Isolated per note so one unreadable event can't abort the sweep. Runs under [mutex].
     */
    @VisibleForTesting
    internal suspend fun syncBack() {
        val notes = runCatching { dao.getNotesList() }.getOrElse {
            Log.w(TAG, "getNotesList failed", it); return
        }
        val zone = ZoneId.systemDefault()
        for (note in notes) {
            val eventId = note.calendarEventId ?: continue
            if (note.completed || note.archived) continue
            // A recurring item's schedule is driven by its recurrence rule + completion, not by dragging a
            // single mirrored occurrence — writing a dragged (possibly past) slot back would fight the
            // rule and drift the note off its re-armed occurrence. The back-sync is scoped to one-shots.
            if (note.recurrence != null) continue
            val event = readEvent(eventId) ?: continue
            val r = reconcile(note.title, note.dueDate, note.dueTime, event, zone) ?: continue
            runCatching {
                dao.updateNote(note.copy(title = r.title, dueDate = r.dueDate, dueTime = r.dueTime))
                // Keep the alarm in step with the moved slot: (re)arm when timed, clear it when the event
                // became all-day (no time to ring at).
                if (r.dueTime != null) alarmScheduler.schedule(note.id, r.title, r.dueDate, r.dueTime, note.recurrence)
                else alarmScheduler.cancel(note.id)
            }.onFailure { Log.w(TAG, "sync-back for note ${note.id} failed", it) }
        }
    }

    /** The event's current start/all-day/title, or null if it's gone, deleted, or unreadable. */
    private fun readEvent(eventId: Long): CalendarEventState? = try {
        context.contentResolver.query(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            arrayOf(
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DELETED,
            ),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return null
            val dtStart = c.getLong(0)
            val deleted = c.getInt(3) == 1
            if (deleted || dtStart <= 0L) return null
            CalendarEventState(dtStart, c.getInt(1) == 1, c.getString(2))
        }
    } catch (e: Exception) {
        Log.w(TAG, "readEvent $eventId failed", e); null
    }

    /** The current state of a mirrored calendar event, as read from the provider. */
    internal data class CalendarEventState(val dtStartMillis: Long, val allDay: Boolean, val title: String?)

    /** The change to apply to a note; [dueDate] is always present since a calendar event always has a start. */
    internal data class Reconciled(val dueDate: String, val dueTime: String?, val title: String)

    companion object {
        private const val TAG = "CalendarObserver"

        /**
         * Pure: given a note's current fields and the event's current state, the change to write back —
         * or null when the event already matches the note (idempotent, so nothing is written and no
         * feedback loop can form). A blank/absent event title falls back to the note's title (a title is
         * never cleared). [zone] is the local zone for timed events (all-day is read in UTC).
         */
        internal fun reconcile(
            noteTitle: String,
            noteDueDate: String?,
            noteDueTime: String?,
            event: CalendarEventState,
            zone: ZoneId,
        ): Reconciled? {
            val (evDate, evTime) = eventDateTime(event, zone)
            val evTitle = event.title?.trim()?.takeIf { it.isNotEmpty() }
            // A rename only counts when the event's non-blank title differs from the note's, compared
            // trimmed so whitespace-only noise isn't mistaken for an edit.
            val titleChanged = evTitle != null && evTitle != noteTitle.trim()
            // Compare times as parsed clock values, not raw strings: a note stored non-canonically ("9:30")
            // against the same instant read back as "09:30" must NOT read as a move, or every mirrored note
            // with a single-digit hour would take a spurious write on the first unrelated calendar change.
            val moved = evDate != noteDueDate || !sameClockTime(evTime, noteDueTime)
            if (!moved && !titleChanged) return null
            return Reconciled(evDate, evTime, if (titleChanged) evTitle!! else noteTitle)
        }

        /** True when two "HH:mm"-ish times are the same clock time (or both absent). Tolerant of padding. */
        private fun sameClockTime(a: String?, b: String?): Boolean {
            if (a == b) return true
            if (a == null || b == null) return false
            return RecurrenceUtil.parseTime(a) == RecurrenceUtil.parseTime(b)
        }

        /** The event's DTSTART as (YYYY-MM-DD, HH:mm?) — all-day reads in UTC, timed in [zone]. Pure. */
        internal fun eventDateTime(event: CalendarEventState, zone: ZoneId): Pair<String, String?> =
            if (event.allDay) {
                Instant.ofEpochMilli(event.dtStartMillis).atZone(ZoneOffset.UTC).toLocalDate().toString() to null
            } else {
                val ldt = Instant.ofEpochMilli(event.dtStartMillis).atZone(zone).toLocalDateTime()
                ldt.toLocalDate().toString() to "%02d:%02d".format(ldt.hour, ldt.minute)
            }
    }
}
