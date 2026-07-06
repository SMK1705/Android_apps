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
