package com.rajasudhan.taskmind.data.source.email

import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.UserRecoverableAuthException
import com.rajasudhan.taskmind.data.source.email.GmailAuth.BasicScopeProbe
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The pure auth-error → user-guidance mapping, probe classification, and log masking behind GmailAuth: turn
 * GoogleAuthUtil's opaque status ("ERROR" / "ServiceDisabled" / …) plus a basic-scope probe into an
 * actionable message. No Play Services needed.
 */
class GmailAuthGuidanceTest {

    // --- authGuidance: probe category + status → user message ---

    @Test
    fun authGuidance_accountRemovedFromDevice_tellsUserToReAddIt() {
        // Not among the device's Google accounts -> the real fix is re-adding it (regardless of the probe).
        val msg = GmailAuth.authGuidance("ERROR", "me@gmail.com", listOf("other@gmail.com"), BasicScopeProbe.BROKEN)
        assertTrue(msg.contains("Settings", ignoreCase = true) && msg.contains("Accounts", ignoreCase = true))
    }

    @Test
    fun authGuidance_basicScopeWorks_pointsAtTheAccountOrAppAuthorization_notDeviceState() {
        // The account signs in (basic scope OK) but the restricted Gmail scope is blocked for it specifically.
        val msg = GmailAuth.authGuidance("ERROR", "me@gmail.com", listOf("me@gmail.com"), BasicScopeProbe.OK)
        assertTrue(msg.contains("Gmail access is blocked", ignoreCase = true))
        assertTrue(msg.contains("Advanced Protection", ignoreCase = true) || msg.contains("test user", ignoreCase = true))
    }

    @Test
    fun authGuidance_basicScopeNeedsConsent_isTreatedLikeItWorks_notAStuckAccount() {
        // The real k***@gmail.com case: the basic-scope probe came back NeedRemoteConsent (RECOVERABLE), which
        // means the account is healthy and only the restricted Gmail scope is blocked — NOT a stuck sign-in.
        // This is the regression this change fixes (it previously fell through to the "stuck" message).
        val msg = GmailAuth.authGuidance("ERROR", "me@gmail.com", listOf("me@gmail.com"), BasicScopeProbe.RECOVERABLE)
        assertTrue(msg.contains("Gmail access is blocked", ignoreCase = true))
        assertTrue(msg.contains("Advanced Protection", ignoreCase = true))
        assertFalse(msg.contains("stuck", ignoreCase = true))
    }

    @Test
    fun authGuidance_basicScopeAlsoBroken_pointsAtStuckDeviceAccountState() {
        val msg = GmailAuth.authGuidance("ERROR", "me@gmail.com", listOf("me@gmail.com"), BasicScopeProbe.BROKEN)
        assertTrue(msg.contains("stuck", ignoreCase = true))
        assertTrue(msg.contains("Play Services", ignoreCase = true) || msg.contains("Accounts", ignoreCase = true))
    }

    @Test
    fun authGuidance_networkError_fromMainStatus_tellsUserToCheckConnection() {
        val msg = GmailAuth.authGuidance("NetworkError", "me@gmail.com", emptyList(), BasicScopeProbe.BROKEN)
        assertTrue(msg.contains("connection", ignoreCase = true))
    }

    @Test
    fun authGuidance_probeUnreachable_tellsUserToCheckConnection() {
        // Even if the main status is opaque, an unreachable probe means connectivity, not a stuck account.
        val msg = GmailAuth.authGuidance("ERROR", "me@gmail.com", listOf("me@gmail.com"), BasicScopeProbe.UNREACHABLE)
        assertTrue(msg.contains("connection", ignoreCase = true))
    }

    // --- classifyProbe: exception type → probe category (the fix's core) ---

    @Test
    fun classifyProbe_needRemoteConsent_isRecoverable_notBroken() {
        // A UserRecoverableAuthException (NeedRemoteConsent / NeedPermission) = the account can grant, it just
        // hasn't yet. That is NOT a broken sign-in.
        assertEquals(
            BasicScopeProbe.RECOVERABLE,
            GmailAuth.classifyProbe(mockk<UserRecoverableAuthException>(relaxed = true))
        )
    }

    @Test
    fun classifyProbe_hardAuthFailure_isBroken() {
        val e = mockk<GoogleAuthException>(relaxed = true)
        every { e.message } returns "BadAuthentication"
        assertEquals(BasicScopeProbe.BROKEN, GmailAuth.classifyProbe(e))
    }

    @Test
    fun classifyProbe_networkStatus_isUnreachable() {
        val e = mockk<GoogleAuthException>(relaxed = true)
        every { e.message } returns "NetworkError"
        assertEquals(BasicScopeProbe.UNREACHABLE, GmailAuth.classifyProbe(e))
    }

    @Test
    fun classifyProbe_ioException_isUnreachable() {
        assertEquals(BasicScopeProbe.UNREACHABLE, GmailAuth.classifyProbe(IOException("no network")))
    }

    // --- mask ---

    @Test
    fun mask_hidesTheLocalPart_keepsDomain() {
        assertEquals("s***@gmail.com", GmailAuth.mask("someone@gmail.com"))
        assertEquals("(none)", GmailAuth.mask(""))
        assertEquals("***", GmailAuth.mask("notanemail"))
    }
}
