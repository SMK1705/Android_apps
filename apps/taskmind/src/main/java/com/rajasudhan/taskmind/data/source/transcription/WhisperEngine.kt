package com.rajasudhan.taskmind.data.source.transcription

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The native whisper.cpp inference seam (#126). Isolating the JNI call behind this interface keeps
 * [WhisperTranscriber] — model presence, audio decode, delegation — fully unit-testable, and confines
 * the not-yet-linked native code to one place.
 */
interface WhisperEngine {
    /** True once the native library + model are usable; false makes the whole second pass a no-op. */
    fun isAvailable(): Boolean

    /** Transcribe 16 kHz mono 16-bit little-endian [pcm] with the ggml model at [modelPath]; null on failure. */
    fun transcribe(pcm: ByteArray, modelPath: String): String?
}

/**
 * Placeholder binding until whisper.cpp is linked in (NDK/CMake + JNI + a quantized ggml model —
 * deferred to #207). Reports unavailable, so the second pass gracefully no-ops and audio falls back to
 * the Vosk first pass; no behaviour change ships until the native engine replaces this.
 */
@Singleton
class NativeWhisperEngine @Inject constructor() : WhisperEngine {
    override fun isAvailable(): Boolean = false
    override fun transcribe(pcm: ByteArray, modelPath: String): String? = null
}
