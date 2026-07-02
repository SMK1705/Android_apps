package com.rajasudhan.taskmind.data.source

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules/cancels the exact reminder alarm for a note. Keyed by note id so a note has exactly one
 * alarm that can be rescheduled (for recurrence) or cancelled. Shared by the approver, the alarm
 * receiver (which reschedules recurring reminders), and the note-detail "Repeat" control.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun schedule(noteId: Int, title: String, dueDate: String?, dueTime: String?, recurrence: String?) {
        // (Re)establishing the main alarm invalidates any pending snooze/nag re-fire from an earlier
        // fire — otherwise a rescheduled or snoozed nag note would keep ringing on its old cadence in
        // parallel with the new schedule. AlarmReceiver's nag branch re-arms the re-fire *after* its
        // recurrence-advance schedule() call, so the live nag loop is unaffected.
        cancelRefire(noteId)
        if (dueDate == null || dueTime == null) return
        // Parse the time tolerantly via the shared helper — single-digit hours like "9:30" are valid
        // and persisted, and a strict HH parser would reject them and silently drop the alarm.
        val time = RecurrenceUtil.parseTime(dueTime) ?: return
        val timeMillis = runCatching {
            LocalDateTime.of(LocalDate.parse(dueDate), time)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull() ?: return
        if (timeMillis <= System.currentTimeMillis()) return // never fire in the past

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, timeMillis,
                pendingIntent(noteId, title, dueDate, dueTime, recurrence)
            )
        }
    }

    /**
     * Cancels the note's main alarm AND any pending snooze/nag re-fire — completing or deleting a
     * note must silence every alarm it has, not just the scheduled one.
     */
    fun cancel(noteId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(noteId, "", null, null, null))
        cancelRefire(noteId)
    }

    /**
     * Cancels only the transient snooze/nag re-fire (the 7M namespace), leaving the note's main
     * alarm intact. Used to stop an in-flight nag chain (toggle off) without silencing the reminder.
     */
    fun cancelRefire(noteId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(snoozePendingIntent(noteId, "", 0))
    }

    /**
     * Re-fires the note's reminder notification [minutes] from now — the "Snooze" action on a fired
     * reminder, and the nag-mode re-fire loop ([nagCount] rides along so [AlarmReceiver] can walk
     * the escalation ladder). Uses its own request-code namespace so it never replaces the note's
     * main alarm (a recurring reminder's already-scheduled next occurrence must survive a snooze),
     * and carries no recurrence/date extras so the re-fire only re-notifies, never advances the
     * recurrence again. Falls back to an inexact-but-doze-safe alarm when exact alarms aren't
     * permitted, rather than silently dropping the snooze.
     */
    fun snoozeReminder(noteId: Int, title: String, minutes: Long = 60, nagCount: Int = 0) {
        val at = System.currentTimeMillis() + minutes * 60_000
        val pi = snoozePendingIntent(noteId, title, nagCount)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun snoozePendingIntent(noteId: Int, title: String, nagCount: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("noteId", noteId)
            putExtra("title", title)
            putExtra("nagCount", nagCount)
        }
        return PendingIntent.getBroadcast(
            context, SNOOZE_RC_BASE + noteId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun pendingIntent(
        noteId: Int, title: String, dueDate: String?, dueTime: String?, recurrence: String?
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("noteId", noteId)
            putExtra("title", title)
            putExtra("dueDate", dueDate)
            putExtra("dueTime", dueTime)
            putExtra("recurrence", recurrence)
        }
        // requestCode = noteId so reschedule/cancel target the same alarm (extras aren't part of matching).
        return PendingIntent.getBroadcast(
            context, noteId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        // Snooze re-fires live in their own request-code namespace, apart from the main per-note
        // alarms (request code = noteId) and every notification-action namespace.
        const val SNOOZE_RC_BASE = 7_000_000
    }
}
