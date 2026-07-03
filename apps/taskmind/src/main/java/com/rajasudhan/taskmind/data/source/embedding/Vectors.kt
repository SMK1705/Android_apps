package com.rajasudhan.taskmind.data.source.embedding

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Pure vector helpers: cosine similarity + compact byte encoding for BLOB storage. */
object Vectors {

    /**
     * Cosine similarity of two vectors. For L2-normalised vectors (what [Embedder] returns) this is
     * just their dot product, in [-1, 1]. Returns 0 for a length mismatch or a zero vector.
     */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        if (na == 0f || nb == 0f) return 0f
        return (dot / (Math.sqrt(na.toDouble()) * Math.sqrt(nb.toDouble()))).toFloat()
    }

    /** Scale [v] to unit length in place; a zero vector is left as-is. */
    fun normalize(v: FloatArray): FloatArray {
        var sum = 0f
        for (x in v) sum += x * x
        if (sum == 0f) return v
        val inv = (1.0 / Math.sqrt(sum.toDouble())).toFloat()
        for (i in v.indices) v[i] *= inv
        return v
    }

    /** Pack a float vector into a little-endian byte array for a Room BLOB column. */
    fun toBytes(v: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (x in v) buf.putFloat(x)
        return buf.array()
    }

    /** Unpack a byte array (from [toBytes]) back into a float vector. */
    fun fromBytes(bytes: ByteArray): FloatArray {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(bytes.size / 4)
        for (i in out.indices) out[i] = buf.float
        return out
    }
}
