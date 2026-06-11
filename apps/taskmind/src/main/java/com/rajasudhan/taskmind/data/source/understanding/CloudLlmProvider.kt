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

            // generationConfig
            val genConfig = JSONObject()
                .put("temperature", 0.1)
                .put("responseMimeType", "application/json")
            put("generationConfig", genConfig)
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use "{\"items\": []}"
            val bodyStr = response.body?.string() ?: return@use "{\"items\": []}"
            val responseObj = JSONObject(bodyStr)
            return@withContext responseObj.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        }
    }
}
