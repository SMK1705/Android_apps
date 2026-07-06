package com.rajasudhan.taskmind.data.source.understanding

/**
 * Pure lexical guard for the NON-destructive near-duplicate flag (#145).
 *
 * Semantic similarity ([com.rajasudhan.taskmind.data.source.embedding.SemanticIndex]) is a broad,
 * unreliable signal for "is this the same item": the shipped hashing embedder scores "Buy 2 tickets"
 * vs "Buy 4 tickets" ~0.92, and even a neural embedder rates "Call Alice"/"Call Bob" close though
 * they're distinct tasks. So a semantically-similar note is only ever *flagged* as a POSSIBLE
 * duplicate when it also clears this strict lexical check — a high overlap of normalised content
 * words AND a compatible date. Nothing here drops a capture; the flag is an in-Inbox advisory, so a
 * false negative just means "no flag" and a false positive is one tap to dismiss. This keeps it
 * precise so genuinely different items (room A vs B, 2 vs 4 tickets, Alice vs Bob) are never flagged.
 *
 * No Android deps, so it's covered by a plain-JVM unit test.
 */
object NearDuplicate {

    /** Content-word overlap (Jaccard) at/above which two items are treated as the same capture. */
    const val TOKEN_OVERLAP = 0.6

    // Only MULTI-character function words are stripped. Single letters and digits are kept as content
    // so "room A" vs "room B" and "buy 2" vs "buy 4" stay distinguishable — the exact false-merge the
    // issue calls out. Dropping filler keeps "call the office" ≈ "call office".
    private val STOP = setOf(
        "the", "to", "for", "of", "and", "or", "at", "in", "on", "is", "it", "an", "as", "by",
        "my", "me", "you", "your", "with", "this", "that", "please", "pls", "about", "re",
    )

    private val NON_WORD = Regex("[^a-z0-9]+")

    /** Normalised content tokens: lowercased, split on non-alphanumerics, multi-char stopwords removed. */
    fun tokens(text: String): Set<String> =
        text.lowercase().split(NON_WORD).filter { it.isNotBlank() && it !in STOP }.toSet()

    /** Jaccard overlap of the two texts' content-token sets; 0 when either has no content tokens. */
    fun tokenOverlap(a: String, b: String): Double {
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val intersection = ta.count { it in tb }.toDouble()
        return intersection / (ta.size + tb.size - intersection)
    }

    /** Dates are compatible for a duplicate if identical, or at least one side is undated. */
    fun datesCompatible(a: String?, b: String?): Boolean =
        a.isNullOrBlank() || b.isNullOrBlank() || a == b

    /**
     * True when [candidateText] is a likely re-capture of [existingText] — high content-word overlap
     * AND a compatible date. Used only to CONFIRM a semantic candidate before flagging it; it never
     * gates whether the capture is kept (it always is).
     */
    fun isLikelyDuplicate(
        candidateText: String, candidateDate: String?,
        existingText: String, existingDate: String?,
    ): Boolean =
        datesCompatible(candidateDate, existingDate) &&
            tokenOverlap(candidateText, existingText) >= TOKEN_OVERLAP
}
