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
    override suspend fun generate(systemMessage: String, userMessage: String): String = withContext(Dispatchers.IO) {
        val apiKey = settingsManager.llmApiKey
        if (apiKey.isBlank()) {
            return@withContext "{\"items\": []}"
        }

        // Audit: record that data is about to leave the device (metadata only, never content).
        egressLogger.record("generativelanguage.googleapis.com", "Cloud LLM extraction")

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
                .put("responseSchema", responseSchema())
            put("generationConfig", genConfig)
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return@withContext sendForJson(request)
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
                    .put("type", JSONObject().put("type", "STRING").put("enum", JSONArray(listOf("reminder", "todo", "note"))))
                    .put("title", JSONObject().put("type", "STRING"))
                    .put("notes", JSONObject().put("type", "STRING"))
                    .put("due_date", nullableString())
                    .put("due_time", nullableString())
                    .put("location", nullableString())
                    .put("recurrence", nullableString())
                    .put("priority", JSONObject().put("type", "STRING").put("enum", JSONArray(listOf("normal", "high"))))
                    .put("confidence", JSONObject().put("type", "NUMBER"))
            )
            .put("required", JSONArray(listOf("type", "title", "notes", "confidence")))
            .put("propertyOrdering", JSONArray(listOf("type", "title", "notes", "due_date", "due_time", "location", "recurrence", "priority", "confidence")))
        return JSONObject()
            .put("type", "OBJECT")
            .put("properties", JSONObject().put("items", JSONObject().put("type", "ARRAY").put("items", itemSchema)))
            .put("required", JSONArray(listOf("items")))
    }

    private fun sendForJson(request: Request): String =
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use "{\"items\": []}"
            val bodyStr = response.body?.string() ?: return@use "{\"items\": []}"
            JSONObject(bodyStr)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        }
}
