package com.rajasudhan.taskmind.data.source

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules (or cancels) the once-a-week "Weekly Wins" recap via WorkManager. A 7-day periodic job
 * whose first run lands on [RECAP_DAY] at the user's chosen hour; toggling it or changing the hour
 * re-applies here.
 */
@Singleton
class WeeklyWinsScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    /**
     * Apply the current preference: enqueue the weekly job at [hour]:00 on [RECAP_DAY], or cancel it.
     *
     * [replace] = false (the launch re-arm) KEEPs any already-scheduled job so a recap WorkManager has
     * deferred under Doze past Sunday's target hour isn't destroyed — blindly re-enqueuing on every
     * launch would cancel the pending occurrence and roll it a FULL WEEK, silently skipping this week's
     * recap. [replace] = true (an explicit settings change) re-enqueues fresh so a new hour takes effect
     * immediately. (CANCEL_AND_REENQUEUE, not UPDATE, for the same reason as the daily brief.)
     */
    fun reschedule(enabled: Boolean, hour: Int, replace: Boolean = true) {
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<WeeklyWinsWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMillis(LocalDateTime.now(), RECAP_DAY, hour), TimeUnit.MILLISECONDS)
            .build()
        val policy = if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP
        workManager.enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }

    companion object {
        const val WORK_NAME = "taskmind_weekly_wins"
        /** The recap is a Sunday-evening ritual — a look back before the week resets. */
        val RECAP_DAY: DayOfWeek = DayOfWeek.SUNDAY

        /**
         * Milliseconds from [now] until the next [day] at [hour]:00 — later today if today is [day]
         * and the hour is still ahead, otherwise the next matching weekday. Pure, so the day-of-week
         * math is unit-testable.
         */
        fun initialDelayMillis(now: LocalDateTime, day: DayOfWeek, hour: Int): Long {
            var next = now.toLocalDate().atTime(hour, 0)
            while (next.dayOfWeek != day || !next.isAfter(now)) {
                next = next.plusDays(1)
            }
            return Duration.between(now, next).toMillis()
        }
    }
}
