package com.rajasudhan.taskmind.data.source

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.rajasudhan.taskmind.data.source.understanding.UnderstandingPipeline
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs captured text (shared from another app, or typed into the quick-add surfaces) through the
 * same understanding pipeline as every other source. A WorkManager job so it survives the launching
 * activity finishing immediately — capture never blocks on the on-device model.
 */
@HiltWorker
class CaptureWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val pipeline: UnderstandingPipeline
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val source = inputData.getString(KEY_SOURCE) ?: "Shared"
        val text = inputData.getString(KEY_TEXT)?.takeIf { it.isNotBlank() } ?: return Result.success()
        return try {
            pipeline.processText(source, text)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val KEY_SOURCE = "source"
        private const val KEY_TEXT = "text"

        /** Enqueue captured text from anywhere with just a Context (no DI needed in the caller). */
        fun enqueue(context: Context, source: String, text: String) {
            if (text.isBlank()) return
            val request = OneTimeWorkRequestBuilder<CaptureWorker>()
                .setInputData(workDataOf(KEY_SOURCE to source, KEY_TEXT to text))
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
