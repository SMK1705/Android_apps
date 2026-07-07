package com.rajasudhan.taskmind.data.source.understanding

import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
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
class NanoEngine @Inject constructor() : OnDeviceEngine {

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

    private companion object {
        const val TAG = "NanoEngine"
    }
}
