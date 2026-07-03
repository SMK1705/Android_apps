package com.rajasudhan.taskmind.data.source.embedding

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorsTest {

    @Test
    fun cosine_ofIdenticalUnitVectors_isOne() {
        val v = Vectors.normalize(floatArrayOf(3f, 4f))
        assertEquals(1f, Vectors.cosine(v, v), 1e-4f)
    }

    @Test
    fun cosine_ofOrthogonalVectors_isZero() {
        assertEquals(0f, Vectors.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-4f)
    }

    @Test
    fun cosine_ofOppositeVectors_isMinusOne() {
        assertEquals(-1f, Vectors.cosine(floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f)), 1e-4f)
    }

    @Test
    fun cosine_handlesLengthMismatch_andZeroVectors() {
        assertEquals(0f, Vectors.cosine(floatArrayOf(1f), floatArrayOf(1f, 2f)), 0f)
        assertEquals(0f, Vectors.cosine(floatArrayOf(0f, 0f), floatArrayOf(1f, 1f)), 0f)
    }

    @Test
    fun normalize_scalesToUnitLength() {
        val v = Vectors.normalize(floatArrayOf(3f, 4f))
        assertArrayEquals(floatArrayOf(0.6f, 0.8f), v, 1e-4f)
    }

    @Test
    fun bytes_roundTripAVector() {
        val v = floatArrayOf(0.1f, -0.25f, 1.5f, 0f, 42.75f)
        assertArrayEquals(v, Vectors.fromBytes(Vectors.toBytes(v)), 0f)
    }

    @Test
    fun toBytes_isFourBytesPerFloat() {
        assertTrue(Vectors.toBytes(FloatArray(256)).size == 1024)
    }
}
