package com.rajasudhan.taskmind.ui.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pure checklist parse/encode/decode/toggle logic. */
class ChecklistTest {

    @Test
    fun deriveSplitsCommaSeparatedList() {
        val items = Checklist.derive("Milk, eggs, bread, coffee")
        assertEquals(listOf("Milk", "eggs", "bread", "coffee"), items.map { it.text })
        assertTrue(items.none { it.checked })
    }

    @Test
    fun deriveSplitsNewlineBulletList() {
        val items = Checklist.derive("- Call bank\n- Pay rent\n• Buy stamps")
        assertEquals(listOf("Call bank", "Pay rent", "Buy stamps"), items.map { it.text })
    }

    @Test
    fun deriveReturnsEmptyForNonList() {
        assertTrue(Checklist.derive("Pick up the dry cleaning tomorrow").isEmpty())
        assertTrue(Checklist.derive("").isEmpty())
    }

    @Test
    fun encodeThenDecodeRoundTrips() {
        val items = listOf(Checklist.Item("Milk", true), Checklist.Item("Eggs", false))
        val decoded = Checklist.decode(Checklist.encode(items))
        assertEquals(items, decoded)
    }

    @Test
    fun toggleFlipsExactlyOneItem() {
        val items = listOf(Checklist.Item("a", false), Checklist.Item("b", false))
        val decoded = Checklist.decode(Checklist.toggleEncoded(items, 1))
        assertEquals(false, decoded[0].checked)
        assertEquals(true, decoded[1].checked)
    }
}
