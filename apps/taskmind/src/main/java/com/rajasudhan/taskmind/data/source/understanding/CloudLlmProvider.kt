package com.rajasudhan.taskmind.data.source.understanding

import android.content.Context
import android.util.Base64
import com.rajasudhan.taskmind.data.model.Tags
import com.rajasudhan.taskmind.data.source.EgressLogger
import com.rajasudhan.taskmind.data.source.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val appContext: Context,
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
     * Ask TaskMind intent classification (#128): the reply is pinned to a flat intent object (a closed
     * `action` plus optional slots) so it never comes back wrapped in the extraction `{items:[…]}` shape.
     */
    override suspend fun generateIntent(systemMessage: String, userMessage: String): String =
        callGemini(systemMessage, userMessage, intentSchema(), "Cloud LLM ask intent", EMPTY_INTENT)

    /** Gemini 2.5 Flash is multimodal, so the cloud engine can read a screenshot directly (#213). */
    override fun supportsVision(): Boolean = true

    /**
     * Multimodal image extraction (#213, Gemma 3n migration Phase 2 — cloud engine): send the screenshot
     * itself to Gemini as a base64 `inline_data` part alongside the extraction instruction + schema, so a
     * poster/invite screenshot yields a fully-formed event WITHOUT going through Tesseract OCR.
     *
     * Returns **null** (not an empty-items JSON) on anything that means "no vision happened" — a non-image
     * mime, a blank key, an unreadable image, or a non-2xx reply — so [RecentDataScanner] falls back to the
     * OCR path instead of silently dropping the screenshot. Only a genuine model reply (even "no items")
     * counts as handled. Audio stays on the transcription path for now; only images are sent here.
     */
    override suspend fun generateFromMedia(systemMessage: String, userMessage: String, media: MediaInput): String? =
        withContext(Dispatchers.IO) {
            if (!media.mimeType.startsWith("image/")) return@withContext null
            val apiKey = settingsManager.llmApiKey
            if (apiKey.isBlank()) return@withContext null

            val base64 = runCatching {
                appContext.contentResolver.openInputStream(media.uri)?.use { it.readBytes() }
            }.getOrNull()?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
            if (base64.isNullOrBlank()) return@withContext null

            // Audit: the image is about to leave the device (metadata only, never the pixels themselves).
            egressLogger.record("generativelanguage.googleapis.com", "Cloud LLM vision extraction")

            val body = buildVisionRequestBody(systemMessage, userMessage, media.mimeType, base64, responseSchema())
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            sendForJsonOrNull(request)
        }

    /**
     * Ask's answer layer: a plain-prose answer grounded in the notes the caller supplies. Unlike every
     * other cloud call here the reply is NOT schema-pinned — the answer is a sentence, not JSON.
     *
     * Returns **null** on a blank key or any failure, so the caller keeps its deterministic answer and
     * the worst case is simply today's behaviour (cards with a generic lead-in), never an error.
     */
    suspend fun generateAnswer(systemMessage: String, userMessage: String): String? =
        withContext(Dispatchers.IO) {
            val apiKey = settingsManager.llmApiKey
            if (apiKey.isBlank()) return@withContext null

            // Audit: this is the one call that carries saved note CONTENT off the device.
            egressLogger.record("generativelanguage.googleapis.com", "Cloud LLM ask answer")

            val body = JSONObject().apply {
                put("systemInstruction", JSONObject().put("parts", JSONObject().put("text", systemMessage)))
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put("role", "user")
                            .put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
                    )
                )
                put(
                    "generationConfig",
                    JSONObject()
                        .put("temperature", 0.1)
                        .put("maxOutputTokens", 200)
                        // gemini-2.5-flash is a THINKING model and its thought tokens are billed against
                        // maxOutputTokens. Left on, ~190 of the 200 went to thinking and the answer was
                        // truncated mid-word — "The gate code is 447" for a real code of 4471 (#313 eval).
                        // Worse, the harder the question the longer it thinks, so truncation hit hardest
                        // exactly where being right matters. Reading a fact back out of supplied text
                        // needs no chain of thought; off is also faster, cheaper and deterministic.
                        .put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
                )
            }
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            sendForAnswerOrNull(request)?.trim()?.takeIf { it.isNotBlank() }
        }

    /**
     * Like [sendForJsonOrNull] but also rejects a **truncated** candidate. A half-sentence is the one
     * failure mode this layer must never surface: "The gate code is 447" reads like a complete answer
     * and is a wrong code. Belt-and-braces behind `thinkingBudget = 0` — null just falls the caller
     * back to the deterministic answer, which costs the user nothing.
     */
    private fun sendForAnswerOrNull(request: Request): String? = runCatching {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            val bodyStr = response.body?.string() ?: return@runCatching null
            val candidate = JSONObject(bodyStr).optJSONArray("candidates")?.optJSONObject(0)
                ?: return@runCatching null
            if (candidate.optString("finishReason") == "MAX_TOKENS") return@runCatching null
            candidate.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * Verifies the configured cloud key actually works — a tiny `generateContent` ping to the same
     * model/endpoint the extraction uses, surfacing the REAL outcome (unlike [generate], which masks
     * every failure into an empty-items fallback). Backs the Settings "Check cloud API" button — the
     * cloud counterpart to "Check on-device model".
     */
    suspend fun checkKey(): CloudKeyCheck = withContext(Dispatchers.IO) {
        val apiKey = settingsManager.llmApiKey
        if (apiKey.isBlank()) return@withContext CloudKeyCheck.NoKey

        // A key check still leaves the device — log it like any other egress.
        egressLogger.record("generativelanguage.googleapis.com", "Cloud LLM key check")

        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", "ping")))
                )
            )
            .put("generationConfig", JSONObject().put("temperature", 0).put("maxOutputTokens", 1))

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) CloudKeyCheck.Ok
                else CloudKeyCheck.Failed(describeError(response.code, response.body?.string().orEmpty()))
            }
        }.getOrElse { e -> CloudKeyCheck.Failed(e.message?.take(140) ?: "network error") }
    }

    /** Turns a Gemini error reply into a short, human-readable reason for the check button. */
    private fun describeError(code: Int, body: String): String {
        val msg = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }
            .getOrNull()?.takeIf { it.isNotBlank() }
        return when {
            code == 400 && msg?.contains("API key", ignoreCase = true) == true -> "Invalid API key"
            code == 400 -> msg ?: "Bad request (400)"
            code == 401 || code == 403 -> "Key rejected or lacks permission ($code)"
            code == 404 -> "Model not available for this key (404)"
            code == 429 -> "Rate limit / quota reached — the key is valid but throttled"
            code in 500..599 -> "Google server error ($code) — try again shortly"
            else -> msg ?: "HTTP $code"
        }
    }

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
                    // Auto-tags (#123): a bounded array of tags, each pinned to the closed taxonomy so
                    // the model can't invent a category. Optional (not in `required`) — an item may have
                    // no tag; the model returns an empty array and sanitizeTags maps that to null.
                    .put(
                        "tags",
                        JSONObject().put("type", "ARRAY").put(
                            "items",
                            JSONObject().put("type", "STRING").put("enum", JSONArray(Tags.TAXONOMY))
                        )
                    )
                    // "low" is included so the shared schema also serves the natural-language EDIT path
                    // (#115), which can lower priority; extraction still only ever emits normal/high (its
                    // prompt never asks for low).
                    .put("priority", JSONObject().put("type", "STRING").put("enum", JSONArray(listOf("low", "normal", "high"))))
                    .put("counterparty", nullableString())
                    .put("confidence", JSONObject().put("type", "NUMBER"))
            )
            .put("required", JSONArray(listOf("type", "title", "notes", "confidence")))
            .put("propertyOrdering", JSONArray(listOf("type", "title", "notes", "due_date", "due_time", "location", "recurrence", "tags", "priority", "counterparty", "confidence")))
        return JSONObject()
            .put("type", "OBJECT")
            .put("properties", JSONObject().put("items", JSONObject().put("type", "ARRAY").put("items", itemSchema)))
            .put("required", JSONArray(listOf("items")))
    }

    /** Structured-output schema for Magic Breakdown: a bare array of short step strings. */
    private fun stringArraySchema(): JSONObject =
        JSONObject().put("type", "ARRAY").put("items", JSONObject().put("type", "STRING"))

    /**
     * Structured-output schema for an Ask TaskMind intent (#128): a flat object whose `action` is a
     * closed enum; the slots are nullable strings (canonicalised app-side by [AskQuery]). Only `action`
     * is required, so a bare `{"action":"query"}` is a valid, minimal reply.
     */
    private fun intentSchema(): JSONObject {
        fun nullableString() = JSONObject().put("type", "STRING").put("nullable", true)
        return JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties",
                JSONObject()
                    .put("action", JSONObject().put("type", "STRING").put("enum", JSONArray(listOf("query", "create"))))
                    .put("type", nullableString())
                    .put("tag", nullableString())
                    .put("window", nullableString())
                    .put("status", nullableString())
                    .put("keyword", nullableString())
                    .put("text", nullableString())
            )
            .put("required", JSONArray(listOf("action")))
            .put("propertyOrdering", JSONArray(listOf("action", "type", "tag", "window", "status", "keyword", "text")))
    }

    private fun sendForJson(request: Request, fallback: String): String = runCatching {
        // Guard the whole send: a network IOException (timeout / socket / TLS) from execute() must yield
        // the schema-shaped [fallback], not propagate — otherwise it unwinds through processText and
        // aborts the rest of the source's scan, silently dropping the remaining rows (the call log has no
        // ledger, so those are lost for good). The inner runCatching only covers JSON navigation of an
        // already-received body; [sendForJsonOrNull] guards its send the same way.
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
    }.getOrElse { fallback }

    /**
     * Like [sendForJson] but returns **null** (rather than a schema-shaped empty) on any failure — the
     * vision contract, where null means "no result, fall back to OCR" and a real reply (even empty items)
     * means "the model saw the image; don't OCR it too".
     */
    private fun sendForJsonOrNull(request: Request): String? = runCatching {
        // The whole send is guarded: a network IOException (timeout / socket / TLS) or a malformed body
        // must yield null, not propagate — otherwise processMedia throws, the screenshot is left marked-
        // processed but never OCR'd, and it's silently lost. null here just means "fall back to OCR".
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            val bodyStr = response.body?.string() ?: return@runCatching null
            JSONObject(bodyStr)
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    companion object {
        // Empty results shaped to match each call's schema, so the caller's parser never chokes.
        private const val EMPTY_ITEMS = "{\"items\": []}"
        private const val EMPTY_ARRAY = "[]"
        private const val EMPTY_INTENT = "{\"action\": \"query\"}"
    }
}

