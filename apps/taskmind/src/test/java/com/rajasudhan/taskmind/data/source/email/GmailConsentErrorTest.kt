package com.rajasudhan.taskmind.data.source.email

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The consent-failure copy surfaces the real Google status code + an actionable hint, instead of the
 * old blanket "Gmail connection cancelled" that hid genuine OAuth-config errors.
 */
class GmailConsentErrorTest {

    @Test
    fun developerError10_pointsAtTheSigningSha1() {
        val msg = gmailConsentErrorMessage(10)
        assertTrue(msg.contains("error 10"))
        assertTrue(msg.contains("SHA-1"))
    }

    @Test
    fun internalError8_pointsAtTheOAuthProjectSetup() {
        val msg = gmailConsentErrorMessage(8)
        assertTrue(msg.contains("error 8"))
        assertTrue(msg.contains("Gmail API") || msg.contains("OAuth"))
    }

    @Test
    fun noTokenSentinel_asksToRetry() {
        assertTrue(gmailConsentErrorMessage(GMAIL_STATUS_NO_TOKEN).contains("try again", ignoreCase = true))
    }

    @Test
    fun anyOtherCode_showsTheRawCode() {
        assertTrue(gmailConsentErrorMessage(13).contains("error 13"))
        assertTrue(gmailConsentErrorMessage(7).contains("error 7"))
    }
}
