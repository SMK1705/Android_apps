package com.rajasudhan.taskmind.data.source.email

import java.util.Base64

/**
 * Pure helpers to pull readable text out of a Gmail [GmailPayload]. No Android dependencies, so it is
 * unit-testable on the plain JVM. Gmail encodes part bodies as **base64url**; multipart messages nest
 * the real content inside [GmailPayload.parts].
 */
object GmailTextExtractor {

    /** Case-insensitive header lookup (e.g. "From", "Subject"). */
    fun header(headers: List<GmailHeader>, name: String): String? =
        headers.firstOrNull { it.name.equals(name, ignoreCase = true) }?.value

    /** Decodes Gmail's base64url body data (tolerant of missing padding); null on empty/failure. */
    fun decodeBody(data: String?): String? {
        if (data.isNullOrBlank()) return null
        return try {
            val cleaned = data.trim().replace("\n", "").replace("\r", "")
            // Pad to a multiple of 4 so the strict decoder is happy with unpadded Gmail data.
            val padded = cleaned.padEnd((cleaned.length + 3) / 4 * 4, '=')
            String(Base64.getUrlDecoder().decode(padded), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Best plain-text representation of a message body: the first `text/plain` part, else the first
     * `text/html` part stripped of tags, searched recursively through the multipart tree. Null if none.
     */
    fun extractBodyText(payload: GmailPayload?): String? {
        if (payload == null) return null
        firstPartText(payload, "text/plain")?.let { return it }
        firstPartText(payload, "text/html")?.let { return stripHtml(it) }
        return null
    }

    private fun firstPartText(payload: GmailPayload, mimeType: String): String? {
        if (payload.mimeType.equals(mimeType, ignoreCase = true)) {
            decodeBody(payload.body?.data)?.let { if (it.isNotBlank()) return it }
        }
        for (part in payload.parts) {
            firstPartText(part, mimeType)?.let { return it }
        }
        return null
    }

    /** Very small HTML→text: drop tags, collapse whitespace, unescape a few common entities. */
    fun stripHtml(html: String): String =
        html
            .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
}
