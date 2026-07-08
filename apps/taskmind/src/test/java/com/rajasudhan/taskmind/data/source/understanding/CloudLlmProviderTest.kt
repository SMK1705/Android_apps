package com.rajasudhan.taskmind.data.source.understanding

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.rajasudhan.taskmind.data.source.EgressLogger
import com.rajasudhan.taskmind.data.source.SettingsManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.IOException

/** The cloud multimodal vision engine (#213): capability, fallback semantics, and the Gemini image-request shape. */
@RunWith(RobolectricTestRunner::class)
class CloudLlmProviderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = mockk<SettingsManager>(relaxed = true)
    private val client = mockk<OkHttpClient>(relaxed = true)
    private val egress = mockk<EgressLogger>(relaxed = true)
    private fun provider() = CloudLlmProvider(context, settings, client, egress)

    @Test
    fun supportsVision_isTrue_becauseGeminiIsMultimodal() {
        assertTrue(provider().supportsVision())
    }

    @Test
    fun generateFromMedia_returnsNull_forNonImageMedia_soAudioStaysOnTranscription() = runTest {
        val audio = MediaInput(Uri.parse("content://media/audio/1"), "audio/wav")
        assertNull(provider().generateFromMedia("sys", "user", audio))
    }

    @Test
    fun generateFromMedia_returnsNull_onBlankKey_soCallerFallsBackToOcr() = runTest {
        // No key → no vision call happens; returning null (not empty-items) lets RecentDataScanner OCR instead.
        every { settings.llmApiKey } returns ""
        val image = MediaInput(Uri.parse("content://media/images/1"), "image/png")
        assertNull(provider().generateFromMedia("sys", "user", image))
    }

    @Test
    fun generateFromMedia_returnsNull_whenTheNetworkCallFails_soCallerFallsBackToOcr() = runTest {
        // A readable image (so we get past the byte-read) + a network IOException on send must yield null,
        // not propagate — otherwise processMedia throws and the screenshot is lost instead of OCR'd.
        every { settings.llmApiKey } returns "key-123"
        val call = mockk<Call>()
        every { call.execute() } throws IOException("network down")
        every { client.newCall(any()) } returns call
        val uri = Uri.parse("content://taskmind-test/shot.png")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(byteArrayOf(1, 2, 3)))

        assertNull(provider().generateFromMedia("sys", "user", MediaInput(uri, "image/png")))
    }

    @Test
    fun generate_returnsSchemaEmptyFallback_whenTheNetworkCallFails_soTheScanIsntAborted() = runTest {
        // #250: a network IOException on the TEXT send must yield the schema-shaped empty result, not
        // propagate — otherwise it unwinds through processText and drops the rest of the source's scan
        // (the call log has no ledger, so those rows are lost). Mirrors the vision path above.
        every { settings.llmApiKey } returns "key-123"
        val call = mockk<Call>()
        every { call.execute() } throws IOException("network down")
        every { client.newCall(any()) } returns call

        assertEquals("{\"items\": []}", provider().generate("sys", "user"))
    }

    @Test
    fun buildVisionRequestBody_carriesTheImageAsAnInlineDataPart_plusTheSchema() {
        val schema = JSONObject().put("type", "OBJECT")
        val body = buildVisionRequestBody("SYS", "USER", "image/png", "QUJD", schema)

        assertEquals("SYS", body.getJSONObject("systemInstruction").getJSONObject("parts").getString("text"))
        val parts = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        // part 0 = the image, with the exact keys the Vision API needs.
        val inline = parts.getJSONObject(0).getJSONObject("inline_data")
        assertEquals("image/png", inline.getString("mime_type"))
        assertEquals("QUJD", inline.getString("data"))
        // part 1 = the instruction text.
        assertEquals("USER", parts.getJSONObject(1).getString("text"))
        // Structured output stays pinned to the extraction schema.
        val gen = body.getJSONObject("generationConfig")
        assertEquals("application/json", gen.getString("responseMimeType"))
        assertTrue(gen.has("responseSchema"))
    }
}
