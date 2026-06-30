package com.rajasudhan.taskmind.ui.sources

import android.content.Context
import com.rajasudhan.taskmind.data.source.SourceManager
import com.rajasudhan.taskmind.data.source.email.GmailAuth
import com.rajasudhan.taskmind.data.source.email.GmailCollector
import com.rajasudhan.taskmind.testutil.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Light coverage: the source toggles and the email-off path delegate to SourceManager / GmailAuth. */
@OptIn(ExperimentalCoroutinesApi::class)
class SourcesViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val sourceManager = mockk<SourceManager>(relaxed = true)
    private val gmailAuth = mockk<GmailAuth>(relaxed = true)
    private val gmailCollector = mockk<GmailCollector>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private fun vm(): SourcesViewModel {
        every { gmailAuth.connectedAccounts } returns emptySet()
        return SourcesViewModel(sourceManager, gmailAuth, gmailCollector, context)
    }

    @Test
    fun toggleSms_delegatesToSourceManager() = runTest {
        vm().toggleSms(true)
        coVerify { sourceManager.setSourceEnabled(SourceManager.KEY_SMS_ENABLED, true) }
    }

    @Test
    fun toggleCalendar_delegatesToSourceManager() = runTest {
        vm().toggleCalendar(true)
        coVerify { sourceManager.setSourceEnabled(SourceManager.KEY_CALENDAR_ENABLED, true) }
    }

    @Test
    fun emailToggleOff_disconnectsAllAccountsAndDisablesSource() = runTest {
        vm().onEmailToggle(false)
        coVerify { gmailAuth.disconnectAll() }
        coVerify { sourceManager.setSourceEnabled(SourceManager.KEY_EMAIL_ENABLED, false) }
    }
}
