package com.rajasudhan.taskmind.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for the pure sender-extraction used by the on-device rejection learning. */
class RejectionLearnerTest {

    @Test
    fun extractsSenderAfterFrom() {
        assertEquals("+91 98765 43210", RejectionLearner.senderKey("SMS from +91 98765 43210"))
        assertEquals("alice@example.com", RejectionLearner.senderKey("Email (me@x.com) from Alice@Example.com"))
        assertEquals("whatsapp", RejectionLearner.senderKey("Notification from WhatsApp"))
    }

    @Test
    fun returnsNullForSourcesWithoutASender() {
        assertNull(RejectionLearner.senderKey("Recording: voice_001"))
        assertNull(RejectionLearner.senderKey("Screenshot: Screenshot_2026.png"))
        assertNull(RejectionLearner.senderKey("Call Log"))
        assertNull(RejectionLearner.senderKey("App Usage"))
        assertNull(RejectionLearner.senderKey("Manual entry"))
        assertNull(RejectionLearner.senderKey("Shared"))
    }
}
