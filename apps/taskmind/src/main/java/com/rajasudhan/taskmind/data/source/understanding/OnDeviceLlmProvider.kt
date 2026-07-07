package com.rajasudhan.taskmind.data.source.understanding

import com.rajasudhan.taskmind.data.source.SettingsManager
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The on-device [LlmProvider]. Since the Gemma 3n migration (#212) it no longer runs inference itself —
 * it selects the user's [OnDeviceEngine] (MediaPipe today, LiteRT-LM once linked) and delegates to it,
 * falling back to [MediaPipeEngine] whenever the chosen engine can't run. That fallback is the migration's
 * safety net (doc invariant #1): the working engine stays available, so switching to LiteRT-LM is never a
 * hard cutover and a not-yet-linked engine never hard-fails on-device.
 *
 * The public surface (generate, isModelPresent, isAvailable, tryLoad, modelFile) is unchanged, so
 * [RoutingLlmProvider] and the Settings model-check UX keep working exactly as before.
 */
@Singleton
class OnDeviceLlmProvider @Inject constructor(
    private val mediaPipe: MediaPipeEngine,
    private val liteRtLm: LiteRtLmEngine,
    private val nano: NanoEngine,
    private val settingsManager: SettingsManager,
) : LlmProvider {

    /** The user-selected on-device engine, defaulting to MediaPipe for anything unknown/unset. */
    private fun selected(): OnDeviceEngine =
        when (OnDeviceEngineOption.fromId(settingsManager.onDeviceEngine)) {
            OnDeviceEngineOption.NANO -> nano
            OnDeviceEngineOption.LITE_RT_LM -> liteRtLm
            OnDeviceEngineOption.MEDIAPIPE -> mediaPipe
        }

    override suspend fun generate(systemMessage: String, userMessage: String): String {
        val engine = selected()
        return try {
            engine.generate(systemMessage, userMessage)
        } catch (e: Exception) {
            // The selected engine can't run (e.g. LiteRT-LM isn't linked yet). Never hard-fail on-device
            // mid-migration: fall back to MediaPipe when it has a model, else rethrow so RoutingLlmProvider
            // takes over (cloud / empty). Only Exceptions are caught — the scaffold throws one deliberately.
            if (engine !== mediaPipe && mediaPipe.isModelPresent()) {
                mediaPipe.generate(systemMessage, userMessage)
            } else {
                throw e
            }
        }
    }

    /** On-device is usable if the selected engine has its model, or MediaPipe (the fallback) does. */
    fun isModelPresent(): Boolean = selected().isModelPresent() || mediaPipe.isModelPresent()

    /** True if the effective engine actually loads on this device. */
    suspend fun isAvailable(): Boolean = tryLoad() == null

    /** Attempts to load; null on success, else the failure — reflecting the engine that would run. */
    suspend fun tryLoad(): Throwable? {
        val engine = selected()
        val err = engine.tryLoad() ?: return null
        // The selected engine can't load → report MediaPipe's status (the fallback that actually runs).
        return if (engine !== mediaPipe) mediaPipe.tryLoad() else err
    }

    /** The engine the user has selected (before any fallback) — for the Settings "check" + honest label. */
    fun selectedEngineOption(): OnDeviceEngineOption =
        OnDeviceEngineOption.fromId(settingsManager.onDeviceEngine)

    /**
     * The selected engine's OWN load status, with NO MediaPipe fallback — so the Settings "check on-device
     * model" action reports the truth about the engine the user picked (e.g. whether Gemini Nano is
     * actually available on this device), not the fallback that would run instead.
     */
    suspend fun checkSelectedEngine(): Throwable? = selected().tryLoad()

    /** MediaPipe owns the model-path UX (Settings → "Check on-device model"). */
    fun modelFile(): File = mediaPipe.modelFile()
    fun defaultModelFile(): File = mediaPipe.defaultModelFile()
}
