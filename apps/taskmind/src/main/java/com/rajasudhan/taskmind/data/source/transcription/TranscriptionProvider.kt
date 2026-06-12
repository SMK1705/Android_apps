package com.rajasudhan.taskmind.data.source.transcription

import android.net.Uri

/**
 * Turns an audio recording into text. On-device by default ([VoskTranscriber]); the interface is the
 * seam where a cloud STT (or Whisper) implementation could be added later.
 */
interface TranscriptionProvider {
    /** Returns the transcript, or null if the model is missing, decode fails, or no speech is found. */
    suspend fun transcribe(uri: Uri): String?
}
