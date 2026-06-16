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
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var dao: TaskMindDao
    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "TaskMind Reminder"

        val pendingIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, TaskMindForegroundService.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Reminder: $title")
            .setContentText("It's time for your task.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(System.currentTimeMillis().toInt(), notification)

        // Repeating reminder: advance the note's due date and schedule the next occurrence.
        val noteId = intent.getIntExtra("noteId", -1)
        val recurrence = intent.getStringExtra("recurrence")
        val dueDate = intent.getStringExtra("dueDate")
        val dueTime = intent.getStringExtra("dueTime")
        if (noteId >= 0 && !recurrence.isNullOrBlank() && dueDate != null) {
            val next = RecurrenceUtil.next(dueDate, recurrence) ?: return
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    dao.updateNoteDueDate(noteId, next)
                    alarmScheduler.schedule(noteId, title, next, dueTime, recurrence)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
