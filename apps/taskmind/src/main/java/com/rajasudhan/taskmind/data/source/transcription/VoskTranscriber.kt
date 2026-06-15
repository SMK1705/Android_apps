package com.rajasudhan.taskmind.data.source.transcription

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device speech-to-text via **Vosk** (offline, no network). The model is NOT bundled — push a
 * Vosk model to internal storage. Two layouts are accepted:
 *   - an unpacked model directory at `filesDir/vosk-model/` (the folder containing `conf/`), or
 *   - a zip at `filesDir/vosk-model.zip`, which is unpacked automatically on first use.
 *
 * Recommended: `vosk-model-small-en-in-0.4` (Indian English, ~36 MB) from alphacephei.com/vosk/models.
 */
@Singleton
class VoskTranscriber @Inject constructor(
    @ApplicationContext private val appContext: Context
) : TranscriptionProvider {

    @Volatile
    private var model: Model? = null
    private val mutex = Mutex()

    private fun modelDir(): File = File(appContext.filesDir, "vosk-model")
    private fun modelZip(): File = File(appContext.filesDir, "vosk-model.zip")

    /** Expected model location, shown in Settings as the adb-push target. */
    fun modelDirPath(): String = modelDir().absolutePath

    fun isModelPresent(): Boolean = findModelRoot(modelDir()) != null || modelZip().exists()

    /** The directory that directly contains a Vosk model (`conf/` inside), searching nested folders. */
    private fun findModelRoot(base: File): File? {
        if (!base.exists()) return null
        if (File(base, "conf").exists()) return base
        return base.walkTopDown().firstOrNull { it.isDirectory && File(it, "conf").exists() }
    }

    private fun ensureUnpacked(): File? {
        findModelRoot(modelDir())?.let { return it }
        val zip = modelZip()
        if (zip.exists()) {
            runCatching { unzip(zip, modelDir()) }
            findModelRoot(modelDir())?.let { return it }
        }
        return null
    }

    private fun loadModel(): Model? {
        model?.let { return it }
        val dir = ensureUnpacked() ?: return null
        return runCatching { Model(dir.absolutePath) }.getOrNull()?.also { model = it }
    }

    /** Diagnostic: null on success, else the failure. */
    suspend fun tryLoad(): Throwable? = withContext(Dispatchers.Default) {
        if (!isModelPresent()) {
            return@withContext IllegalStateException("No Vosk model at ${modelDir().absolutePath}")
        }
        runCatching { mutex.withLock { loadModel() ?: error("Model present but failed to load") } }
            .exceptionOrNull()
    }

    override suspend fun transcribe(uri: Uri): String? = withContext(Dispatchers.Default) {
        val pcm = AudioDecoder.decodeToPcm16kMono(appContext, uri) ?: return@withContext null
        if (pcm.isEmpty()) return@withContext null
        mutex.withLock {
            val m = loadModel() ?: return@withContext null
            val recognizer = Recognizer(m, 16_000.0f)
            try {
                val sb = StringBuilder()
                var i = 0
                val chunk = 8000 // ~0.25 s of 16 kHz 16-bit audio
                while (i < pcm.size) {
                    val len = minOf(chunk, pcm.size - i)
                    val buf = pcm.copyOfRange(i, i + len)
                    if (recognizer.acceptWaveForm(buf, len)) {
                        sb.append(textOf(recognizer.result)).append(' ')
                    }
                    i += len
                }
                sb.append(textOf(recognizer.finalResult))
                sb.toString().replace(Regex("\\s+"), " ").trim().ifBlank { null }
            } finally {
                recognizer.close()
            }
        }
    }

    private fun textOf(resultJson: String): String =
        runCatching { JSONObject(resultJson).optString("text") }.getOrDefault("")

    private fun unzip(zip: File, dest: File) {
        dest.mkdirs()
        val destRoot = dest.canonicalFile.toPath()
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(dest, entry.name)
                // Guard against zip-slip: skip any entry whose path escapes the model dir
                // (e.g. "../../databases/x") so a crafted zip can't overwrite other app files.
                if (!outFile.canonicalFile.toPath().startsWith(destRoot)) {
                    entry = zis.nextEntry
                    continue
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zis.copyTo(it) }
                }
                entry = zis.nextEntry
            }
        }
    }
}
