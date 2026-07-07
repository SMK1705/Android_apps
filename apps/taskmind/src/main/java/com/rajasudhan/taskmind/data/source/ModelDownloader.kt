package com.rajasudhan.taskmind.data.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Downloads an on-device model file (Vosk zip, Tesseract traineddata) straight into app storage. */
@Singleton
class ModelDownloader @Inject constructor(
    private val egressLogger: EgressLogger,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Streams [url] into [dest] (via a `.part` temp so a half-download never looks complete),
     * reporting 0..100 progress. Returns null on success, or the failure. [token] adds a
     * `Authorization: Bearer` header for gated hosts (e.g. a Hugging Face access token for a
     * license-gated Gemma model); omit it for public files (Vosk, Tesseract, whisper.cpp).
     */
    suspend fun download(url: String, dest: File, token: String? = null, onProgress: (Int) -> Unit): Throwable? =
        withContext(Dispatchers.IO) {
            runCatching {
                dest.parentFile?.mkdirs()
                val tmp = File(dest.parentFile, dest.name + ".part")
                val request = Request.Builder().url(url)
                    .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer ${token.trim()}") }
                    .build()
                // Fetching a model reaches out to a remote host, so it IS egress — record it (metadata
                // only, never content) to keep the Privacy ledger honest. Without this the "No data has
                // left this device" guarantee is silently false every time a model is downloaded.
                egressLogger.record(request.url.host, "On-device model download (${dest.name})")
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val body = resp.body ?: error("empty response body")
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        tmp.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            var downloaded = 0L
                            var lastPct = -1
                            while (true) {
                                val read = input.read(buf)
                                if (read == -1) break
                                output.write(buf, 0, read)
                                downloaded += read
                                if (total > 0) {
                                    val pct = (downloaded * 100 / total).toInt()
                                    if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                                }
                            }
                        }
                    }
                }
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
            }.exceptionOrNull()
        }
}
