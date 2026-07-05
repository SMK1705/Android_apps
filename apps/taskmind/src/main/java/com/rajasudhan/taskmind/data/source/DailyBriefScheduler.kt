package com.rajasudhan.taskmind.data.source

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
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
    /**
     * Apply the current preference: enqueue the daily job at [hour]:[minute], or cancel it.
     *
     * [replace] = false (the launch re-arm) KEEPs any already-scheduled job so a run that WorkManager
     * has deferred under Doze past the target time isn't destroyed — blindly re-enqueuing on every
     * launch would cancel the pending occurrence and roll it to the next day, silently skipping today's
     * brief. [replace] = true (an explicit settings change) re-enqueues fresh so a new delivery time
     * takes effect immediately. (CANCEL_AND_REENQUEUE, not UPDATE: UPDATE ignores a new initialDelay
     * once the job has fired, so a changed time would never land.)
     */
    fun reschedule(enabled: Boolean, hour: Int, minute: Int, replace: Boolean = true) {
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<DailyBriefWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMillis(LocalDateTime.now(), hour, minute), TimeUnit.MILLISECONDS)
            .build()
        val policy = if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP
        workManager.enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }

    companion object {
        const val WORK_NAME = "taskmind_daily_brief"

        /**
         * Milliseconds from [now] until the next [hour]:[minute] — today if it's still ahead,
         * otherwise tomorrow. Pure, so the day-boundary math is unit-testable; [zone] is injectable
         * for the same reason.
         *
         * The delay is the REAL elapsed time between the two wall-clock moments, resolved in [zone] —
         * a naive `Duration.between(LocalDateTime, LocalDateTime)` counts wall-clock hours, so on a
         * DST-transition day (a skipped or repeated hour between now and the target) it's an hour off
         * and the brief fires early/late.
         */
        fun initialDelayMillis(now: LocalDateTime, hour: Int, minute: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
            var next = now.toLocalDate().atTime(hour, minute)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now.atZone(zone).toInstant(), next.atZone(zone).toInstant()).toMillis()
        }
    }
}
