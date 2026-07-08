package com.rajasudhan.taskmind.data.source

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rajasudhan.taskmind.data.source.email.GmailAuth
import com.rajasudhan.taskmind.data.source.email.GmailCollector
import com.rajasudhan.taskmind.data.source.understanding.UnderstandingPipeline
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Email capture is forward-only from when the source was enabled — like SMS and the other sources.
 * `scanEmail`'s `since` is clamped up to `emailEnabledAt`, so the Gmail query (`after:<since>`) never asks
 * for mail from before you turned email on, even when the scan's own window reaches further back. This
 * guards the pagination change (#263) from ever mining a pre-enable backlog through the LLM.
 */
@RunWith(RobolectricTestRunner::class)
class EmailForwardOnlyScanTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val pipeline = mockk<UnderstandingPipeline>(relaxed = true)
    private val gmailAuth = mockk<GmailAuth>(relaxed = true)
    private val gmailCollector = mockk<GmailCollector>(relaxed = true)
    private val settingsManager = mockk<SettingsManager>(relaxed = true)

    private val enabledAt = 10_000_000L
    private val account = "me@gmail.com"

    /** SourceManager with ONLY email enabled, stamped as turned on at [enabledAt]. */
    private fun emailOnlySources() = mockk<SourceManager>(relaxed = true).also { sm ->
        every { sm.isSmsEnabled } returns flowOf(false)
        every { sm.isCallLogEnabled } returns flowOf(false)
        every { sm.isEmailEnabled } returns flowOf(true)
        every { sm.isAppUsageEnabled } returns flowOf(false)
        every { sm.isAudioEnabled } returns flowOf(false)
        every { sm.isImagesEnabled } returns flowOf(false)
        every { sm.emailEnabledAt } returns flowOf(enabledAt)
        every { sm.processedEmailIds(any()) } returns flowOf(emptySet())
    }

    private fun scanner(sm: SourceManager) = RecentDataScanner(
        context, sm,
        settingsManager = settingsManager,
        pipeline = pipeline,
        gmailAuth = gmailAuth,
        gmailCollector = gmailCollector,
        appUsageCollector = mockk(relaxed = true),
        transcriber = mockk(relaxed = true),
        ocrEngine = mockk(relaxed = true),
        personContextNotifier = mockk(relaxed = true),
    )

    @Test
    fun emailFetch_sinceIsClampedUpToEnabledAt_soPreEnableMailIsNeverRequested() = runTest {
        every { settingsManager.gmailAccounts } returns setOf(account)
        coEvery { gmailAuth.silentAccessToken(account) } returns "token"

        // A scan window that reaches SIX HOURS before email was enabled.
        scanner(emailOnlySources()).scanSince(enabledAt - 6 * 60 * 60 * 1000L)

        // The fetch must start at enabledAt, not the earlier window — Gmail is only ever asked for mail
        // that arrived AFTER email was turned on; nothing older is fetched or run through the LLM.
        coVerify { gmailCollector.fetchUnreadPrimary("token", enabledAt, emptySet()) }
    }

    @Test
    fun emailFetch_usesTheNormalWindow_whenItAlreadyStartsAfterEnabledAt() = runTest {
        every { settingsManager.gmailAccounts } returns setOf(account)
        coEvery { gmailAuth.silentAccessToken(account) } returns "token"
        val since = enabledAt + 60_000L // an incremental window that already starts after enable

        scanner(emailOnlySources()).scanSince(since)

        coVerify { gmailCollector.fetchUnreadPrimary("token", since, emptySet()) }
    }
}
