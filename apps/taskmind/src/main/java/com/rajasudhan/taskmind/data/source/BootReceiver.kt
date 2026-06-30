package com.rajasudhan.taskmind.data.source

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rajasudhan.taskmind.data.local.TaskMindDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Re-arms reminder alarms after a device restart. AlarmManager alarms are cleared on reboot and the
 * app keeps no other record of them, so without this every reminder — one-shot or recurring — would
 * be silently lost once the phone is powered off and on again.
 *
 * For each active note with a timed reminder we reschedule its alarm; recurring ones are first
 * advanced to their next future occurrence so a reminder whose stored date is now in the past (it
 * was due while the phone was off) resumes on its next slot instead of being dropped.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var dao: TaskMindDao
    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        // Only react to a real boot broadcast (BOOT_COMPLETED, or an OEM quick-boot variant).
        if (intent.action !in BOOT_ACTIONS) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = LocalDateTime.now()
                for (note in dao.getReminderNotes()) {
                    val date = note.dueDate ?: continue
                    val time = note.dueTime ?: continue
                    // A time we can't parse can't be scheduled anyway; skip it rather than advance and
                    // persist a date derived from a midnight fallback.
                    if (RecurrenceUtil.parseTime(time) == null) continue
                    if (!note.recurrence.isNullOrBlank()) {
                        // Recurring: land on the next slot that's actually in the future.
                        val next = RecurrenceUtil.firstFutureOccurrence(date, time, note.recurrence, now) ?: continue
                        if (next != date) dao.updateNoteDueDate(note.id, next)
                        alarmScheduler.schedule(note.id, note.title, next, time, note.recurrence)
                    } else {
                        // One-shot: re-arm as stored. The scheduler drops it if it's already past
                        // (it fired, or was missed, while the device was off), which is correct.
                        alarmScheduler.schedule(note.id, note.title, date, time, null)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
    }
}
