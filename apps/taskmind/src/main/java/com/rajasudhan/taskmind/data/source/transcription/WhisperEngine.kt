package com.rajasudhan.taskmind.data.source.transcription

import androidx.annotation.VisibleForTesting
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The native whisper.cpp inference seam (#126). Isolating the JNI call behind this interface keeps
 * [WhisperTranscriber] — model presence, audio decode, delegation — fully unit-testable, and confines
 * the native code to one place.
 */
interface WhisperEngine {
    /** True once the native library + model are usable; false makes the whole second pass a no-op. */
    fun isAvailable(): Boolean

    /** Transcribe 16 kHz mono 16-bit little-endian [pcm] with the ggml model at [modelPath]; null on failure. */
    fun transcribe(pcm: ByteArray, modelPath: String): String?
}

/**
 * whisper.cpp binding (#207). Loads `libwhisper_jni.so` once at class-load; if the native lib isn't present
 * (a build without the native module, or an unsupported ABI) [isAvailable] stays false and the second pass
 * gracefully no-ops onto the Vosk first pass. [transcribe] converts the decoder's 16-bit LE PCM to the
 * normalized float samples whisper.cpp expects and hands off to the JNI bridge.
 */
@Singleton
class NativeWhisperEngine @Inject constructor() : WhisperEngine {

    override fun isAvailable(): Boolean = LIB_LOADED

    override fun transcribe(pcm: ByteArray, modelPath: String): String? {
        if (!LIB_LOADED || pcm.size < 2) return null
        val samples = pcm16ToFloat(pcm)
        if (samples.isEmpty()) return null
        return runCatching { nativeTranscribe(samples, modelPath) }
            .getOrNull()
            ?.trim()
            ?.ifBlank { null }
    }

    /** Runs whisper.cpp on [samples] (mono, [-1,1], 16 kHz) with the model at [modelPath]; null on failure. */
    private external fun nativeTranscribe(samples: FloatArray, modelPath: String): String?

    companion object {
        private val LIB_LOADED: Boolean = runCatching { System.loadLibrary("whisper_jni") }.isSuccess

        /**
         * 16-bit little-endian PCM bytes → normalized float samples in [-1, 1). Pure, so the conversion is
         * unit-tested without the native lib. A trailing odd byte (half a sample) is ignored.
         */
        @VisibleForTesting
        internal fun pcm16ToFloat(pcm: ByteArray): FloatArray {
            val n = pcm.size / 2
            val out = FloatArray(n)
            for (i in 0 until n) {
                val lo = pcm[i * 2].toInt() and 0xFF
                val hi = pcm[i * 2 + 1].toInt()   // signed high byte → sign-extends the sample
                out[i] = ((hi shl 8) or lo) / 32768f
            }
            return out
        }
    }
}
