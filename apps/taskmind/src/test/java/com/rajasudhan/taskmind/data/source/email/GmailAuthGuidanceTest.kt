package com.rajasudhan.taskmind.data.source.email

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure auth-error → user-guidance mapping and log masking behind GmailAuth: turn GoogleAuthUtil's
 * opaque status ("ERROR" / "BadAuthentication" / …) into an actionable message. No Play Services needed.
 */
class GmailAuthGuidanceTest {

    @Test
    fun authGuidance_accountRemovedFromDevice_tellsUserToReAddIt() {
        // Not among the device's Google accounts -> the real fix is re-adding it (regardless of the probe).
        val msg = GmailAuth.authGuidance("ERROR", "me@gmail.com", listOf("other@gmail.com"), basicScopeOk = false)
        assertTrue(msg.contains("Settings", ignoreCase = true) && msg.contains("Accounts", ignoreCase = true))
    }

    @Test
    fun authGuidance_basicScopeWorks_pointsAtTheAccountOrAppAuthorization_notDeviceState() {
        // The account signs in (basic scope OK) but the restricted Gmail scope is blocked for it specifically.
        val msg = GmailAuth.authGuidance("ERROR", "me@gmail.com", listOf("me@gmail.com"), basicScopeOk = true)
        assertTrue(msg.contains("Gmail access is blocked", ignoreCase = true))
        assertTrue(msg.contains("Advanced Protection", ignoreCase = true) || msg.contains("test user", ignoreCase = true))
    }

    @Test
    fun authGuidance_basicScopeAlsoFails_pointsAtStuckDeviceAccountState() {
        val msg = GmailAuth.authGuidance("ERROR", "me@gmail.com", listOf("me@gmail.com"), basicScopeOk = false)
        assertTrue(msg.contains("stuck", ignoreCase = true))
        assertTrue(msg.contains("Play Services", ignoreCase = true) || msg.contains("Accounts", ignoreCase = true))
    }

    @Test
    fun authGuidance_networkError_whenBasicAlsoFails_tellsUserToCheckConnection() {
        val msg = GmailAuth.authGuidance("NetworkError", "me@gmail.com", emptyList(), basicScopeOk = false)
        assertTrue(msg.contains("connection", ignoreCase = true))
    }

    @Test
    fun mask_hidesTheLocalPart_keepsDomain() {
        assertEquals("s***@gmail.com", GmailAuth.mask("someone@gmail.com"))
        assertEquals("(none)", GmailAuth.mask(""))
        assertEquals("***", GmailAuth.mask("notanemail"))
    }
}
