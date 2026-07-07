package com.rajasudhan.taskmind.data.source.understanding

interface LlmProvider {
    suspend fun generate(systemMessage: String, userMessage: String): String

    /**
     * Generate a plain list — e.g. a task broken into steps — as a JSON array of short strings
     * (`["Gather documents", "Fill the form"]`). Providers that support structured output (the cloud
     * one) constrain the reply to an array-of-strings schema so it can't come back as anything else;
     * others fall back to free-form [generate] and lean on tolerant parsing downstream.
     *
     * The default delegates to [generate], which is exactly right for the on-device model: it can't
     * enforce a schema, so a free-form call with a "return a JSON array" prompt is the best it can do.
     * Only the cloud and routing providers override this.
     */
    suspend fun generateList(systemMessage: String, userMessage: String): String =
        generate(systemMessage, userMessage)

    /**
     * Classify an "Ask TaskMind" utterance into a small constrained intent JSON (#128). The default
     * delegates to [generate] — right for the on-device model, which can't enforce a schema, so a
     * free-form call with a "return this JSON" prompt is the best it can do. The cloud and routing
     * providers override this to pin a flat intent schema (so it can't come back as the extraction
     * shape) and to route with fallback.
     */
    suspend fun generateIntent(systemMessage: String, userMessage: String): String =
        generate(systemMessage, userMessage)

    /**
     * Whether this provider can read an image/audio input directly (#211, Gemma 3n migration Phase 0).
     * Default **false** → text-only, so every existing provider and caller is untouched. Only a vision-
     * capable engine (a later phase) overrides this to true.
     */
    fun supportsVision(): Boolean = false

    /**
     * Multimodal extraction: run the model over [media] (an image/audio clip) with a text instruction,
     * returning the same extraction-JSON contract as [generate]. Returns **null** when this provider
     * can't see — the caller then falls back to the OCR / transcribe → [generate] path. The default is
     * a no-op null so text-only providers need no change; only a vision engine overrides it.
     */
    suspend fun generateFromMedia(systemMessage: String, userMessage: String, media: MediaInput): String? = null
}
