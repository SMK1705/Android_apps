package com.rajasudhan.taskmind.data.source

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure tests for the notification-title normalisation that keeps group-chat learning stable (#199). */
class NotificationTextTest {

    @Test
    fun stripsGroupSummaryCountAndRotatingSender() {
        assertEquals("westshore bros 😎", NotificationText.conversationTitle("westshore bros 😎 (10 messages): ~ navin"))
        assertEquals("westshore bros 😎", NotificationText.conversationTitle("westshore bros 😎 (43 messages): ~ dwiz"))
        assertEquals("westshore bros 😎", NotificationText.conversationTitle("westshore bros 😎 (13 messages): navin westshore"))
        assertEquals("Project Team", NotificationText.conversationTitle("Project Team (1 message)")) // singular, no preview
    }

    @Test
    fun leavesStableTitlesUnchanged() {
        assertEquals("Amma", NotificationText.conversationTitle("Amma"))
        assertEquals("+91 98765 43210", NotificationText.conversationTitle("+91 98765 43210"))
        assertEquals("Alice", NotificationText.conversationTitle("Alice"))
        // A parenthesised group name that is NOT a message-count marker is preserved.
        assertEquals("Team (Q4) 😎", NotificationText.conversationTitle("Team (Q4) 😎 (7 messages): bob"))
    }

    @Test
    fun fallsBackToRawWhenNothingWouldRemain() {
        assertEquals("(5 messages)", NotificationText.conversationTitle("(5 messages)"))
    }

    @Test
    fun groupNotificationsCollapseToOneRejectionLearningKey() {
        // The bug: each raw title yields a different senderKey, so learning never accumulates. After
        // normalisation the group's notifications share one key and the dismiss count can reach the threshold.
        val a = "Notification from " + NotificationText.conversationTitle("westshore bros 😎 (10 messages): ~ navin")
        val b = "Notification from " + NotificationText.conversationTitle("westshore bros 😎 (29 messages): ~ rishikesh aher")
        assertEquals(RejectionLearner.senderKey(a), RejectionLearner.senderKey(b))
        assertEquals("westshore bros 😎", RejectionLearner.senderKey(a))
    }
}
