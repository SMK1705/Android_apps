package com.rajasudhan.taskmind.data.source

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.rajasudhan.taskmind.MainActivity
import com.rajasudhan.taskmind.R
import com.rajasudhan.taskmind.data.local.TaskMindDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var dao: TaskMindDao
    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "TaskMind Reminder"
        val noteId = intent.getIntExtra("noteId", -1)
        val recurrence = intent.getStringExtra("recurrence")
        val dueDate = intent.getStringExtra("dueDate")
        val dueTime = intent.getStringExtra("dueTime")

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context, noteId, title, recurrence, dueDate, dueTime)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * The alarm-handling logic, extracted from [onReceive] so it can be unit-tested without the
     * broadcast/goAsync plumbing. [now] is injectable to make the recurrence advance deterministic.
     */
    internal suspend fun handle(
        context: Context,
        noteId: Int,
        title: String,
        recurrence: String?,
        dueDate: String?,
        dueTime: String?,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        // If the note was deleted, this alarm is stale: don't notify, and cancel it so a recurring
        // reminder can't keep rescheduling itself for a note that no longer exists.
        if (noteId >= 0 && dao.getNoteByIdNow(noteId) == null) {
            alarmScheduler.cancel(noteId)
            return
        }

        notifyReminder(context, title)

        // Repeating reminder: advance the note's due date and schedule the next occurrence. Step at
        // least one period past the date that just fired, then keep skipping forward until the slot is
        // actually in the future — otherwise a late delivery (device was asleep/off) would compute a
        // slot already in the past, which the scheduler drops, silently breaking the recurrence forever.
        if (noteId >= 0 && !recurrence.isNullOrBlank() && dueDate != null) {
            val firstNext = RecurrenceUtil.next(dueDate, recurrence) ?: return
            val next = RecurrenceUtil.firstFutureOccurrence(firstNext, dueTime, recurrence, now)
                ?: firstNext
            dao.updateNoteDueDate(noteId, next)
            alarmScheduler.schedule(noteId, title, next, dueTime, recurrence)
        }
    }

    private fun notifyReminder(context: Context, title: String) {
        val pendingIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, TaskMindForegroundService.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Reminder: $title")
            .setContentText("It's time for your task.")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(System.currentTimeMillis().toInt(), notification)
    }
}
