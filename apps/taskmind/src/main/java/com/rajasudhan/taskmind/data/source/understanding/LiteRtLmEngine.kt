package com.rajasudhan.taskmind.data.source.understanding

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LiteRT-LM (Gemma 3n) on-device engine — the Gemma 3n migration target (#212, Phase 1). Google
 * recommends moving on-device LLM work off maintenance-only MediaPipe to LiteRT-LM; this is the seam it
 * plugs into.
 *
 * **Scaffold only.** The LiteRT-LM runtime isn't linked yet: wiring the artifact, the multi-GB `.litertlm`
 * model, the Gemma 3n chat template, and the KV-cache cap all have to be verified on a physical device
 * (and gated by the on-device eval), so they land in a device follow-up. Until then this reports "no
 * model" and refuses to run, so [OnDeviceLlmProvider] transparently falls back to [MediaPipeEngine] —
 * the same deferred-native pattern used for Whisper (#126 → #207). It throws
 * [UnsupportedOperationException] (an [Exception], not an [Error]) precisely so the provider's
 * `catch (Exception)` fallback catches it rather than crashing.
 */
@Singleton
class LiteRtLmEngine @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : OnDeviceEngine {

    override val option: OnDeviceEngineOption = OnDeviceEngineOption.LITE_RT_LM

    /** Where the Gemma 3n `.litertlm` bundle will live once the runtime is linked (mirrors MediaPipe). */
    fun modelFile(): File = File(appContext.filesDir, "model.litertlm")

    // Not runnable yet — see the class doc. Report unavailable so the provider falls back to MediaPipe.
    override fun isModelPresent(): Boolean = false

    override suspend fun generate(systemMessage: String, userMessage: String): String =
        throw notLinked()

    override suspend fun tryLoad(): Throwable? = notLinked()

    private fun notLinked() =
        UnsupportedOperationException("LiteRT-LM engine is not linked yet (Phase 1 device work, #212 follow-up)")
}
