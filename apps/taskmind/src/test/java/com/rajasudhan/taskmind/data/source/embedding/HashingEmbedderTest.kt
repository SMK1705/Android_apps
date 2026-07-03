package com.rajasudhan.taskmind.data.source.embedding

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class HashingEmbedderTest {

    private val embedder = HashingEmbedder()

    @Test
    fun producesAUnitVectorOfTheDeclaredSize() = runTest {
        val v = embedder.embed("dentist appointment tomorrow")!!
        assertEquals(embedder.dimension, v.size)
        var norm = 0f
        for (x in v) norm += x * x
        assertEquals(1f, sqrt(norm), 1e-3f)
    }

    @Test
    fun contentlessText_embedsToNull() = runTest {
        assertNull(embedder.embed("   "))
        assertNull(embedder.embed("the a to it")) // all stopwords / too short → no features
    }

    @Test
    fun rewordedSameThing_scoresFarHigherThanUnrelated() = runTest {
        val a = embedder.embed("dentist appointment on friday at 3pm")!!
        val reworded = embedder.embed("appointment with the dentist friday 3 pm")!!
        val unrelated = embedder.embed("buy groceries milk and eggs")!!

        val simSame = Vectors.cosine(a, reworded)
        val simUnrelated = Vectors.cosine(a, unrelated)
        assertTrue("reworded-same should be clearly related: $simSame", simSame > 0.45f)
        assertTrue("unrelated should be near zero: $simUnrelated", simUnrelated < 0.2f)
        assertTrue(simSame > simUnrelated)
    }

    @Test
    fun identicalText_isMaximallySimilar() = runTest {
        val a = embedder.embed("Fix the kitchen tap")!!
        val b = embedder.embed("fix the kitchen tap")!! // only case differs → lowercased the same
        assertEquals(1f, Vectors.cosine(a, b), 1e-4f)
    }

    @Test
    fun singleCharTokensDistinguishOtherwiseIdenticalText() = runTest {
        // Regression: a lone digit/letter must perturb the vector, so "buy 2 tickets" and
        // "buy 4 tickets" are NOT identical (they are distinct items, not duplicates).
        val two = embedder.embed("buy 2 tickets")!!
        val four = embedder.embed("buy 4 tickets")!!
        assertTrue("distinct counts must not collide", Vectors.cosine(two, four) < 0.999f)
    }

    @Test
    fun toleratesTypos_viaCharacterTrigrams() = runTest {
        val a = embedder.embed("dentist appointment")!!
        val typo = embedder.embed("dentst appointmnt")!!
        val unrelated = embedder.embed("grocery shopping")!!
        assertTrue(Vectors.cosine(a, typo) > Vectors.cosine(a, unrelated))
    }
}
