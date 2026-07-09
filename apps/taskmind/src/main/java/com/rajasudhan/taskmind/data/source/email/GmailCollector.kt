package com.rajasudhan.taskmind.data.source.email

import com.rajasudhan.taskmind.data.source.EgressLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches recent **Primary-category** emails (read *or* unread) via the Gmail REST API and turns each into
 * a small [Email] (sender, subject, plain-text body) for the understanding pipeline. Read mail is included
 * on purpose, so an email the user opens before the next scan runs isn't silently dropped — dedup is the
 * caller's processed-id ledger and the `after:` window keeps the fetch bounded. Email content stays on the
 * device; only the Gmail API request itself is network egress, and it is audited.
 */
@Singleton
class GmailCollector @Inject constructor(
    private val api: GmailApi,
    private val egressLogger: EgressLogger
) {
    data class Email(val id: String, val sender: String, val subject: String, val body: String)

    /** The Gmail access token was rejected with a 401 — the caller should invalidate it, re-authorize, and retry. */
    class Unauthorized : Exception()

    private companion object {
        const val TAG = "GmailCollector"
        // Safety caps so a huge in-window backlog can't fetch/process unboundedly. The scan is
        // already window-clamped (`after:` + the per-source enabled-at stamp), so these are backstops.
        const val MAX_MESSAGES_PER_SCAN = 100
        const val MAX_PAGES = 25
    }

    suspend fun fetchRecentPrimary(
        accessToken: String,
        sinceMillis: Long,
        skipIds: Set<String>,
        maxResults: Int = 20,
        maxMessages: Int = MAX_MESSAGES_PER_SCAN
    ): List<Email> = withContext(Dispatchers.IO) {
        val bearer = "Bearer $accessToken"
        val afterSeconds = sinceMillis / 1000
        // No `is:unread`: read mail is scanned too, so an email the user opens before the next scan isn't
        // dropped. Dedup is the caller's processed-id ledger (skipIds); the `after:` window bounds the fetch.
        val query = "category:primary after:$afterSeconds"

        egressLogger.record("gmail.googleapis.com", "Gmail fetch (recent primary)")
        val emails = mutableListOf<Email>()
        // Follow nextPageToken until the whole window is covered — a single page (maxResults) would
        // permanently drop the oldest emails when more than one page of Primary mail is in the window
        // (they're never added to the processed-id ledger and fall outside the next scan's window).
        var pageToken: String? = null
        var pages = 0
        do {
            val list = apiCall("listMessages") { api.listMessages(bearer, query, maxResults, pageToken) } ?: break
            pages++
            for (ref in list.messages) {
                if (emails.size >= maxMessages) break
                if (ref.id in skipIds) continue
                val msg = apiCall("getMessage ${ref.id}") { api.getMessage(bearer, ref.id, "full") } ?: continue
                val headers = msg.payload?.headers ?: emptyList()
                val sender = GmailTextExtractor.header(headers, "From") ?: "unknown sender"
                val subject = GmailTextExtractor.header(headers, "Subject") ?: "(no subject)"
                val body = GmailTextExtractor.extractBodyText(msg.payload) ?: msg.snippet
                // A calendar invite often ships its real details only in the .ics part; lead with a parsed
                // summary so the model reliably sees the meeting's title/time/place.
                val invite = GmailTextExtractor.extractCalendarText(msg.payload)
                val finalBody = if (invite != null) "$invite\n\n$body" else body
                emails.add(Email(ref.id, sender, subject, finalBody))
            }
            pageToken = list.nextPageToken?.takeIf { it.isNotBlank() }
        } while (pageToken != null && emails.size < maxMessages && pages < MAX_PAGES)

        if (pageToken != null && (emails.size >= maxMessages || pages >= MAX_PAGES)) {
            android.util.Log.w(TAG, "hit a scan cap (messages=${emails.size}, pages=$pages); more mail remains in the window")
        }
        android.util.Log.i(TAG, "Gmail fetch: ${emails.size} email(s) over $pages page(s) for query=$query")
        emails
    }

    /**
     * Runs a Gmail API [call], returning its result, or **null** (logged) on a non-auth failure so one
     * bad call doesn't abort the batch. A **401** (the token was accepted by GoogleAuthUtil but rejected
     * by Gmail — stale/revoked) is re-thrown as [Unauthorized] so the caller can invalidate the token,
     * re-authorize, and retry once instead of the scan silently returning nothing (#G1).
     */
    private suspend fun <T> apiCall(what: String, call: suspend () -> T): T? =
        try {
            call()
        } catch (e: HttpException) {
            if (e.code() == 401) throw Unauthorized()
            android.util.Log.e(TAG, "Gmail $what failed (HTTP ${e.code()})", e)
            null
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Gmail $what failed", e)
            null
        }

    /** The connected account's email address (for display in Settings/Sources). */
    suspend fun profileEmail(accessToken: String): String? = withContext(Dispatchers.IO) {
        egressLogger.record("gmail.googleapis.com", "Gmail profile lookup")
        runCatching { api.getProfile("Bearer $accessToken").emailAddress }.getOrNull()
    }
}
