package com.rajasudhan.taskmind.data.source

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.rajasudhan.taskmind.data.source.ocr.OcrEngine
import com.rajasudhan.taskmind.data.source.understanding.UnderstandingPipeline
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

/**
 * Runs captured content — text (shared / typed into the quick-add surfaces) or a shared image (OCR'd
 * on-device) — through the same understanding pipeline as every other source. A WorkManager job so it
 * survives the launching activity finishing immediately; capture never blocks on the on-device model.
 */
@HiltWorker
class CaptureWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val pipeline: UnderstandingPipeline,
    private val ocrEngine: OcrEngine
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val source = inputData.getString(KEY_SOURCE) ?: "Shared"
        val imagePath = inputData.getString(KEY_IMAGE_PATH)
        return try {
            val text = if (imagePath != null) {
                val file = File(imagePath)
                val ocr = ocrEngine.recognize(file)
                runCatching { file.delete() }
                ocr
            } else {
                inputData.getString(KEY_TEXT)
            }
            if (!text.isNullOrBlank()) pipeline.processText(source, text)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val KEY_SOURCE = "source"
        private const val KEY_TEXT = "text"
        private const val KEY_IMAGE_PATH = "image_path"

        /** Enqueue captured text from anywhere with just a Context (no DI needed in the caller). */
        fun enqueue(context: Context, source: String, text: String) {
            if (text.isBlank()) return
            val request = OneTimeWorkRequestBuilder<CaptureWorker>()
                .setInputData(workDataOf(KEY_SOURCE to source, KEY_TEXT to text))
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        /** Enqueue a captured image (already copied into app storage) for on-device OCR. */
        fun enqueueImage(context: Context, source: String, imageFile: File) {
            val request = OneTimeWorkRequestBuilder<CaptureWorker>()
                .setInputData(workDataOf(KEY_SOURCE to source, KEY_IMAGE_PATH to imageFile.absolutePath))
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
