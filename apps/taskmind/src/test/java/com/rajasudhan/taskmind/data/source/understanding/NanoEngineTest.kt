package com.rajasudhan.taskmind.data.source.understanding

import android.content.ContentResolver
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guard branches of NanoEngine's on-device vision path (#226) — the slices that return null WITHOUT touching
 * AICore, so they're JVM-testable (the lazy `Generation.getClient()` is never accessed on these paths). The
 * happy path (real ImagePart inference) is device-only, exactly like MediaPipeEngine.generate.
 */
class NanoEngineTest {

    private val resolver = mockk<ContentResolver>(relaxed = true)
    private val context = mockk<Context>(relaxed = true) {
        every { contentResolver } returns resolver
    }
    private val engine = NanoEngine(context)

    @Test
    fun generateFromMedia_returnsNull_forNonImageMedia() = runTest {
        val media = mockk<MediaInput>()
        every { media.mimeType } returns "audio/wav"

        // Non-image → null (OCR fallback) before ever reading the content or calling the model.
        assertNull(engine.generateFromMedia("sys", "user", media))
        verify(exactly = 0) { context.contentResolver }
    }

    @Test
    fun generateFromMedia_returnsNull_whenImageUnreadable() = runTest {
        val media = mockk<MediaInput>(relaxed = true)
        every { media.mimeType } returns "image/png"
        every { resolver.openInputStream(any()) } returns null

        // Unreadable URI → null (OCR fallback), and — critically — the availability cache is NOT poisoned,
        // since this is an image-read failure, not a Nano engine failure.
        assertNull(engine.generateFromMedia("sys", "user", media))
    }
}
