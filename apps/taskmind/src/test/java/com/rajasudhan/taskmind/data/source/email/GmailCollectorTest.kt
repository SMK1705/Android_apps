package com.rajasudhan.taskmind.data.source.email

import com.rajasudhan.taskmind.data.source.EgressLogger
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.util.Base64

/**
 * Gmail fetch behaviour: pagination (collect every message across pages, not just the first) and the query,
 * which scans **read or unread** Primary mail in the window so an email the user opens before the next scan
 * isn't dropped — dedup is the caller's processed-id ledger, not the unread flag.
 */
class GmailCollectorTest {

    private val api = mockk<GmailApi>()
    private val egress = mockk<EgressLogger>(relaxed = true)
    private val collector = GmailCollector(api, egress)

    private fun ref(id: String) = GmailRef(id = id)
    private fun msg(id: String) = GmailMessage(
        id = id,
        payload = GmailPayload(
            mimeType = "text/plain",
            headers = listOf(GmailHeader("From", "$id@example.com"), GmailHeader("Subject", "S-$id")),
            body = GmailBody(data = Base64.getUrlEncoder().withoutPadding().encodeToString("body $id".toByteArray()))
        )
    )

    @Test
    fun query_scansReadAndUnreadPrimaryMail_withinTheAfterWindow() = runTest {
        val query = slot<String>()
        coEvery { api.listMessages(any(), capture(query), any(), null) } returns
            GmailMessageList(messages = emptyList(), nextPageToken = null)

        collector.fetchRecentPrimary("tok", sinceMillis = 60_000L, skipIds = emptySet())

        // No `is:unread`: an email the user opens before the scan runs must still be caught (dedup is the
        // processed-id ledger, not the read flag). 60_000 ms -> after:60 (epoch seconds).
        assertFalse("query must not restrict to unread", query.captured.contains("is:unread"))
        assertEquals("category:primary after:60", query.captured)
    }

    @Test
    fun followsNextPageToken_collectingEveryEmail_notJustTheFirstPage() = runTest {
        val page1 = GmailMessageList(messages = (1..20).map { ref("m$it") }, nextPageToken = "PAGE2")
        val page2 = GmailMessageList(messages = (21..25).map { ref("m$it") }, nextPageToken = null)
        coEvery { api.listMessages(any(), any(), any(), null) } returns page1
        coEvery { api.listMessages(any(), any(), any(), "PAGE2") } returns page2
        (1..25).forEach { coEvery { api.getMessage(any(), "m$it", any()) } returns msg("m$it") }

        val emails = collector.fetchRecentPrimary("tok", sinceMillis = 0L, skipIds = emptySet(), maxResults = 20)

        assertEquals(25, emails.size) // both pages, not just the first 20
        assertEquals((1..25).map { "m$it" }, emails.map { it.id })
    }

    @Test
    fun stopsAtTheMessageCap_withoutFetchingFurtherPages() = runTest {
        val page1 = GmailMessageList(messages = (1..20).map { ref("m$it") }, nextPageToken = "PAGE2")
        coEvery { api.listMessages(any(), any(), any(), null) } returns page1
        (1..20).forEach { coEvery { api.getMessage(any(), "m$it", any()) } returns msg("m$it") }

        val emails = collector.fetchRecentPrimary("tok", 0L, emptySet(), maxResults = 20, maxMessages = 10)

        assertEquals(10, emails.size) // capped; page 2 never requested
    }

    @Test
    fun skipsAlreadyProcessedIds_butStillFollowsPagesForNewOnes() = runTest {
        val page1 = GmailMessageList(messages = listOf(ref("a"), ref("b")), nextPageToken = "P2")
        val page2 = GmailMessageList(messages = listOf(ref("c")), nextPageToken = null)
        coEvery { api.listMessages(any(), any(), any(), null) } returns page1
        coEvery { api.listMessages(any(), any(), any(), "P2") } returns page2
        coEvery { api.getMessage(any(), "a", any()) } returns msg("a")
        coEvery { api.getMessage(any(), "c", any()) } returns msg("c")

        val emails = collector.fetchRecentPrimary("tok", 0L, skipIds = setOf("b"), maxResults = 20)

        assertEquals(listOf("a", "c"), emails.map { it.id }) // b skipped; c on page 2 still collected
    }

    @Test
    fun listFailure_onTheFirstPage_yieldsEmpty_notACrash() = runTest {
        coEvery { api.listMessages(any(), any(), any(), null) } throws RuntimeException("boom")

        val emails = collector.fetchRecentPrimary("tok", 0L, emptySet())

        assertEquals(emptyList<GmailCollector.Email>(), emails)
    }

    @Test
    fun a401_isSurfacedAsUnauthorized_soTheScanCanRefreshTheToken() = runTest {
        // A real HTTP 401 (stale/revoked token) must NOT be swallowed to empty like other errors — it's
        // re-thrown as Unauthorized so RecentDataScanner can invalidate the token and retry (#G1).
        coEvery { api.listMessages(any(), any(), any(), any()) } throws
            HttpException(Response.error<GmailMessageList>(401, "unauthorized".toResponseBody(null)))

        try {
            collector.fetchRecentPrimary("stale-token", 0L, emptySet())
            fail("expected GmailCollector.Unauthorized")
        } catch (e: GmailCollector.Unauthorized) {
            // expected
        }
    }
}
