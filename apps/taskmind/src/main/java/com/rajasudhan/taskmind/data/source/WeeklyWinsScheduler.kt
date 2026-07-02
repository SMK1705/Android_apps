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
    /** Apply the current preference: enqueue the weekly job at [hour]:00 on [RECAP_DAY], or cancel it. */
    fun reschedule(enabled: Boolean, hour: Int) {
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<WeeklyWinsWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMillis(LocalDateTime.now(), RECAP_DAY, hour), TimeUnit.MILLISECONDS)
            .build()
        // CANCEL_AND_REENQUEUE (not UPDATE): UPDATE keeps the existing periodCount and ignores a new
        // initialDelay once the job has fired, so a changed recap hour would never take effect.
        // Re-enqueuing fresh always re-applies the delay to the next occurrence; since the delay is
        // recomputed to the next Sunday:hour every time, the recap still lands weekly either way.
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
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
