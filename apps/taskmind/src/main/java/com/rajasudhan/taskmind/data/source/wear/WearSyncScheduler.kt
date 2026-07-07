package com.rajasudhan.taskmind.data.source.wear

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the periodic [WearSyncWorker] that refreshes the watch next-due tile (#216). Every 30 minutes
 * — often enough for a glanceable "what's next" without meaningful battery cost. KEEPs any already-scheduled
 * run on launch so a job WorkManager deferred under Doze isn't reset.
 */
@Singleton
class WearSyncScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<WearSyncWorker>(30, TimeUnit.MINUTES).build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        const val WORK_NAME = "taskmind_wear_sync"
    }
}
