package com.rajasudhan.taskmind.data.source.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device OCR via **Tesseract** (offline, no telemetry). The trained data is NOT bundled — push
 * `eng.traineddata` to `filesDir/tessdata/` (see tools/setup_tesseract_model.py). Tesseract's
 * `init(dataPath, lang)` expects `dataPath` to contain a `tessdata/` subfolder, so dataPath = filesDir.
 */
@Singleton
class OcrEngine @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    private val mutex = Mutex()

    private fun dataPath(): String = appContext.filesDir.absolutePath
    private fun trainedFile(): File = File(File(appContext.filesDir, "tessdata"), "eng.traineddata")

    /** Expected trained-data location, shown in Settings as the adb-push target. */
    fun tessDataPath(): String = trainedFile().absolutePath

    fun isModelPresent(): Boolean = trainedFile().exists()

    /** OCRs the image at [uri]; null if no model, decode fails, or no text. Runs off the main thread. */
    suspend fun recognize(uri: Uri): String? = withContext(Dispatchers.Default) {
        val bitmap = decode { appContext.contentResolver.openInputStream(uri) } ?: return@withContext null
        recognizeBitmap(bitmap)
    }

    /** OCRs an image [file] (used for shared images copied into app storage). */
    suspend fun recognize(file: File): String? = withContext(Dispatchers.Default) {
        val bitmap = decode { file.inputStream() } ?: return@withContext null
        recognizeBitmap(bitmap)
    }

    private inline fun decode(open: () -> java.io.InputStream?): Bitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        open()?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()

    private suspend fun recognizeBitmap(bitmap: Bitmap): String? = mutex.withLock {
        if (!isModelPresent()) return null
        val tess = TessBaseAPI()
        try {
            if (!tess.init(dataPath(), "eng")) return null
            tess.setImage(bitmap)
            tess.getUTF8Text()?.replace(Regex("\\s+"), " ")?.trim()?.ifBlank { null }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { tess.recycle() }
            runCatching { bitmap.recycle() }
        }
    }

    /** Diagnostic for Settings: null on success, else the failure. */
    suspend fun tryLoad(): Throwable? = withContext(Dispatchers.Default) {
        if (!isModelPresent()) return@withContext IllegalStateException("No OCR model at ${tessDataPath()}")
        val tess = TessBaseAPI()
        try {
            if (tess.init(dataPath(), "eng")) null else IllegalStateException("Tesseract failed to initialise")
        } catch (e: Throwable) {
            e
        } finally {
            runCatching { tess.recycle() }
        }
    }
}
