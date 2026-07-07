package com.rajasudhan.taskmind.data.source.understanding

import com.rajasudhan.taskmind.data.source.SettingsManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Engine selection + the MediaPipe safety-net fallback for the Gemma 3n migration seam (#212). */
class OnDeviceLlmProviderTest {

    private val mediaPipe = mockk<MediaPipeEngine>(relaxed = true)
    private val liteRtLm = mockk<LiteRtLmEngine>(relaxed = true)
    private val settings = mockk<SettingsManager>(relaxed = true)
    private fun provider() = OnDeviceLlmProvider(mediaPipe, liteRtLm, settings)

    @Test
    fun mediapipeSelected_usesMediaPipe() = runTest {
        every { settings.onDeviceEngine } returns "mediapipe"
        coEvery { mediaPipe.generate(any(), any()) } returns "MP-out"

        assertEquals("MP-out", provider().generate("sys", "user"))
        coVerify(exactly = 0) { liteRtLm.generate(any(), any()) }
    }

    @Test
    fun litertSelected_andRunnable_usesLiteRt() = runTest {
        // Forward-looking: once the LiteRT-LM engine is linked, selecting it actually routes there.
        every { settings.onDeviceEngine } returns "litertlm"
        coEvery { liteRtLm.generate(any(), any()) } returns "LRT-out"

        assertEquals("LRT-out", provider().generate("sys", "user"))
        coVerify(exactly = 0) { mediaPipe.generate(any(), any()) }
    }

    @Test
    fun litertSelected_butNotLinked_fallsBackToMediaPipe_whenModelPresent() = runTest {
        // Today's reality: LiteRT-LM throws (scaffold) → fall back to MediaPipe so on-device still works.
        every { settings.onDeviceEngine } returns "litertlm"
        coEvery { liteRtLm.generate(any(), any()) } throws UnsupportedOperationException("not linked")
        every { mediaPipe.isModelPresent() } returns true
        coEvery { mediaPipe.generate(any(), any()) } returns "MP-fallback"

        assertEquals("MP-fallback", provider().generate("sys", "user"))
    }

    @Test(expected = UnsupportedOperationException::class)
    fun litertSelected_notLinked_andNoMediaPipeModel_rethrows() = runTest {
        // No fallback available → propagate so RoutingLlmProvider can go to cloud / return empty.
        every { settings.onDeviceEngine } returns "litertlm"
        coEvery { liteRtLm.generate(any(), any()) } throws UnsupportedOperationException("not linked")
        every { mediaPipe.isModelPresent() } returns false

        provider().generate("sys", "user")
    }

    @Test
    fun unknownEngineId_defaultsToMediaPipe() = runTest {
        every { settings.onDeviceEngine } returns "totally-bogus"
        coEvery { mediaPipe.generate(any(), any()) } returns "MP-out"

        assertEquals("MP-out", provider().generate("sys", "user"))
    }

    @Test
    fun isModelPresent_trueWhenMediaPipeHasModel_evenIfSelectedEngineHasNone() = runTest {
        every { settings.onDeviceEngine } returns "litertlm"
        every { liteRtLm.isModelPresent() } returns false
        every { mediaPipe.isModelPresent() } returns true

        assertTrue(provider().isModelPresent())
    }

    @Test
    fun isModelPresent_falseWhenNeitherEngineHasModel() = runTest {
        every { settings.onDeviceEngine } returns "mediapipe"
        every { mediaPipe.isModelPresent() } returns false

        assertFalse(provider().isModelPresent())
    }

    @Test
    fun tryLoad_selectedEngineFails_reportsMediaPipeFallbackStatus() = runTest {
        // LiteRT-LM can't load; the reported availability is MediaPipe's (what will actually run).
        every { settings.onDeviceEngine } returns "litertlm"
        coEvery { liteRtLm.tryLoad() } returns UnsupportedOperationException("not linked")
        coEvery { mediaPipe.tryLoad() } returns null // MediaPipe loads fine

        assertTrue(provider().isAvailable())
    }
}
