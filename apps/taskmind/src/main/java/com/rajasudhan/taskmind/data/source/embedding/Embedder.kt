package com.rajasudhan.taskmind.data.source.embedding

/**
 * Turns a piece of text into a fixed-length unit vector for meaning-ish similarity (semantic-ish
 * search + near-duplicate detection). An interface so the engine is swappable: the shipped
 * [HashingEmbedder] is a dependency-free, near-instant lexical embedder, and a heavier neural
 * embedder (e.g. EmbeddingGemma) could later drop in behind the same seam.
 */
interface Embedder {
    /** Vector length every [embed] returns. Stable for a given embedder so stored vectors stay comparable. */
    val dimension: Int

    /**
     * Embed [text] into an L2-normalised vector (so cosine similarity is a plain dot product), or
     * null when this embedder can't produce one (e.g. a model-backed embedder with no model yet).
     */
    suspend fun embed(text: String): FloatArray?
}
