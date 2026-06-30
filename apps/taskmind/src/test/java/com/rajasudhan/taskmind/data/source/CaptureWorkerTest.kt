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
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
        coVerify { pipeline.processText("Manual test", "buy milk on the way home") }
    }

    @Test
    fun blankText_doesNotInvokeThePipeline() = runTest {
        val result = worker("source" to "Shared", "text" to "   ").doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { pipeline.processText(any(), any()) }
    }
}
