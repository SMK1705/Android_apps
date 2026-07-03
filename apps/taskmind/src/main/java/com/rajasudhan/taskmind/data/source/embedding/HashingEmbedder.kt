package com.rajasudhan.taskmind.data.source.embedding

import javax.inject.Inject
import javax.inject.Singleton

/**
 * A dependency-free, near-instant text embedder. It hashes a text's word tokens **and** character
 * trigrams into a fixed 256-dim vector (signed feature hashing), then L2-normalises it. No model, no
 * download, microseconds per call, ~1 KB per stored vector.
 *
 * It captures *lexical* similarity robustly — word-order-independent, and typo-/morphology-tolerant
 * via the trigrams ("dentist" ≈ "dentists", "appt" ≈ "appointment"'s prefix) — which is exactly what
 * near-duplicate merging (the same appointment arriving via SMS/WhatsApp/email) and fuzzy search
 * need. It is NOT a neural embedder: it won't relate "plumber" to "kitchen tap" with zero word
 * overlap. That deeper semantics can drop in later behind [Embedder].
 */
@Singleton
class HashingEmbedder @Inject constructor() : Embedder {

    override val dimension = DIMENSION

    override suspend fun embed(text: String): FloatArray? {
        val vec = FloatArray(DIMENSION)
        var any = false
        fun add(feature: String, weight: Float) {
            val h = feature.hashCode()
            val idx = h.mod(DIMENSION)                      // bucket (low bits)
            val sign = if ((h ushr 8) and 1 == 0) weight else -weight // sign from an independent bit
            vec[idx] += sign
            any = true
        }
        for (w in TOKEN.split(text.lowercase())) {
            // Keep single-character alphanumerics (a lone "2" or "A" is meaningful — it distinguishes
            // "buy 2 tickets" from "buy 4 tickets"); only empties and stopwords are dropped.
            if (w.isEmpty() || w in STOPWORDS) continue
            add("w:$w", WORD_WEIGHT)
            // Character trigrams over a boundary-padded word, so prefixes/suffixes match fuzzily.
            val padded = "^$w$"
            for (i in 0..padded.length - GRAM) add("g:" + padded.substring(i, i + GRAM), GRAM_WEIGHT)
        }
        if (!any) return null
        return Vectors.normalize(vec)
    }

    private companion object {
        const val DIMENSION = 256
        const val GRAM = 3
        // Whole-word matches carry most of the weight; trigrams add fuzzy robustness without drowning them.
        const val WORD_WEIGHT = 1.0f
        const val GRAM_WEIGHT = 0.35f
        val TOKEN = Regex("[^\\p{L}\\p{N}]+")
        val STOPWORDS = setOf(
            "the", "a", "an", "to", "of", "and", "or", "for", "in", "on", "at", "is", "it", "me",
            "my", "you", "your", "i", "we", "us", "by", "be", "with", "that", "this", "will", "can",
        )
    }
}
