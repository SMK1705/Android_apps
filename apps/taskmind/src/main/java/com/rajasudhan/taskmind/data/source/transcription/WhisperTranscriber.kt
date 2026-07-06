package com.rajasudhan.taskmind.data.source.transcription

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whisper second-pass transcription (#126) — the second [TranscriptionProvider] behind the seam, more
 * accurate than Vosk on accents and Hindi/Tamil/English code-switching. Like Vosk, the model is NOT
 * bundled: adb-push a quantized ggml model to `filesDir/whisper-model.bin` (shown in Settings).
 *
 * The actual whisper.cpp inference lives in [WhisperEngine]; its native binding is deferred to #207, so
 * until that lands [WhisperEngine.isAvailable] is false and this transcriber is a graceful no-op.
 */
@Singleton
class WhisperTranscriber @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val engine: WhisperEngine,
) : TranscriptionProvider {

    private fun modelFile(): File = File(appContext.filesDir, "whisper-model.bin")

    /** Expected model location, shown in Settings as the adb-push target. */
    fun modelPath(): String = modelFile().absolutePath

    fun isModelPresent(): Boolean = modelFile().let { it.exists() && it.length() > 0 }

    /** Whether a second pass can actually run right now (model present AND the native engine linked). */
    fun isReady(): Boolean = isModelPresent() && engine.isAvailable()

    override suspend fun transcribe(uri: Uri): String? = withContext(Dispatchers.Default) {
        if (!isReady()) return@withContext null
        val pcm = AudioDecoder.decodeToPcm16kMono(appContext, uri) ?: return@withContext null
        if (pcm.isEmpty()) return@withContext null
        engine.transcribe(pcm, modelFile().absolutePath)
            ?.replace(Regex("\\s+"), " ")?.trim()?.ifBlank { null }
    }
}
