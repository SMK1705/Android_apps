package com.rajasudhan.taskmind.data.source

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the daily auto-snapshot ([AutoSnapshotWorker]). Unlike the brief/recap this has no user
 * setting — it's an always-on safety net — so it's simply (re)armed on every launch with KEEP, which
 * preserves any already-scheduled run rather than rolling a Doze-deferred snapshot forward a day on
 * each app start. The first run lands at [SNAPSHOT_HOUR], a quiet time when the device is usually idle.
 */
@Singleton
class AutoSnapshotScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    fun schedule(now: LocalDateTime = LocalDateTime.now()) {
        val request = PeriodicWorkRequestBuilder<AutoSnapshotWorker>(24, TimeUnit.HOURS)
            // Reuse the brief's DST-safe delay math so the first run lands at the next SNAPSHOT_HOUR.
            .setInitialDelay(
                DailyBriefScheduler.initialDelayMillis(now, SNAPSHOT_HOUR, 0), TimeUnit.MILLISECONDS
            )
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        const val WORK_NAME = "taskmind_auto_snapshot"
        private const val SNAPSHOT_HOUR = 3 // 03:00 local — device typically idle / charging
    }
}
