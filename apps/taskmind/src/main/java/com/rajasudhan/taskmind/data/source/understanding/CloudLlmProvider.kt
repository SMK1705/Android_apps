package com.rajasudhan.taskmind.data.source.understanding

import com.rajasudhan.taskmind.data.source.EgressLogger
import com.rajasudhan.taskmind.data.source.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class CloudLlmProvider @Inject constructor(
    private val settingsManager: SettingsManager,
    private val client: OkHttpClient,
    private val egressLogger: EgressLogger
) : LlmProvider {
    /** Task extraction: the reply is constrained to the `{items:[…]}` extraction schema. */
    override suspend fun generate(systemMessage: String, userMessage: String): String =
        callGemini(systemMessage, userMessage, responseSchema(), "Cloud LLM extraction", EMPTY_ITEMS)

    /**
     * Magic Breakdown: the reply is constrained to a bare array of strings, so a free-form
     * decomposition prompt comes back as `["step", "step", …]` — never coerced into the extraction
     * shape (which is what mangled it before, leaking field names into the checklist).
     */
    override suspend fun generateList(systemMessage: String, userMessage: String): String =
        callGemini(systemMessage, userMessage, stringArraySchema(), "Cloud LLM task breakdown", EMPTY_ARRAY)

    /**
     * Posts a Gemini generateContent request whose output is pinned to [schema], returning the
     * model's raw JSON text. [purpose] is what gets written to the egress ledger; [fallback] is
     * returned (without any network call) on a blank key, and (after a call) on a non-2xx response
     * or missing body — always shaped to match [schema] so the caller's parser stays happy.
     */
    private suspend fun callGemini(
        systemMessage: String,
        userMessage: String,
        schema: JSONObject,
        purpose: String,
        fallback: String,
    ): String = withContext(Dispatchers.IO) {
        val apiKey = settingsManager.llmApiKey
        if (apiKey.isBlank()) {
            return@withContext fallback
        }

        // Audit: record that data is about to leave the device (metadata only, never content).
        egressLogger.record("generativelanguage.googleapis.com", purpose)

        val jsonBody = JSONObject().apply {
            // system_instruction
            val sysPart = JSONObject().put("text", systemMessage)
            val sysInstruction = JSONObject().put("parts", sysPart)
            put("systemInstruction", sysInstruction)

            // contents
            val userPart = JSONObject().put("text", userMessage)
            val userContent = JSONObject().put("role", "user").put("parts", JSONArray().put(userPart))
            put("contents", JSONArray().put(userContent))

            // generationConfig — enforce the exact JSON shape with a response schema so the model
            // can't return malformed JSON or stray fields (far more reliable than asking in prose).
            val genConfig = JSONObject()
                .put("temperature", 0.1)
                .put("responseMimeType", "application/json")
                .put("responseSchema", schema)
            put("generationConfig", genConfig)
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return@withContext sendForJson(request, fallback)
    }

    /**
     * The Gemini structured-output schema mirroring [LlmResponse]/[LlmItem]: an object with an
     * `items` array whose `type` is constrained to the three kinds and the optional fields nullable.
     * `recurrence` is left a nullable string (vs. an enum) so the model can return null cleanly;
     * `ExtractionHeuristics.sanitizeRecurrence` clamps it to daily/weekly/monthly downstream.
     */
    private fun responseSchema(): JSONObject {
        fun nullableString() = JSONObject().put("type", "STRING").put("nullable", true)
        val itemSchema = JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties",
                JSONObject()
                    .put("type", JSONObject().put("type", "STRING").put("enum", JSONArray(listOf("reminder", "todo", "note", "waiting_on"))))
                    .put("title", JSONObject().put("type", "STRING"))
                    .put("notes", JSONObject().put("type", "STRING"))
                    .put("due_date", nullableString())
                    .put("due_time", nullableString())
                    .put("location", nullableString())
                    .put("recurrence", nullableString())
                    .put("priority", JSONObject().put("type", "STRING").put("enum", JSONArray(listOf("normal", "high"))))
                    .put("counterparty", nullableString())
                    .put("confidence", JSONObject().put("type", "NUMBER"))
            )
            .put("required", JSONArray(listOf("type", "title", "notes", "confidence")))
            .put("propertyOrdering", JSONArray(listOf("type", "title", "notes", "due_date", "due_time", "location", "recurrence", "priority", "counterparty", "confidence")))
        return JSONObject()
            .put("type", "OBJECT")
            .put("properties", JSONObject().put("items", JSONObject().put("type", "ARRAY").put("items", itemSchema)))
            .put("required", JSONArray(listOf("items")))
    }

    /** Structured-output schema for Magic Breakdown: a bare array of short step strings. */
    private fun stringArraySchema(): JSONObject =
        JSONObject().put("type", "ARRAY").put("items", JSONObject().put("type", "STRING"))

    private fun sendForJson(request: Request, fallback: String): String =
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use fallback
            val bodyStr = response.body?.string() ?: return@use fallback
            // Navigate with opt* (null on absence, never throwing) and fall back to the schema-shaped
            // empty result on any missing hop. A 200 can still carry no usable text — an empty
            // `candidates` (safety block) or a candidate with content but no `parts` (MAX_TOKENS
            // truncation). The old chained get* accessors threw JSONException there, which was never
            // caught and aborted the whole source's scan, silently dropping the remaining items.
            val text = runCatching {
                JSONObject(bodyStr)
                    .optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
            }.getOrNull()
            if (text.isNullOrBlank()) fallback else text
        }

    companion object {
        // Empty results shaped to match each call's schema, so the caller's parser never chokes.
        private const val EMPTY_ITEMS = "{\"items\": []}"
        private const val EMPTY_ARRAY = "[]"
    }
}
