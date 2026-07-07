package com.rajasudhan.taskmind.data.source.wear

import com.rajasudhan.taskmind.data.source.appfunctions.DueItem
import com.rajasudhan.taskmind.data.source.appfunctions.DueTodayResult
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the pure tile-payload logic (#216): what the watch next-due tile shows for a given due-today
 * result. The Data Layer write itself is device-verified in the follow-up.
 */
class WearSyncTest {

    @Test
    fun formatWhen_timedItem_showsTheTime() {
        assertEquals("Today · 15:00", WearSync.formatWhen("15:00"))
    }

    @Test
    fun formatWhen_untimedItem_showsTodayOnly() {
        assertEquals("Today", WearSync.formatWhen(null))
        assertEquals("Today", WearSync.formatWhen("   "))
    }

    @Test
    fun nextDuePayload_nothingDue_isAllClear() {
        assertEquals("" to "", WearSync.nextDuePayload(DueTodayResult(emptyList(), 0)))
    }

    @Test
    fun nextDuePayload_picksTheSoonestItem() {
        val result = DueTodayResult(
            items = listOf(
                DueItem(1, "Call dentist", "09:00", "reminder"),
                DueItem(2, "Buy milk", "18:00", "todo"),
            ),
            count = 2,
        )
        assertEquals("Call dentist" to "Today · 09:00", WearSync.nextDuePayload(result))
    }

    @Test
    fun nextDuePayload_untimedItem_showsTodayOnly() {
        val result = DueTodayResult(listOf(DueItem(3, "Water the plants", null, "todo")), 1)
        assertEquals("Water the plants" to "Today", WearSync.nextDuePayload(result))
    }
}
