package com.rajasudhan.taskmind.data.source

/**
 * Pure helpers for the Call action: decide whether a note is about phoning someone, and pull a
 * dialable phone number out of its text. Kept Android-free so it's unit-tested directly.
 */
object PhoneUtil {
    // A run that looks like a phone number: optional leading +, then digits and separators.
    private val CANDIDATE = Regex("""\+?\d[\d\s().\-]{5,}\d""")
    // Skip ISO dates ("2026-06-17") that otherwise look like an 8-digit number.
    private val DATE_LIKE = Regex("""\d{4}-\d{1,2}-\d{1,2}""")
    private val CALL_INTENT = Regex("""\b(call|calling|ring|dial)\b""", RegexOption.IGNORE_CASE)

    private val CALL_VERB_PREFIX = Regex("""^(call back|call|calling|ring|dial)\s+""", RegexOption.IGNORE_CASE)

    /** True when the text is about phoning someone/a number, so a Call button is appropriate. */
    fun isCallIntent(vararg texts: String?): Boolean {
        val joined = texts.filterNotNull().joinToString(" ")
        return CALL_INTENT.containsMatchIn(joined)
    }

    /**
     * A person name to look up in contacts when no number is in the text — e.g. a WhatsApp "call me"
     * whose notification only carries the sender name. Prefers the notification sender
     * ("Notification from John" → "John"); falls back to a call-intent title ("Call John" → "John").
     * Returns null when no plausible name is present (or it's actually a number).
     */
    fun personName(source: String?, title: String?): String? {
        source?.trim()?.let { s ->
            val prefix = "notification from "
            if (s.length > prefix.length && s.lowercase().startsWith(prefix)) {
                val name = s.substring(prefix.length).trim()
                if (name.isNotBlank() && extractFirst(name) == null) return name
            }
        }
        title?.trim()?.let { t ->
            val stripped = t.replaceFirst(CALL_VERB_PREFIX, "").trim()
            if (stripped.isNotBlank() && !stripped.equals(t, ignoreCase = true) && extractFirst(stripped) == null) {
                return stripped
            }
        }
        return null
    }

    /**
     * The first dialable phone number in [text], normalized for a `tel:` URI, or null. International
     * (`+…`) numbers need 7–15 digits; national numbers need 10–15 (so 8-digit dates don't match).
     */
    fun extractFirst(text: String?): String? {
        if (text.isNullOrBlank()) return null
        for (match in CANDIDATE.findAll(text)) {
            val raw = match.value.trim()
            if (DATE_LIKE.containsMatchIn(raw)) continue
            val digits = raw.count(Char::isDigit)
            val international = raw.trimStart().startsWith("+")
            if ((international && digits in 7..15) || digits in 10..15) return normalize(raw)
        }
        return null
    }

    /** Keep a leading +, drop everything but digits — a clean value for a `tel:` URI. */
    fun normalize(raw: String): String {
        val plus = raw.trimStart().startsWith("+")
        val digits = raw.filter(Char::isDigit)
        return if (plus) "+$digits" else digits
    }
}
