package com.rajasudhan.taskmind.data.source

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic background scan (via WorkManager) of recent SMS/call logs. Battery-friendly:
 * runs on a schedule rather than constantly. De-duplication in the pipeline prevents repeats
 * across overlapping windows.
 */
@HiltWorker
class DataCollectionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val scanner: RecentDataScanner
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Window slightly larger than the run interval so nothing slips through the gaps.
        val since = System.currentTimeMillis() - 45 * 60 * 1000L
        return try {
            scanner.scanSince(since)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
