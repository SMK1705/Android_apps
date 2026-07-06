package com.rajasudhan.taskmind.data.source.transcription

import android.net.Uri
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

/** The Vosk-first / Whisper-second routing (#126). */
class TranscriberTest {

    private val uri = mockk<Uri>(relaxed = true)
    private val vosk = mockk<VoskTranscriber>()
    private val whisper = mockk<WhisperTranscriber>()
    private val settings = mockk<SettingsManager>()
    private val transcriber = Transcriber(vosk, whisper, settings)

    private fun secondPass(on: Boolean, ready: Boolean) {
        every { settings.whisperSecondPassEnabled } returns on
        every { whisper.isReady() } returns ready
    }

    @Test
    fun secondPassOff_usesVoskOnly_andNeverRunsWhisper() = runTest {
        secondPass(on = false, ready = true)
        coEvery { vosk.transcribe(uri) } returns "vosk text"

        val r = transcriber.transcribe(uri)

        assertEquals("vosk text", r.text)
        assertFalse(r.secondPassUsed)
        coVerify(exactly = 0) { whisper.transcribe(any()) }
    }

    @Test
    fun secondPassNotReady_usesVoskOnly() = runTest {
        secondPass(on = true, ready = false) // model/native engine missing
        coEvery { vosk.transcribe(uri) } returns "vosk text"

        val r = transcriber.transcribe(uri)

        assertEquals("vosk text", r.text)
        assertFalse(r.secondPassUsed)
    }

    @Test
    fun whisperMateriallyImproves_adoptsWhisper() = runTest {
        secondPass(on = true, ready = true)
        coEvery { vosk.transcribe(uri) } returns "by rent monday"
        coEvery { whisper.transcribe(uri) } returns "pay the electricity bill on friday"

        val r = transcriber.transcribe(uri)

        assertEquals("pay the electricity bill on friday", r.text)
        assertTrue(r.secondPassUsed)
    }

    @Test
    fun whisperBarelyChangesIt_keepsVosk() = runTest {
        secondPass(on = true, ready = true)
        coEvery { vosk.transcribe(uri) } returns "team standup at nine tomorrow"
        coEvery { whisper.transcribe(uri) } returns "team standup at nine tomorrow"

        val r = transcriber.transcribe(uri)

        assertEquals("team standup at nine tomorrow", r.text)
        assertFalse(r.secondPassUsed) // not a material change → no wasted re-extract
    }

    @Test
    fun whisperReturnsNull_keepsVosk() = runTest {
        secondPass(on = true, ready = true)
        coEvery { vosk.transcribe(uri) } returns "vosk text"
        coEvery { whisper.transcribe(uri) } returns null

        val r = transcriber.transcribe(uri)

        assertEquals("vosk text", r.text)
        assertFalse(r.secondPassUsed)
    }

    @Test
    fun voskFailsButWhisperSucceeds_adoptsWhisper() = runTest {
        secondPass(on = true, ready = true)
        coEvery { vosk.transcribe(uri) } returns null
        coEvery { whisper.transcribe(uri) } returns "whisper rescued this recording"

        val r = transcriber.transcribe(uri)

        assertEquals("whisper rescued this recording", r.text)
        assertTrue(r.secondPassUsed)
    }
}
