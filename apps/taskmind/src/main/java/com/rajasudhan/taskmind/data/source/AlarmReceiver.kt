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
    @Inject lateinit var calendarMirror: CalendarMirror

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "TaskMind Reminder"
        val noteId = intent.getIntExtra("noteId", -1)
        val recurrence = intent.getStringExtra("recurrence")
        val dueDate = intent.getStringExtra("dueDate")
        val dueTime = intent.getStringExtra("dueTime")
        val nagCount = intent.getIntExtra("nagCount", 0)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context, noteId, title, recurrence, dueDate, dueTime, nagCount = nagCount)
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
        nagCount: Int = 0,
    ) {
        // If the note was deleted or already completed, this alarm is stale: don't notify, and
        // cancel it so a recurring reminder can't keep rescheduling itself for a note that no
        // longer needs it. (Completing a note doesn't cancel its alarm, so without this a finished
        // task would still ring.)
        val note = if (noteId >= 0) dao.getNoteByIdNow(noteId) else null
        if (noteId >= 0 && (note == null || note.completed)) {
            alarmScheduler.cancel(noteId)
            return
        }

        notifyReminder(context, noteId, title)

        // Repeating reminder: advance the note's due date and schedule the next occurrence. Step at
        // least one period past the date that just fired, then keep skipping forward until the slot is
        // actually in the future — otherwise a late delivery (device was asleep/off) would compute a
        // slot already in the past, which the scheduler drops, silently breaking the recurrence forever.
        //
        // Completion-based repeats (#124) are the exception: they must NOT advance on fire — the point is
        // that they stay due until the user finishes, at which moment [CompletionRecurrence] reschedules
        // them from the completion time. (Nag, below, still re-rings until done.)
        if (noteId >= 0 && !recurrence.isNullOrBlank() && dueDate != null && note?.repeatFromCompletion != true) {
            val anchor = note?.recurrenceAnchorDay
            val firstNext = RecurrenceUtil.next(dueDate, recurrence, anchor) ?: return
            val next = RecurrenceUtil.firstFutureOccurrence(firstNext, dueTime, recurrence, now, anchor)
                ?: firstNext
            dao.updateNoteDueDate(noteId, next)
            // A recurring reminder is mirrored as a single event tracking its next occurrence — move it
            // forward with the note so the calendar doesn't fall a period behind on each fire (#119).
            note?.calendarEventId?.let { calendarMirror.update(it, title, next, dueTime) }
            alarmScheduler.schedule(noteId, title, next, dueTime, recurrence)
        }

        // Nag mode: keep re-firing until the task is done. Walks the escalation ladder (5 → 10 →
        // 20 → 30 min, then every 30) via the nagCount that rides on the re-fire intent. The chain
        // stops on its own: Done/complete/delete all cancel the pending re-fire (AlarmScheduler.cancel
        // covers the snooze/nag namespace), and the completed-note guard above eats any straggler.
        if (note != null && note.nag) {
            // Mark the chain active so it can be resumed after a reboot (even for a recurring reminder,
            // whose dueDate has just advanced above and so no longer reveals that it fired).
            dao.setNagFiring(noteId, true)
            val interval = NAG_INTERVALS[nagCount.coerceIn(0, NAG_INTERVALS.size - 1)]
            alarmScheduler.snoozeReminder(noteId, title, interval, nagCount + 1)
        }
    }

    /**
     * Posts the reminder on the HIGH-importance channel (heads-up + sound — the whole point of a
     * reminder), deep-links the tap to the note itself, and adds Done / Snooze actions so it can be
     * triaged from the shade instead of dead-ending. Stable per-note id, so a snoozed re-fire
     * replaces the original instead of stacking.
     */
    private fun notifyReminder(context: Context, noteId: Int, title: String) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (noteId >= 0) putExtra(MainActivity.EXTRA_OPEN_NOTE_ID, noteId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, if (noteId >= 0) ReminderActionReceiver.OPEN_RC_BASE + noteId else 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, TaskMindForegroundService.REMINDER_CHANNEL_ID)
            .setContentTitle("Reminder: $title")
            .setContentText("It's time for your task.")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (noteId >= 0) {
            builder
                .addAction(
                    android.R.drawable.checkbox_on_background, "Done",
                    reminderAction(context, ReminderActionReceiver.ACTION_DONE, ReminderActionReceiver.DONE_RC_BASE + noteId, noteId, title)
                )
                .addAction(
                    android.R.drawable.ic_popup_reminder, "Snooze 1h",
                    reminderAction(context, ReminderActionReceiver.ACTION_SNOOZE, ReminderActionReceiver.SNOOZE_RC_BASE + noteId, noteId, title)
                )
                .addAction(
                    android.R.drawable.ic_menu_month, "Tomorrow",
                    reminderAction(context, ReminderActionReceiver.ACTION_SNOOZE_TOMORROW, ReminderActionReceiver.TOMORROW_RC_BASE + noteId, noteId, title)
                )
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(if (noteId >= 0) NOTIFICATION_ID_BASE + noteId else System.currentTimeMillis().toInt(), builder.build())
    }

    private fun reminderAction(context: Context, action: String, rc: Int, noteId: Int, title: String): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            this.action = action
            putExtra(ReminderActionReceiver.EXTRA_NOTE_ID, noteId)
            putExtra(ReminderActionReceiver.EXTRA_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context, rc, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        // Fired-reminder notifications: stable id per note, clear of the geofence (100_000 + id)
        // and foreground-service (1) / review (42) ids.
        const val NOTIFICATION_ID_BASE = 200_000

        // Nag-mode escalation ladder, in minutes: quick first nudge, then back off to a steady
        // half-hour drumbeat until the task is completed.
        val NAG_INTERVALS = longArrayOf(5, 10, 20, 30)
    }
}
