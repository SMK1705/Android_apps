package com.rajasudhan.taskmind.data.source

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
        if (dueDate == null || dueTime == null) return
        val timeMillis = runCatching {
            LocalDateTime.parse("${dueDate}T$dueTime", DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
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

    fun cancel(noteId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(noteId, "", null, null, null))
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
}
