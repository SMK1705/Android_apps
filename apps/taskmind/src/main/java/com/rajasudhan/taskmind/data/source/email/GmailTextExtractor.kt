package com.rajasudhan.taskmind.data.source.email

import java.nio.charset.Charset
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

    /**
     * Decodes Gmail's base64url body data (tolerant of missing padding) using [charset]; null on
     * empty/failure. Gmail's `format=full` already transfer-decodes the Content-Transfer-Encoding, so the
     * bytes are the raw body — but in the *part's* charset, which is NOT always UTF-8. Callers pass the
     * charset from the part's Content-Type (see [charsetFor]) so an ISO-8859-1 / windows-1252 / Shift_JIS
     * body isn't mojibake'd by a hardcoded UTF-8 decode.
     */
    fun decodeBody(data: String?, charset: Charset = Charsets.UTF_8): String? {
        if (data.isNullOrBlank()) return null
        return try {
            val cleaned = data.trim().replace("\n", "").replace("\r", "")
            // Pad to a multiple of 4 so the strict decoder is happy with unpadded Gmail data.
            val padded = cleaned.padEnd((cleaned.length + 3) / 4 * 4, '=')
            String(Base64.getUrlDecoder().decode(padded), charset)
        } catch (e: Exception) {
            null
        }
    }

    /** The charset named in a part's `Content-Type: …; charset=…` header, or UTF-8 when absent/unknown. */
    fun charsetFor(headers: List<GmailHeader>): Charset {
        val contentType = header(headers, "Content-Type") ?: return Charsets.UTF_8
        val name = Regex("(?i);\\s*charset=\"?([^\";\\s]+)\"?").find(contentType)?.groupValues?.get(1)
            ?: return Charsets.UTF_8
        return runCatching { Charset.forName(name) }.getOrDefault(Charsets.UTF_8)
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
            decodeBody(payload.body?.data, charsetFor(payload.headers))?.let { if (it.isNotBlank()) return it }
        }
        for (part in payload.parts) {
            firstPartText(part, mimeType)?.let { return it }
        }
        return null
    }

    /**
     * If the message carries a calendar invitation (a `text/calendar` part — i.e. an .ics), pull its
     * key fields into one readable line the model can turn into an event. Many invites ship the human
     * text only inside the .ics, so without this an invite-only email yields nothing. Null when there
     * is no invite or no usable fields.
     */
    fun extractCalendarText(payload: GmailPayload?): String? {
        if (payload == null) return null
        val ics = firstPartText(payload, "text/calendar") ?: return null
        // ICS folds long lines with a CRLF followed by a space/tab; unfold before reading fields.
        val unfolded = ics.replace(Regex("\\r?\\n[ \\t]"), "")
        fun field(name: String): String? =
            Regex("(?im)^$name(?:;[^:\\r\\n]*)?:(.*)$").find(unfolded)?.groupValues?.get(1)
                ?.trim()?.replace("\\,", ",")?.replace("\\n", " ")?.takeIf { it.isNotBlank() }
        val summary = field("SUMMARY")
        val start = field("DTSTART")?.let { formatIcsDateTime(it) }
        val location = field("LOCATION")
        if (summary == null && start == null) return null
        return buildString {
            append("Calendar invitation.")
            if (summary != null) append(" Title: $summary.")
            if (start != null) append(" When: $start.")
            if (location != null) append(" Where: $location.")
        }
    }

    /** Turns an ICS DTSTART value (e.g. `20260625T150000Z`) into `2026-06-25 15:00`, UTC→local. */
    private fun formatIcsDateTime(raw: String): String = try {
        val core = raw.trim()
        if (core.endsWith("Z")) {
            java.time.LocalDateTime
                .parse(core.removeSuffix("Z"), java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                .atZone(java.time.ZoneOffset.UTC)
                .withZoneSameInstant(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        } else {
            val m = Regex("(\\d{4})(\\d{2})(\\d{2})(?:T(\\d{2})(\\d{2}))?").find(core) ?: return core
            val (y, mo, d, h, mi) = m.destructured
            if (h.isNotEmpty()) "$y-$mo-$d $h:$mi" else "$y-$mo-$d"
        }
    } catch (e: Exception) {
        raw
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
