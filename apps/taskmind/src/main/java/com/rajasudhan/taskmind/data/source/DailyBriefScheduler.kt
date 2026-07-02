package com.rajasudhan.taskmind.data.source

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules (or cancels) the once-a-day morning brief via WorkManager. A daily periodic job whose
 * first run lands at the user's chosen time; changing the time or toggling it off re-applies here.
 */
@Singleton
class DailyBriefScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    /** Apply the current preference: enqueue the daily job at [hour]:[minute], or cancel it. */
    fun reschedule(enabled: Boolean, hour: Int, minute: Int) {
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<DailyBriefWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMillis(LocalDateTime.now(), hour, minute), TimeUnit.MILLISECONDS)
            .build()
        // CANCEL_AND_REENQUEUE (not UPDATE): UPDATE keeps the existing periodCount and ignores a new
        // initialDelay once the job has fired at least once, so a changed delivery time would never
        // take effect. Re-enqueuing fresh always re-applies the delay to the next occurrence — and
        // since the delay is recomputed to the next hour:minute every time (launch or settings
        // change), the brief still lands daily at the chosen time either way.
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
    }

    companion object {
        const val WORK_NAME = "taskmind_daily_brief"

        /**
         * Milliseconds from [now] until the next [hour]:[minute] — today if it's still ahead,
         * otherwise tomorrow. Pure, so the day-boundary math is unit-testable.
         */
        fun initialDelayMillis(now: LocalDateTime, hour: Int, minute: Int): Long {
            var next = now.toLocalDate().atTime(hour, minute)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now, next).toMillis()
        }
    }
}
