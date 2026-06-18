package com.rajasudhan.taskmind.data.source

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rajasudhan.taskmind.data.local.TaskMindDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic background scan (via WorkManager) of recent SMS/call logs/email + the app-usage digest.
 * Battery-friendly: runs on a schedule rather than constantly. Also performs retention cleanup
 * (purge actioned suggestions; delete notes older than the configured retention).
 */
@HiltWorker
class DataCollectionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val scanner: RecentDataScanner,
    private val dao: TaskMindDao,
    private val settingsManager: SettingsManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Scan since the last scan (shared watermark with the manual refresh), so a deferred or
            // skipped run doesn't leave a gap — it just covers more ground next time.
            scanner.scanIncremental()
            runRetentionCleanup()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private suspend fun runRetentionCleanup() {
        // Actioned (approved/rejected) suggestions are never shown again — always purge them.
        runCatching { dao.deletePurgeableSuggestions() }
        val days = settingsManager.retentionDays
        if (days > 0) {
            val cutoff = System.currentTimeMillis() - days.toLong() * 24 * 60 * 60 * 1000
            runCatching { dao.deleteNotesOlderThan(cutoff) }
        }
    }
}