/** Outcome of [CloudLlmProvider.checkKey] — backs the Settings "Check cloud API" button. */
sealed interface CloudKeyCheck {
    /** The key + model responded (HTTP 2xx). */
    data object Ok : CloudKeyCheck
    /** No cloud key is configured yet. */
    data object NoKey : CloudKeyCheck
    /** The call reached Google but failed; [reason] is a short human-readable cause. */
    data class Failed(val reason: String) : CloudKeyCheck
}

/**
 * Builds the Gemini `generateContent` body for a multimodal image extraction (#213): the schema-pinned
 * [systemMessage], plus a user turn carrying the image as a base64 `inline_data` part and the
 * [userMessage] text. Top-level + pure so the request shape — the exact `inline_data`/`mime_type`/`data`
 * keys the Vision API needs — is unit-testable without a network call.
 */
internal fun buildVisionRequestBody(
    systemMessage: String,
    userMessage: String,
    mimeType: String,
    base64Data: String,
    schema: JSONObject,
): JSONObject = JSONObject().apply {
    put("systemInstruction", JSONObject().put("parts", JSONObject().put("text", systemMessage)))
    val imagePart = JSONObject().put(
        "inline_data",
        JSONObject().put("mime_type", mimeType).put("data", base64Data)
    )
    val textPart = JSONObject().put("text", userMessage)
    // Image first, then the instruction — the model attends to "here is a screenshot; extract tasks".
    val userContent = JSONObject().put("role", "user").put("parts", JSONArray().put(imagePart).put(textPart))
    put("contents", JSONArray().put(userContent))
    put(
        "generationConfig",
        JSONObject()
            .put("temperature", 0.1)
            .put("responseMimeType", "application/json")
            .put("responseSchema", schema)
    )
}
