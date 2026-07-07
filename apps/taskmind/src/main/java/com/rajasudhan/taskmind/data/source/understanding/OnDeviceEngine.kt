package com.rajasudhan.taskmind.data.source.understanding

/**
 * The on-device inference engines TaskMind can run its local LLM on. Gemma 3n migration (#212): the
 * app keeps the working MediaPipe engine selectable while LiteRT-LM is proven on real devices, so the
 * switch is never a hard cutover (invariant #1 of GEMMA_3N_MIGRATION.md).
 */
enum class OnDeviceEngineOption(val id: String) {
    /** MediaPipe LLM Inference over a Gemma `.task`/`.litertlm` — the current, working default. */
    MEDIAPIPE("mediapipe"),

    /** LiteRT-LM (Gemma 3n) — the migration target; a seam only until its runtime is linked on-device. */
    LITE_RT_LM("litertlm");

    companion object {
        /** Parse a stored id back to an option, defaulting to [MEDIAPIPE] for anything unknown/blank. */
        fun fromId(id: String?): OnDeviceEngineOption =
            entries.firstOrNull { it.id == id } ?: MEDIAPIPE
    }
}

/**
 * One selectable on-device text engine. [OnDeviceLlmProvider] picks the user's engine and delegates to
 * it, falling back to MediaPipe when the chosen engine can't run — so on-device never hard-fails
 * mid-migration. Each engine owns its own model-file resolution and (model-specific) chat template.
 */
interface OnDeviceEngine {
    /** Which option this engine implements (for selection + honest labelling). */
    val option: OnDeviceEngineOption

    /** Whether this engine's model file is present on disk (cheap; no load). */
    fun isModelPresent(): Boolean

    /** Load the model if needed and run one generation. Throws if the engine can't run on this device. */
    suspend fun generate(systemMessage: String, userMessage: String): String

    /** Attempts to load the model; null on success, else the failure (for diagnostics / availability). */
    suspend fun tryLoad(): Throwable?
}
