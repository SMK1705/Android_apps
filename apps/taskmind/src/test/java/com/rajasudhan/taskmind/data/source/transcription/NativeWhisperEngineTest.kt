package com.rajasudhan.taskmind.data.source.transcription

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pure PCM→float conversion whisper.cpp is fed (#207). The JNI + native inference are device-verified
 * separately; this pins the byte-level decode (endianness, sign, normalization) that feeds it.
 */
class NativeWhisperEngineTest {

    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    @Test
    fun empty_isEmpty() {
        assertEquals(0, NativeWhisperEngine.pcm16ToFloat(ByteArray(0)).size)
    }

    @Test
    fun decodesLittleEndianSignedSamplesNormalized() {
        // 16-bit LE: [lo, hi]. 0x4000=16384→0.5, 0x7FFF=32767→~1, 0x8000=-32768→-1, 0x0000=0.
        val out = NativeWhisperEngine.pcm16ToFloat(bytes(0x00, 0x40, 0xFF, 0x7F, 0x00, 0x80, 0x00, 0x00))
        assertEquals(4, out.size)
        assertEquals(0.5f, out[0], 1e-6f)
        assertEquals(32767f / 32768f, out[1], 1e-6f)
        assertEquals(-1.0f, out[2], 1e-6f)
        assertEquals(0.0f, out[3], 1e-6f)
    }

    @Test
    fun negativeMidSample_signExtends() {
        // 0xFF 0xFF = -1 (LE signed) → -1/32768.
        val out = NativeWhisperEngine.pcm16ToFloat(bytes(0xFF, 0xFF))
        assertEquals(-1f / 32768f, out[0], 1e-6f)
    }

    @Test
    fun oddTrailingByte_isIgnored() {
        // 3 bytes = one whole sample + a dangling half — the half is dropped, not misread.
        assertEquals(1, NativeWhisperEngine.pcm16ToFloat(bytes(0x00, 0x40, 0x12)).size)
    }
}
