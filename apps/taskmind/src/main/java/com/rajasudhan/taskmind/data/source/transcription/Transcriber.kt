package com.rajasudhan.taskmind.data.source.transcription

import android.net.Uri
import com.rajasudhan.taskmind.data.source.SettingsManager
import javax.inject.Inject
import javax.inject.Singleton

/** The chosen transcript plus whether the Whisper second pass supplied it (for logging/telemetry). */
data class TranscriptResult(val text: String?, val secondPassUsed: Boolean)

/**
 * Routes a saved recording through the transcription passes (#126): the Vosk first pass, optionally
 * upgraded by the Whisper second pass. Keeps Vosk as the primary (and the only live-dictation engine)
 * while letting Whisper's better accuracy on accents / code-switching win when it materially improves
 * the text — so the batch audio scan runs extraction once, on the best transcript available.
 */
@Singleton
class Transcriber @Inject constructor(
    private val vosk: VoskTranscriber,
    private val whisper: WhisperTranscriber,
    private val settingsManager: SettingsManager,
) {
    /** A transcript is possible at all only when the Vosk first-pass model is present. */
    fun isModelPresent(): Boolean = vosk.isModelPresent()

    /**
     * Best-effort transcript for [uri]. Runs the Whisper second pass only when the user enabled it, its
     * model is present, and the native engine is linked; adopts its result only when it MATERIALLY
     * changed the text ([TranscriptDiff]) — so a second pass that didn't move the needle never causes a
     * wasted re-extract downstream.
     */
    suspend fun transcribe(uri: Uri): TranscriptResult {
        val first = vosk.transcribe(uri)
        if (!settingsManager.whisperSecondPassEnabled || !whisper.isReady()) return TranscriptResult(first, false)
        val second = whisper.transcribe(uri)
        return if (TranscriptDiff.isMaterialChange(first, second)) TranscriptResult(second, secondPassUsed = true)
        else TranscriptResult(first ?: second, secondPassUsed = false)
    }
}
