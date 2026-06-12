package com.rajasudhan.taskmind.data.source.transcription

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decodes compressed audio recordings to the 16 kHz mono 16-bit PCM that Vosk expects. */
object AudioDecoder {

    /** Decodes [uri] to 16 kHz mono 16-bit little-endian PCM. Null on failure. */
    fun decodeToPcm16kMono(context: Context, uri: Uri): ByteArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            val out = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    if (info.size > 0) {
                        val buf = codec.getOutputBuffer(outIdx)!!
                        val chunk = ByteArray(info.size)
                        buf.get(chunk)
                        buf.clear()
                        out.write(chunk)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            pcmDownmixAndResample(out.toByteArray(), srcRate, channels, 16_000)
        } catch (e: Exception) {
            android.util.Log.e("AudioDecoder", "decode failed", e)
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /**
     * Downmixes [pcm] (16-bit LE, [channels] interleaved samples at [srcRate] Hz) to mono and
     * linearly resamples to [dstRate]. Pure (no Android deps) — unit-tested.
     */
    fun pcmDownmixAndResample(pcm: ByteArray, srcRate: Int, channels: Int, dstRate: Int): ByteArray {
        if (pcm.size < 2 || srcRate <= 0 || channels <= 0) return ByteArray(0)
        val shorts = ShortArray(pcm.size / 2)
        ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)

        val mono = if (channels == 1) shorts else {
            val frames = shorts.size / channels
            ShortArray(frames) { f ->
                var sum = 0
                for (c in 0 until channels) sum += shorts[f * channels + c]
                (sum / channels).toShort()
            }
        }

        val resampled = if (srcRate == dstRate) mono else {
            val ratio = dstRate.toDouble() / srcRate
            val outLen = (mono.size * ratio).toInt().coerceAtLeast(1)
            ShortArray(outLen) { i ->
                val srcPos = i / ratio
                val idx = srcPos.toInt()
                val frac = srcPos - idx
                val a = mono[idx.coerceIn(0, mono.size - 1)].toInt()
                val b = mono[(idx + 1).coerceIn(0, mono.size - 1)].toInt()
                (a + (b - a) * frac).toInt().toShort()
            }
        }

        val outBytes = ByteArray(resampled.size * 2)
        ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(resampled)
        return outBytes
    }
}
