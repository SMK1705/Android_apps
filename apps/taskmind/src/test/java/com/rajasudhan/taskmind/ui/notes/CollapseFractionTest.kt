package com.rajasudhan.taskmind.ui.notes

import com.rajasudhan.taskmind.ui.bold.collapseFraction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pure header-collapse mapping behind the Notes collapsing header: the (negative) absorbed scroll
 * [offsetPx] against the measured region height [fullPx] and the pinned-bar floor [pinnedPx] → a 0f..1f
 * progress. Guards the clamps and the divide-by-zero on the first (unmeasured) frame.
 */
class CollapseFractionTest {

    private val full = 200f
    private val pinned = 58f   // maxCollapse = 142

    @Test
    fun atRest_isFullyExpanded() {
        assertEquals(0f, collapseFraction(0f, full, pinned), 0f)
    }

    @Test
    fun fullyAbsorbed_isFullyCollapsed() {
        assertEquals(1f, collapseFraction(-(full - pinned), full, pinned), 0f)
    }

    @Test
    fun halfAbsorbed_isHalfway() {
        assertEquals(0.5f, collapseFraction(-(full - pinned) / 2f, full, pinned), 1e-4f)
    }

    @Test
    fun overCollapse_clampsToOne() {
        assertEquals(1f, collapseFraction(-500f, full, pinned), 0f)
    }

    @Test
    fun positiveOffset_clampsToZero() {
        // Downward drag can't push the header below its expanded rest state.
        assertEquals(0f, collapseFraction(50f, full, pinned), 0f)
    }

    @Test
    fun regionShorterThanBar_neverCollapses() {
        // If the region measures smaller than the pinned bar, there is nothing to collapse.
        assertEquals(0f, collapseFraction(-10f, 40f, pinned), 0f)
    }

    @Test
    fun firstFrameBeforeMeasure_isZero_noDivideByZero() {
        // fullPx starts at 0 before layout; must not produce NaN/Infinity.
        val f = collapseFraction(-10f, 0f, pinned)
        assertEquals(0f, f, 0f)
    }
}
