package com.rajasudhan.taskmind.data.source.understanding

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * System **Gemini Nano** on-device engine via the ML Kit GenAI Prompt API (#214, Gemma 3n migration
 * Phase 4). Nano runs through the Android AICore system service, so on a supported device there is
 * **zero model download** — the model is system-provided and shared.
 *
 * It's a peer of [MediaPipeEngine] behind the [OnDeviceEngine] seam, selected via the on-device engine
 * setting; when it can't run (unsupported device, or the model isn't downloaded yet) it throws so
 * [OnDeviceLlmProvider] falls back to MediaPipe. Nano has no schema-constrained output, so its reply goes
 * through the same tolerant Moshi parse as the MediaPipe path.
 *
 * Device note (verified on a Galaxy S25 Ultra, which reports `FEATURE_NOT_FOUND`): both [checkStatus] and
 * `generateContent` can *throw* a `GenAiException` — not just return `UNAVAILABLE` — when the Prompt API
 * feature isn't provisioned. Both throwing paths are handled: [tryLoad] catches it, and [generate] lets it
 * propagate to the provider's catch. Availability is async while the seam's [isModelPresent] is sync, so
 * the last-known status is cached (refreshed by [tryLoad] / a successful [generate]), starting pessimistic.
 */
@Singleton
class NanoEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : OnDeviceEngine {

    override val option: OnDeviceEngineOption = OnDeviceEngineOption.NANO

    private val client by lazy { Generation.getClient() }

    @Volatile
    private var available = false

    /** Cached availability (see the class doc) — refreshed by [tryLoad] / [generate]. */
    override fun isModelPresent(): Boolean = available

    override suspend fun tryLoad(): Throwable? = try {
        when (client.checkStatus()) {
            FeatureStatus.AVAILABLE -> {
                available = true
                null
            }
            // Not installed / installing. We don't kick off (and block on) the multi-minute download here —
            // that belongs behind an explicit "download" action with progress; report the state instead.
            FeatureStatus.DOWNLOADABLE -> {
                available = false
                IllegalStateException("Gemini Nano can be downloaded on this device but isn't installed yet.")
            }
            FeatureStatus.DOWNLOADING -> {
                available = false
                IllegalStateException("Gemini Nano is downloading — try again in a moment.")
            }
            else -> {
                available = false
                IllegalStateException("Gemini Nano is not supported on this device.")
            }
        }
    } catch (e: Exception) {
        // On unsupported devices checkStatus throws (e.g. AICore FEATURE_NOT_FOUND) rather than returning
        // UNAVAILABLE — treat any failure as "not usable" so the provider falls back to MediaPipe.
        available = false
        Log.e(TAG, "Nano status check failed", e)
        e
    }

    override suspend fun generate(systemMessage: String, userMessage: String): String {
        val prompt = if (userMessage.isBlank()) systemMessage else "$systemMessage\n\n$userMessage"
        // No checkStatus pre-check — generateContent itself throws if Nano can't run, and that propagates
        // to OnDeviceLlmProvider's fallback; a pre-check would just double the AICore round-trip per call.
        val response = try {
            client.generateContent(prompt)
        } catch (e: Exception) {
            // Nano just failed (e.g. AICore dropped it mid-session) — clear the cache so isModelPresent()
            // can't keep reporting a stale "ready" to the honest engine label; then fall through to fallback.
            available = false
            throw e
        }
        available = true
        val text = response.candidates.firstOrNull()?.text
        Log.i(TAG, "Nano generated ${text?.length ?: 0} chars")
        return text?.takeIf { it.isNotBlank() } ?: "{\"items\": []}"
    }

    /**
     * Kick off the system Gemini Nano download (#226) — the explicit action [tryLoad] deliberately does NOT
     * start (it only reports `DOWNLOADABLE`). Collects the ML Kit download [kotlinx.coroutines.flow.Flow] and
     * reports 0..100 progress; returns null on success, else the failure. On a device where Nano can't be
     * downloaded, `download()` throws and that surfaces as the returned Throwable.
     */
    suspend fun download(onProgress: (Int) -> Unit): Throwable? = try {
        var total = 0L
        onProgress(0)
        client.download().collect { status ->
            when (status) {
                is DownloadStatus.DownloadStarted -> total = status.bytesToDownload
                is DownloadStatus.DownloadProgress ->
                    if (total > 0) {
                        onProgress((status.totalBytesDownloaded * 100 / total).toInt().coerceIn(0, 100))
                    }
                is DownloadStatus.DownloadCompleted -> onProgress(100)
                is DownloadStatus.DownloadFailed -> throw status.e
            }
        }
        available = true
        null
    } catch (e: Exception) {
        available = false
        Log.e(TAG, "Nano download failed", e)
        e
    }

    /** Nano (via the ML Kit Prompt API) is multimodal, so it can read a screenshot directly (#226). */
    override fun supportsVision(): Boolean = true

    /**
     * On-device multimodal image extraction (#226): decode the screenshot and send it to Nano as an
     * [ImagePart] alongside the extraction instruction, so a poster/invite screenshot yields a fully-formed
     * event WITHOUT going through Tesseract OCR — and, unlike the cloud vision path, the pixels never leave
     * the device. Returns **null** on anything that means "no vision happened" (non-image mime, unreadable
     * image, or an inference failure) so [RecentDataScanner] falls back to OCR instead of dropping the shot.
     */
    override suspend fun generateFromMedia(
        systemMessage: String,
        userMessage: String,
        media: MediaInput,
    ): String? = withContext(Dispatchers.IO) {
        if (!media.mimeType.startsWith("image/")) return@withContext null
        // Isolate the image read/decode from the engine call (mirrors CloudLlmProvider): an unreadable or
        // undecodable screenshot means "no vision happened → OCR fallback", NOT a Nano failure — so it must
        // NOT poison the availability cache (which routing reads to pick on-device vs cloud vision).
        val bitmap = runCatching {
            context.contentResolver.openInputStream(media.uri)?.use { it.readBytes() }
        }.getOrNull()
            ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            ?: return@withContext null

        val prompt = if (userMessage.isBlank()) systemMessage else "$systemMessage\n\n$userMessage"
        try {
            val request = GenerateContentRequest.Builder(ImagePart(bitmap), TextPart(prompt)).build()
            val response = client.generateContent(request)
            available = true
            // Mirror the cloud contract: a blank reply means "nothing seen" → return null so the caller still
            // OCRs the shot, instead of marking it handled with zero tasks. Only a genuine reply suppresses OCR.
            response.candidates.firstOrNull()?.text?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            // A real Nano inference failure (unsupported, dropped session) — clear the cache so isModelPresent()
            // can't report a stale "ready", and return null so the caller falls back to the OCR path.
            available = false
            Log.e(TAG, "Nano vision failed", e)
            null
        }
    }

    private companion object {
        const val TAG = "NanoEngine"
    }
}
