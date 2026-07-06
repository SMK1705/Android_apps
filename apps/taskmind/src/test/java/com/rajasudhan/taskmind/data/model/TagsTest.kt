package com.rajasudhan.taskmind.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure-JVM tests for the auto-tag taxonomy + comma-encoding (#123). */
class TagsTest {

    @Test
    fun canonicalMapsAnyCasingIntoTheTaxonomyOrNull() {
        assertEquals("Money", Tags.canonical("money"))
        assertEquals("Work", Tags.canonical("  WORK  "))
        assertEquals("Health", Tags.canonical("Health"))
        assertNull(Tags.canonical("Urgent"))   // not in the taxonomy
        assertNull(Tags.canonical(""))
    }

    @Test
    fun encodeDecodeRoundTrips() {
        assertEquals("Money,Work", Tags.encode(listOf("Money", "Work")))
        assertEquals(listOf("Money", "Work"), Tags.decode("Money,Work"))
        // decode tolerates stray spaces and blanks, and treats null/blank as no tags.
        assertEquals(listOf("Money", "Work"), Tags.decode(" Money , Work "))
        assertEquals(emptyList<String>(), Tags.decode(null))
        assertEquals(emptyList<String>(), Tags.decode(""))
    }

    @Test
    fun taxonomyIsTheSevenClosedCategories() {
        assertEquals(
            listOf("Money", "Health", "Family", "Work", "Shopping", "Travel", "Home"),
            Tags.TAXONOMY
        )
    }
}
