package com.rajasudhan.taskmind.data.source.wear

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Keeps the watch's next-due tile (#216) fresh by re-publishing the phone's soonest item on a periodic
 * cadence via [WearSync]. Cheap and read-only (one due-today query, one Data Layer write); when no watch
 * is paired the write just stays local. Pushing an update the instant the due set changes is a device-only
 * refinement left to the follow-up — the tile also re-reads on its own freshness interval.
 */
@HiltWorker
class WearSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val wearSync: WearSync,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        wearSync.publishNextDue()
        Result.success()
    } catch (e: Exception) {
        e.printStackTrace()
        Result.retry()
    }
}
