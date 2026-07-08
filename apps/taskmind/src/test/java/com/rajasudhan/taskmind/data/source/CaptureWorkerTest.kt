package com.rajasudhan.taskmind.data.source

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.test.core.app.ApplicationProvider
import com.rajasudhan.taskmind.data.source.ocr.OcrEngine
import com.rajasudhan.taskmind.data.source.understanding.UnderstandingPipeline
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** The quick-capture worker that funnels shared text/images into the understanding pipeline. */
@RunWith(RobolectricTestRunner::class)
class CaptureWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val pipeline = mockk<UnderstandingPipeline>(relaxed = true)
    private val ocr = mockk<OcrEngine>(relaxed = true)

    private fun worker(vararg data: Pair<String, Any?>) =
        TestListenableWorkerBuilder<CaptureWorker>(context)
            .setInputData(workDataOf(*data))
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(c: Context, name: String, params: WorkerParameters) =
                    CaptureWorker(c, params, pipeline, ocr)
            })
            .build()

    @Test
    fun capturedText_isRunThroughThePipeline() = runTest {
        val result = worker("source" to "Manual test", "text" to "buy milk on the way home").doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        // Typed capture seeds the deterministic date parser (#116).
        coVerify { pipeline.processText("Manual test", "buy milk on the way home", seedSchedule = true) }
    }

    @Test
    fun blankText_doesNotInvokeThePipeline() = runTest {
        val result = worker("source" to "Shared", "text" to "   ").doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { pipeline.processText(any(), any(), any()) }
    }

    @Test
    fun imageCapture_pipelineThrows_returnsRetry_andKeepsTheImageForTheRetry() = runTest {
        // #K1: deleting the OCR'd image before the pipeline consumed it made a retry non-idempotent — the
        // retry re-OCR'd a missing file, got null, and reported success, silently losing the capture.
        val file = File.createTempFile("capture", ".png", context.cacheDir).apply { writeBytes(byteArrayOf(1, 2, 3)) }
        coEvery { ocr.recognize(any<File>()) } returns "dentist tuesday 3pm"
        coEvery { pipeline.processText(any(), any(), any()) } throws RuntimeException("LLM route down")

        val result = worker("source" to "Shared image", "image_path" to file.absolutePath).doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertTrue("captured image must survive a retryable failure so the retry can re-OCR it", file.exists())
        file.delete()
    }

    @Test
    fun imageCapture_success_ocrsThroughThePipeline_thenDeletesTheImage() = runTest {
        val file = File.createTempFile("capture", ".png", context.cacheDir).apply { writeBytes(byteArrayOf(1, 2, 3)) }
        coEvery { ocr.recognize(any<File>()) } returns "dentist tuesday 3pm"

        val result = worker("source" to "Shared image", "image_path" to file.absolutePath).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        // OCR'd image text does NOT seed the deterministic date parser (#116)...
        coVerify { pipeline.processText("Shared image", "dentist tuesday 3pm", seedSchedule = false) }
        // ...and only now, once it's safely through the pipeline, is the source image cleaned up.
        assertFalse("image is deleted after the pipeline consumes it", file.exists())
    }
}
