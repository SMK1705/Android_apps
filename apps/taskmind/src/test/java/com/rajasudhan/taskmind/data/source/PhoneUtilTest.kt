package com.rajasudhan.taskmind.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneUtilTest {

    @Test
    fun extractsInternationalNumber() {
        assertEquals("+31644016988", PhoneUtil.extractFirst("Call Gauh, number: +31644016988"))
    }

    @Test
    fun extractsNationalNumber() {
        assertEquals("4079017892", PhoneUtil.extractFirst("ring me at 407-901-7892 tonight"))
    }

    @Test
    fun extractsParenthesizedNumber() {
        assertEquals("4079017892", PhoneUtil.extractFirst("call (407) 901-7892"))
    }

    @Test
    fun ignoresIsoDatesAndTimes() {
        assertNull(PhoneUtil.extractFirst("meeting on 2026-06-17 at 16:00"))
    }

    @Test
    fun ignoresShortCodes() {
        assertNull(PhoneUtil.extractFirst("your verification code is 481920"))
    }

    @Test
    fun prefersRealNumberOverADateInTheSameText() {
        assertEquals("+31644016988", PhoneUtil.extractFirst("on 2026-06-17 call +31644016988"))
    }

    @Test
    fun detectsCallIntent() {
        assertTrue(PhoneUtil.isCallIntent("Call Gauh", null, "Call Gauh, number: +31644016988"))
        assertTrue(PhoneUtil.isCallIntent("Reminder", "ring the dentist to confirm"))
    }

    @Test
    fun rejectsNonCallIntent() {
        assertFalse(PhoneUtil.isCallIntent("Meet at Panda Express", "Lets meet at 4pm"))
        // "recalled" must not count as a call intent.
        assertFalse(PhoneUtil.isCallIntent("Recalled product notice"))
    }

    @Test
    fun personNameFromNotificationSender() {
        // A WhatsApp "call me" — the number is never in the text, only the sender's name.
        assertEquals("John", PhoneUtil.personName("Notification from John", "wants you to call"))
        // Prefix match is case-insensitive.
        assertEquals("Mom", PhoneUtil.personName("notification from Mom", null))
    }

    @Test
    fun personNameFromCallIntentTitle() {
        assertEquals("Sarah", PhoneUtil.personName(null, "Call Sarah"))
        assertEquals("the dentist", PhoneUtil.personName(null, "Ring the dentist"))
        assertEquals("Dad", PhoneUtil.personName("SMS from +123456", "Call back Dad"))
    }

    @Test
    fun personNamePrefersNotificationSenderOverTitle() {
        assertEquals("John", PhoneUtil.personName("Notification from John", "Call Sarah"))
    }

    @Test
    fun personNameNullWhenNoNameToLookUp() {
        // No call verb in the title, and the source isn't a named notification.
        assertNull(PhoneUtil.personName("SMS from +123456", "Meeting at 4pm"))
        // A number after the verb is dialable directly — not a contact to look up.
        assertNull(PhoneUtil.personName(null, "Call +31644016988"))
        // A plain SMS sender (a number) is not a contact name.
        assertNull(PhoneUtil.personName("SMS from 4079017892", null))
    }
}
