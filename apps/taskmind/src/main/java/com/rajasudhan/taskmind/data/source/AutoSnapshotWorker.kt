package com.rajasudhan.taskmind.data.source

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Daily job that writes a rolling plain-JSON snapshot of all notes to app-private storage — the
 * belt-and-suspenders data safety net (issue #161). Scheduled by [AutoSnapshotScheduler].
 */
@HiltWorker
class AutoSnapshotWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val snapshotManager: SnapshotManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // snapshot() returns 0 (success) when there are simply no notes to capture, and null only on a
        // real write failure (storage briefly unavailable) — worth one retry rather than losing the day.
        return if (snapshotManager.snapshot() != null) Result.success() else Result.retry()
    }
}
