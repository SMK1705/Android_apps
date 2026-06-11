package com.rajasudhan.taskmind.data.source.understanding

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.rajasudhan.taskmind.data.source.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device LLM via MediaPipe LLM Inference running a local Gemma model (e.g. Gemma 3 4B).
 * Fully offline, free, no API/allowlist needed. The model is NOT bundled in the APK;
 * push a Gemma `.task` file to [modelFile] (see Settings → "Check on-device model").
 *
 * Throws if the model file is missing or fails to load so [RoutingLlmProvider] can fall back.
 */
@Singleton
class OnDeviceLlmProvider @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsManager: SettingsManager
) : LlmProvider {

    @Volatile
    private var engine: LlmInference? = null
    private var loadedPath: String? = null
    private val mutex = Mutex()

    /**
     * Default location if no custom path is set: the app's INTERNAL files dir.
     * Accepts either a classic `.task` or the newer `.litertlm` (Gemma 3n) bundle.
     * Internal storage avoids the scoped-storage FUSE quirk where files pushed into
     * Android/data/<pkg> via adb aren't visible to the running app.
     */
    fun defaultModelFile(): File {
        val dir = appContext.filesDir
        // Prefer the newer .litertlm (e.g. Gemma 3n) when present; fall back to a .task (e.g. 1B).
        return listOf("model.litertlm", "model.task")
            .map { File(dir, it) }
            .firstOrNull { it.exists() }
            ?: File(dir, "model.task")
    }

    /** Resolved model file: the user's configured path, else the default location. */
    fun modelFile(): File {
        val custom = settingsManager.onDeviceModelPath
        return if (custom.isNotBlank()) File(custom) else defaultModelFile()
    }

    fun isModelPresent(): Boolean = modelFile().exists()

    private fun createEngine(): LlmInference {
        val file = modelFile()
        check(file.exists()) { "Model not found at ${file.absolutePath}" }
        val options = LlmInferenceOptions.builder()
            .setModelPath(file.absolutePath)
            // The GPU (LiteRT/OpenCL) backend caps the KV-cache at 2048 tokens for these int4
            // Gemma builds — requesting more makes engine init fail with INVALID_ARGUMENT
            // ("Max number of tokens is larger than the maximum cache size: 4096 > 2048").
            // 2048 is ample for SMS/notifications (system prompt + input + JSON output).
            .setMaxTokens(2048)
            // Run on the GPU (Adreno) — required for larger models (Gemma 3n) to be usable.
            .setPreferredBackend(LlmInference.Backend.GPU)
            .build()
        return LlmInference.createFromOptions(appContext, options).also {
            loadedPath = file.absolutePath
        }
    }

    /** Returns a ready engine, rebuilding if the configured model path changed. */
    private fun engineForCurrentPath(): LlmInference {
        val target = modelFile().absolutePath
        if (engine != null && loadedPath != target) {
            runCatching { engine?.close() }
            engine = null
        }
        return engine ?: createEngine().also { engine = it }
    }

    override suspend fun generate(systemMessage: String, userMessage: String): String =
        withContext(Dispatchers.Default) {
            val content = if (userMessage.isBlank()) systemMessage else "$systemMessage\n\n$userMessage"
            // Instruction-tuned Gemma needs its chat turn template, or it returns nothing.
            val prompt = "<start_of_turn>user\n$content<end_of_turn>\n<start_of_turn>model\n"
            // MediaPipe LlmInference is not reentrant; serialize init + inference.
            mutex.withLock {
                engineForCurrentPath().generateResponse(prompt) ?: "{\"items\": []}"
            }
        }

    /** True if the model is present and actually loads on this device. */
    suspend fun isAvailable(): Boolean = tryLoad() == null

    /** Attempts to load the model; returns null on success or the failure for diagnostics. */
    suspend fun tryLoad(): Throwable? = withContext(Dispatchers.Default) {
        if (!isModelPresent()) {
            return@withContext IllegalStateException("No model at ${modelFile().absolutePath}")
        }
        try {
            mutex.withLock { engineForCurrentPath() }
            null
        } catch (e: Throwable) {
            android.util.Log.e("OnDeviceLlm", "Model load failed", e)
            e
        }
    }
}
