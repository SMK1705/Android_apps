package com.rajasudhan.taskmind.data.source

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Note
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
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
    @Inject lateinit var notifier: SuggestionNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val reNotify = when (intent.action) {
            in BOOT_ACTIONS -> true    // reboot / app update: alarms AND state were cleared — restore all
            in CLOCK_ACTIONS -> false  // clock / timezone change: only re-arm the wall-clock alarms
            else -> return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                rearm(reNotify = reNotify)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Re-arms every active timed reminder. Extracted from [onReceive] so it's unit-testable without
     * the broadcast/goAsync plumbing; [now] is injectable to make the recurrence advance deterministic.
     *
     * [reNotify] = true (a reboot / app update) also restores state the restart cleared — the pending
     * snooze-resurface alarms and the review notification. On a clock / timezone change ([reNotify] =
     * false) that state is intact (a snooze fires at an absolute instant, unaffected by the change, and
     * the notification was never cleared), so only the wall-clock reminder alarms above need re-arming.
     */
    internal suspend fun rearm(now: LocalDateTime = LocalDateTime.now(), reNotify: Boolean = true) {
        for (note in dao.getReminderNotes()) {
            val date = note.dueDate ?: continue
            val time = note.dueTime ?: continue
            // A time we can't parse can't be scheduled anyway; skip it rather than advance and
            // persist a date derived from a midnight fallback.
            if (RecurrenceUtil.parseTime(time) == null) continue
            if (!note.recurrence.isNullOrBlank()) {
                // Recurring: land on the next slot that's actually in the future.
                val next = RecurrenceUtil.firstFutureOccurrence(date, time, note.recurrence, now) ?: continue
                // Persist the advance only on a reboot (genuine elapsed time). On a clock/timezone
                // change `now` is user-controlled, so a temporary set-forward-then-back would otherwise
                // corrupt the stored date and skip an occurrence — re-arm the alarm but leave the date.
                if (reNotify && next != date) dao.updateNoteDueDate(note.id, next)
                alarmScheduler.schedule(note.id, note.title, next, time, note.recurrence)
            } else {
                // One-shot: re-arm as stored. The scheduler drops it if it's already past
                // (it fired, or was missed, while the device was off), which is correct.
                alarmScheduler.schedule(note.id, note.title, date, time, null)
                restartNagIfFired(note, date, time, now)
            }
        }

        // Waiting-on follow-up nudges are plain one-shot alarms too (scheduled by SuggestionApprover
        // for a dated waiting_on item), so a reboot would silently drop the "chase this up" reminder
        // just like a real reminder — re-arm them the same way. schedule() drops any already-past slot.
        for (note in dao.getWaitingOnReminders()) {
            val date = note.dueDate ?: continue
            val time = note.dueTime ?: continue
            if (RecurrenceUtil.parseTime(time) == null) continue
            // schedule() advances a recurring follow-up past its stale slot; persist the armed date so
            // the stored date matches — but only on a reboot, not a clock change (see the reminder loop).
            val armed = alarmScheduler.schedule(note.id, note.title, date, time, note.recurrence)
            if (reNotify && !armed.isNullOrBlank() && armed != date) dao.updateNoteDueDate(note.id, armed)
            // A waiting-on nag note gets its nag chain restarted too — the restart was previously only
            // in the reminder loop, so a nag on a non-'reminder' note silently died on reboot.
            restartNagIfFired(note, date, time, now)
        }

        // A clock/timezone change clears none of the below, so stop here — the wall-clock alarm
        // re-arm above is all it needs.
        if (!reNotify) return

        // Snoozed suggestions: their resurface alarms died with the reboot too. Re-arm the ones
        // still in the future, then re-post the review notification — that both restores the
        // pre-reboot notification (cleared by the restart) and immediately surfaces any snooze
        // that expired while the device was off. No-ops when nothing is pending.
        val nowMs = System.currentTimeMillis()
        dao.getPendingSuggestions().first()
            .filter { (it.snoozedUntil ?: 0L) > nowMs }
            .forEach { notifier.scheduleResurface(it.id, it.snoozedUntil!!) }
        notifier.notifyPending()
    }

    /**
     * Restarts a nag note's re-fire chain from the top of the ladder if it had already fired but was
     * never completed — the transient nag re-fire is a plain alarm that died with the reboot, and
     * re-arming the main slot alone doesn't resume "nag until done". Driven off [Note.nag], not the
     * note's type, so a waiting-on nag resumes too. Scoped to one-shot notes; a recurring nag can't be
     * detected this way (its stored date is advanced when it fires) and is tracked separately.
     */
    private fun restartNagIfFired(note: Note, date: String, time: String, now: LocalDateTime) {
        if (!note.nag || note.completed || !note.recurrence.isNullOrBlank()) return
        val slot = runCatching { LocalDateTime.of(LocalDate.parse(date), RecurrenceUtil.parseTime(time)) }.getOrNull()
        if (slot != null && slot.isBefore(now)) {
            alarmScheduler.snoozeReminder(note.id, note.title, AlarmReceiver.NAG_INTERVALS[0], 0)
        }
    }

    private companion object {
        val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            // App update clears all pending exact alarms too (only this app's process is notified), so
            // re-arm on it — otherwise every reminder stays silent until the next actual reboot.
            Intent.ACTION_MY_PACKAGE_REPLACED
        )

        // Clock / timezone changes DON'T clear alarms, but an RTC (wall-clock) reminder set for "9am"
        // then fires at the old zone's 9am instant — the wrong wall-clock time in the new zone. Re-arm
        // each so its epoch is recomputed for the current zone. These implicit broadcasts are exempt
        // from the API 26+ manifest-registration limit (Android's broadcast-exceptions list covers them
        // specifically for clock/alarm apps).
        val CLOCK_ACTIONS = setOf(
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED // "android.intent.action.TIME_SET"
        )
    }
}
