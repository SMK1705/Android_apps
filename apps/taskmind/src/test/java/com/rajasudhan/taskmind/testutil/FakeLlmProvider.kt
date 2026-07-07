package com.rajasudhan.taskmind.testutil

import com.rajasudhan.taskmind.data.source.understanding.LlmProvider
import com.rajasudhan.taskmind.data.source.understanding.MediaInput

/**
 * A [LlmProvider] that returns canned model output. Pass one response (reused for every call) or
 * several (consumed in order; the last one sticks) to exercise the pipeline's retry path.
 *
 * For the multimodal seam (#211): set [vision] true to report [supportsVision], and [mediaResponse]
 * to the JSON a vision engine would return from [generateFromMedia] (null = the engine declined).
 */
class FakeLlmProvider(
    vararg responses: String,
    private val vision: Boolean = false,
    private val mediaResponse: String? = null,
) : LlmProvider {
    private val responses: List<String> = responses.toList().ifEmpty { listOf("""{"items":[]}""") }
    var calls = 0
        private set
    var mediaCalls = 0
        private set
    val userMessages = mutableListOf<String>()

    override suspend fun generate(systemMessage: String, userMessage: String): String {
        userMessages += userMessage
        val idx = calls.coerceAtMost(this.responses.size - 1)
        calls++
        return this.responses[idx]
    }

    override fun supportsVision(): Boolean = vision

    override suspend fun generateFromMedia(systemMessage: String, userMessage: String, media: MediaInput): String? {
        mediaCalls++
        return mediaResponse
    }
}

/** Build a single LLM item JSON object matching [com.rajasudhan.taskmind.data.source.understanding.LlmItem]. */
fun llmItem(
    type: String = "note",
    title: String,
    notes: String = "",
    dueDate: String? = null,
    dueTime: String? = null,
    location: String? = null,
    recurrence: String? = null,
    priority: String = "normal",
    confidence: Double = 0.9,
): String {
    fun q(s: String?) = if (s == null) "null" else "\"$s\""
    return """{"type":"$type","title":"$title","notes":"$notes",""" +
        """"due_date":${q(dueDate)},"due_time":${q(dueTime)},"location":${q(location)},""" +
        """"recurrence":${q(recurrence)},"priority":"$priority","confidence":$confidence}"""
}

/** Wrap items into the `{"items":[...]}` envelope the model returns. */
fun llmResponse(vararg items: String): String = """{"items":[${items.joinToString(",")}]}"""
