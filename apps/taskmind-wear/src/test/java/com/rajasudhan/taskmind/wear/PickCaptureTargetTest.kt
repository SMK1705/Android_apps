package com.rajasudhan.taskmind.wear

import com.google.android.gms.wearable.Node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The capture-target selection behind wrist voice capture (#216 / #W1): send to a SINGLE phone that
 * actually advertises the capture capability, so a capture can't be silently lost (delivered to a node
 * without the app) or duplicated (fanned out to two app-bearing nodes).
 */
class PickCaptureTargetTest {

    private fun node(id: String, nearby: Boolean): Node = object : Node {
        override fun getId() = id
        override fun getDisplayName() = id
        override fun isNearby() = nearby
    }

    @Test
    fun noCapableNode_returnsNull_soTheWatchReportsNoPhone_notFalseSuccess() {
        assertNull(pickCaptureTarget(emptyList()))
    }

    @Test
    fun prefersANearbyNode() {
        val far = node("far", nearby = false)
        val near = node("near", nearby = true)
        assertEquals("near", pickCaptureTarget(listOf(far, near))?.id)
    }

    @Test
    fun fallsBackToAnyReachableNode_whenNoneAreNearby() {
        val a = node("a", nearby = false)
        val b = node("b", nearby = false)
        assertEquals("a", pickCaptureTarget(listOf(a, b))?.id)
    }

    @Test
    fun picksExactlyOne_soACaptureCantLandInTwoInboxes() {
        val one = node("one", nearby = true)
        val two = node("two", nearby = true)
        assertEquals("one", pickCaptureTarget(listOf(one, two))?.id) // one node, not both
    }
}
