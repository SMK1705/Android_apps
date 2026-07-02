package com.rajasudhan.taskmind.data.source.understanding

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies the LLM JSON contract — in particular that omitted fields fall back to defaults, which is
 * what keeps small on-device models (that frequently drop `confidence`, `notes`, etc.) parseable.
 * Built the same way as production ([com.rajasudhan.taskmind.di.NetworkModule]).
 */
class LlmModelsParsingTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(LlmResponse::class.java)

    @Test
    fun parsesFullyPopulatedItem() {
        val json = """{"items":[{"type":"reminder","title":"Dentist","notes":"bring card",""" +
            """"due_date":"2026-06-15","due_time":"09:30","priority":"high","confidence":0.9}]}"""
        val res = adapter.fromJson(json)
        assertNotNull(res)
        assertEquals(1, res!!.items.size)
        val item = res.items[0]
        assertEquals("reminder", item.type)
        assertEquals("Dentist", item.title)
        assertEquals("bring card", item.notes)
        assertEquals("2026-06-15", item.dueDate)
        assertEquals("09:30", item.dueTime)
        assertEquals("high", item.priority)
        assertEquals(0.9, item.confidence, 1e-6)
    }

    @Test
    fun appliesDefaultsForOmittedFields() {
        // Small on-device models often emit just a title. Defaults must fill the rest.
        val item = adapter.fromJson("""{"items":[{"title":"Buy milk"}]}""")!!.items[0]
        assertEquals("note", item.type)
        assertEquals("Buy milk", item.title)
        assertEquals("", item.notes)
        assertNull(item.dueDate)
        assertNull(item.dueTime)
        assertEquals("normal", item.priority)   // omitted priority floors to normal
        assertEquals(0.7, item.confidence, 1e-6)
    }

    @Test
    fun parsesEmptyItemList() {
        val res = adapter.fromJson("""{"items":[]}""")
        assertNotNull(res)
        assertEquals(0, res!!.items.size)
    }

    @Test
    fun fencedJsonParsesAfterStripping() {
        val fenced = "```json\n{\"items\":[{\"title\":\"X\",\"confidence\":0.8}]}\n```"
        val res = adapter.fromJson(ExtractionHeuristics.stripJsonFences(fenced))
        assertNotNull(res)
        assertEquals("X", res!!.items[0].title)
        assertEquals(0.8, res.items[0].confidence, 1e-6)
    }
}
