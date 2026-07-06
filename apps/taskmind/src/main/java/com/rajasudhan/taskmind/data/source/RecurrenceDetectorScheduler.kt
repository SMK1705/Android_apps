package com.rajasudhan.taskmind.data.source

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the periodic auto-recurrence detector ([RecurrenceDetectorWorker]) — #124 Part B. Runs every
 * few days: a habit's cadence changes slowly, so there's no value (and real battery cost) in mining more
 * often. KEEPs any already-scheduled run on launch so a job WorkManager deferred under Doze isn't reset.
 */
@Singleton
class RecurrenceDetectorScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<RecurrenceDetectorWorker>(3, TimeUnit.DAYS).build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        const val WORK_NAME = "taskmind_recurrence_detector"
    }
}
