package com.rajasudhan.taskmind.data.source.transcription

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioDecoderTest {

    private fun bytesOf(vararg samples: Int): ByteArray {
        val b = ByteArray(samples.size * 2)
        val sb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        samples.forEach { sb.put(it.toShort()) }
        return b
    }

    private fun shortsOf(bytes: ByteArray): ShortArray {
        val s = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(s)
        return s
    }

    @Test
    fun monoSameRateIsUnchanged() {
        val out = AudioDecoder.pcmDownmixAndResample(bytesOf(100, 200, 300), 16000, 1, 16000)
        assertArrayEquals(shortArrayOf(100, 200, 300), shortsOf(out))
    }

    @Test
    fun stereoDownmixesToMono() {
        // interleaved L,R: (100,200),(300,400) -> averages 150, 350
        val out = AudioDecoder.pcmDownmixAndResample(bytesOf(100, 200, 300, 400), 16000, 2, 16000)
        assertArrayEquals(shortArrayOf(150, 350), shortsOf(out))
    }

    @Test
    fun downsamplesHalfRate() {
        // 32k -> 16k (ratio 0.5): out[0]=src@0=100, out[1]=src@2=300
        val out = AudioDecoder.pcmDownmixAndResample(bytesOf(100, 200, 300, 400), 32000, 1, 16000)
        assertArrayEquals(shortArrayOf(100, 300), shortsOf(out))
    }

    @Test
    fun emptyReturnsEmpty() {
        assertEquals(0, AudioDecoder.pcmDownmixAndResample(ByteArray(0), 16000, 1, 16000).size)
    }
}
