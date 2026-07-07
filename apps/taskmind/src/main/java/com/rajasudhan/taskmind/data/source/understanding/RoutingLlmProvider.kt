package com.rajasudhan.taskmind.data.source.understanding

import com.rajasudhan.taskmind.data.source.SettingsManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks the LLM backend per the user's Settings choice:
 *  - on-device (default): local Gemma via MediaPipe ([OnDeviceLlmProvider]), no data leaves the phone.
 *  - cloud: only if the user explicitly selected it (or as a fallback when on-device is
 *    unavailable AND a cloud key is configured).
 */
@Singleton
class RoutingLlmProvider @Inject constructor(
    private val onDevice: OnDeviceLlmProvider,
    private val cloud: CloudLlmProvider,
    private val settingsManager: SettingsManager
) : LlmProvider {

    override suspend fun generate(systemMessage: String, userMessage: String): String {
        if (settingsManager.useOnDeviceLlm) {
            return try {
                onDevice.generate(systemMessage, userMessage)
            } catch (e: Exception) {
                // On-device unavailable (Nano not downloaded / unsupported). Fall back to
                // cloud only if the user has provided a key; otherwise return "nothing".
                if (settingsManager.llmApiKey.isNotBlank()) {
                    cloud.generate(systemMessage, userMessage)
                } else {
                    "{\"items\": []}"
                }
            }
        }
        return cloud.generate(systemMessage, userMessage)
    }

    override suspend fun generateList(systemMessage: String, userMessage: String): String {
        if (settingsManager.useOnDeviceLlm) {
            return try {
                onDevice.generateList(systemMessage, userMessage)
            } catch (e: Exception) {
                // On-device unavailable — fall back to the cloud breakdown only if a key is set;
                // otherwise an empty array, and the caller reports "couldn't break that down".
                if (settingsManager.llmApiKey.isNotBlank()) {
                    cloud.generateList(systemMessage, userMessage)
                } else {
                    "[]"
                }
            }
        }
        return cloud.generateList(systemMessage, userMessage)
    }

    override suspend fun generateIntent(systemMessage: String, userMessage: String): String {
        if (settingsManager.useOnDeviceLlm) {
            return try {
                onDevice.generateIntent(systemMessage, userMessage)
            } catch (e: Exception) {
                // On-device unavailable — fall back to cloud intent classification only if a key is set;
                // otherwise a safe empty "query" intent, which Ask handles by falling through to search.
                if (settingsManager.llmApiKey.isNotBlank()) {
                    cloud.generateIntent(systemMessage, userMessage)
                } else {
                    "{\"action\": \"query\"}"
                }
            }
        }
        return cloud.generateIntent(systemMessage, userMessage)
    }

    /**
     * Whether any effective engine can read an image/audio input (#211). Mirrors [generateFromMedia]'s
     * routing so callers (the screenshot/audio scan) can gate on it. False in Phase 0 — no engine reports
     * [supportsVision] yet — so the scan paths keep using OCR / transcription.
     */
    override fun supportsVision(): Boolean = route() != VisionRoute.NONE

    /**
     * Routes a multimodal extraction to the first effective vision engine, or null when none can see
     * (Phase 0: always null → the caller falls back to OCR / transcribe). The engine preference mirrors
     * [generate]: on-device first when selected, else the cloud fallback when a key is set; cloud when
     * cloud is the chosen backend.
     */
    override suspend fun generateFromMedia(systemMessage: String, userMessage: String, media: MediaInput): String? =
        when (route()) {
            VisionRoute.ON_DEVICE -> onDevice.generateFromMedia(systemMessage, userMessage, media)
            VisionRoute.CLOUD -> cloud.generateFromMedia(systemMessage, userMessage, media)
            VisionRoute.NONE -> null
        }

    private fun route(): VisionRoute = visionRoute(
        useOnDevice = settingsManager.useOnDeviceLlm,
        onDeviceVision = onDevice.supportsVision(),
        cloudVision = cloud.supportsVision(),
        hasCloudKey = settingsManager.llmApiKey.isNotBlank(),
    )

    /**
     * Whether extraction's DATA actually stays on the device, mirroring [generate]'s routing — so the
     * UI can label the engine honestly (#197) instead of hardcoding "on-device". True only when:
     *  - on-device is selected, AND
     *  - either the model is present (so it runs locally), OR there's no cloud key to fall back to
     *    (so nothing leaves the phone even though extraction can't run).
     * False whenever work goes to the cloud: cloud is selected, or on-device is selected but the model
     * is missing and a key IS configured (the runtime fallback in [generate] sends the text to Gemini).
     */
    fun isOnDeviceEffective(): Boolean =
        settingsManager.useOnDeviceLlm && (onDevice.isModelPresent() || settingsManager.llmApiKey.isBlank())
}

/** Which engine (if any) should handle a multimodal extraction. */
enum class VisionRoute { ON_DEVICE, CLOUD, NONE }

/**
 * The pure vision-routing decision (#211), extracted so it's unit-testable without the providers. Mirrors
 * the text [RoutingLlmProvider.generate] policy:
 *  - on-device selected → the on-device engine if it can see, else the cloud **only** when it can see and
 *    a key is configured (the same key-gated fallback the text path uses), else nobody;
 *  - cloud selected → the cloud engine if it can see, else nobody.
 *
 * In Phase 0 both `onDeviceVision` and `cloudVision` are false (no engine overrides `supportsVision`), so
 * this always returns [VisionRoute.NONE] and the feature ships dark.
 */
internal fun visionRoute(
    useOnDevice: Boolean,
    onDeviceVision: Boolean,
    cloudVision: Boolean,
    hasCloudKey: Boolean,
): VisionRoute = when {
    useOnDevice && onDeviceVision -> VisionRoute.ON_DEVICE
    useOnDevice && cloudVision && hasCloudKey -> VisionRoute.CLOUD
    useOnDevice -> VisionRoute.NONE
    cloudVision -> VisionRoute.CLOUD
    else -> VisionRoute.NONE
}
