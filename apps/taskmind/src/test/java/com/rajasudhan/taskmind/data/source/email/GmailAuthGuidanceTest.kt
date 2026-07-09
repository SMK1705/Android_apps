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
        // The failing account is not among the device's Google accounts -> the real fix is re-adding it.
        val msg = GmailAuth.authGuidance("ERROR", "me@gmail.com", listOf("other@gmail.com"))
        assertTrue(msg.contains("Settings", ignoreCase = true) && msg.contains("Accounts", ignoreCase = true))
    }

    @Test
    fun authGuidance_emptyDeviceList_doesNotFalselyClaimRemoved_usesStatus() {
        // If we couldn't read the account list, don't claim it was removed — fall back to status guidance.
        val msg = GmailAuth.authGuidance("NetworkError", "me@gmail.com", emptyList())
        assertTrue(msg.contains("connection", ignoreCase = true))
    }

    @Test
    fun authGuidance_needPermission_tellsUserToReconnect() {
        val msg = GmailAuth.authGuidance("NeedPermission", "me@gmail.com", listOf("me@gmail.com"))
        assertTrue(msg.contains("reconnect", ignoreCase = true))
    }

    @Test
    fun authGuidance_badAuthentication_tellsUserToReAddTheAccount() {
        val msg = GmailAuth.authGuidance("BadAuthentication", "me@gmail.com", listOf("me@gmail.com"))
        assertTrue(msg.contains("Accounts", ignoreCase = true))
    }

    @Test
    fun authGuidance_genericError_whenAccountIsOnDevice_isStillActionable() {
        val msg = GmailAuth.authGuidance("ERROR", "me@gmail.com", listOf("me@gmail.com"))
        assertTrue(msg.contains("Reconnect", ignoreCase = true))
    }

    @Test
    fun mask_hidesTheLocalPart_keepsDomain() {
        assertEquals("s***@gmail.com", GmailAuth.mask("someone@gmail.com"))
        assertEquals("(none)", GmailAuth.mask(""))
        assertEquals("***", GmailAuth.mask("notanemail"))
    }
}
