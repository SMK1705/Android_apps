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

    /** True when the text is about phoning someone/a number, so a Call button is appropriate. */
    fun isCallIntent(vararg texts: String?): Boolean {
        val joined = texts.filterNotNull().joinToString(" ")
        return CALL_INTENT.containsMatchIn(joined)
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
