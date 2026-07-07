package com.rajasudhan.taskmind.data.source.wear

import android.content.Context
import com.rajasudhan.taskmind.data.source.CaptureWorker
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.Runs
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Locks the wrist-capture receiver (#216): only capture-path messages with real speech reach the
 * pipeline, and they do so as a trimmed "Watch" capture through the shared [CaptureWorker].
 */
class WearCaptureListenerServiceTest {

    @Test
    fun ignoresMessagesOnOtherPaths() {
        assertNull(WearCaptureListenerService.captureTextFor("/taskmind/next_due", "hi".toByteArray()))
    }

    @Test
    fun trimsTheSpokenText() {
        assertEquals(
            "Call the dentist tomorrow at 3",
            WearCaptureListenerService.captureTextFor(
                WearContract.PATH_CAPTURE, "  Call the dentist tomorrow at 3  ".toByteArray(),
            ),
        )
    }

    @Test
    fun ignoresEmptyOrBlankSpeech() {
        assertNull(WearCaptureListenerService.captureTextFor(WearContract.PATH_CAPTURE, "   ".toByteArray()))
        assertNull(WearCaptureListenerService.captureTextFor(WearContract.PATH_CAPTURE, ByteArray(0)))
    }

    @Test
    fun decodesUtf8Speech() {
        assertEquals(
            "café ☕",
            WearCaptureListenerService.captureTextFor(
                WearContract.PATH_CAPTURE, "café ☕".toByteArray(Charsets.UTF_8),
            ),
        )
    }

    @Test
    fun handleMessage_enqueuesTrimmedTextAsAWatchCapture() {
        mockkObject(CaptureWorker.Companion)
        try {
            every { CaptureWorker.enqueue(any(), any(), any()) } just Runs
            val context = mockk<Context>()

            WearCaptureListenerService.handleMessage(
                context, WearContract.PATH_CAPTURE, "  Buy milk  ".toByteArray(),
            )

            verify(exactly = 1) { CaptureWorker.enqueue(context, "Watch", "Buy milk") }
        } finally {
            unmockkObject(CaptureWorker.Companion)
        }
    }

    @Test
    fun handleMessage_ignoresBlankSpeech() {
        mockkObject(CaptureWorker.Companion)
        try {
            every { CaptureWorker.enqueue(any(), any(), any()) } just Runs

            WearCaptureListenerService.handleMessage(mockk(), WearContract.PATH_CAPTURE, "   ".toByteArray())

            verify(exactly = 0) { CaptureWorker.enqueue(any(), any(), any()) }
        } finally {
            unmockkObject(CaptureWorker.Companion)
        }
    }
}
