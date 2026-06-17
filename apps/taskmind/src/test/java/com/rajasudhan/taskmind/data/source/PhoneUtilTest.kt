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
}
