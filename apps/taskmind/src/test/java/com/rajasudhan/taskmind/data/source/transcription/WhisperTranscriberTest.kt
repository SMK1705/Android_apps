package com.rajasudhan.taskmind.data.source.transcription

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** The Whisper seam impl (#126): model-presence detection and the graceful no-op while the native
 *  engine is deferred (#207). The actual decode+inference path needs a real device/model. */
@RunWith(RobolectricTestRunner::class)
class WhisperTranscriberTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val engine = mockk<WhisperEngine>(relaxed = true)
    private val transcriber = WhisperTranscriber(context, engine)

    private fun modelFile() = File(context.filesDir, "whisper-model.bin")

    @After
    fun tearDown() { modelFile().delete() }

    @Test
    fun isModelPresent_falseWhenNoFile() {
        modelFile().delete()
        assertFalse(transcriber.isModelPresent())
    }

    @Test
    fun isModelPresent_trueOnceTheModelIsPushed() {
        modelFile().writeBytes(ByteArray(16))
        assertTrue(transcriber.isModelPresent())
    }

    @Test
    fun isReady_falseWhenEngineUnavailable_evenWithTheModel() {
        modelFile().writeBytes(ByteArray(16))
        every { engine.isAvailable() } returns false
        assertFalse(transcriber.isReady())
    }

    @Test
    fun transcribe_isAGracefulNoOp_whenNotReady() = runTest {
        // No model + no native engine (the deferred default) → null, without touching the decoder.
        modelFile().delete()
        every { engine.isAvailable() } returns false
        assertNull(transcriber.transcribe(mockk(relaxed = true)))
    }
}
