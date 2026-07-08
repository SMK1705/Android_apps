package com.rajasudhan.taskmind.data.source.email

import com.rajasudhan.taskmind.data.source.EgressLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches recent **unread, Primary-category** emails via the Gmail REST API and turns each into a
 * small [Email] (sender, subject, plain-text body) for the understanding pipeline. Email content
 * stays on the device; only the Gmail API request itself is network egress, and it is audited.
 */
@Singleton
class GmailCollector @Inject constructor(
    private val api: GmailApi,
    private val egressLogger: EgressLogger
) {
    data class Email(val id: String, val sender: String, val subject: String, val body: String)

    private companion object {
        const val TAG = "GmailCollector"
        // Safety caps so a huge unread-in-window backlog can't fetch/process unboundedly. The scan is
        // already window-clamped (`after:` + the per-source enabled-at stamp), so these are backstops.
        const val MAX_MESSAGES_PER_SCAN = 100
        const val MAX_PAGES = 25
    }

    suspend fun fetchUnreadPrimary(
        accessToken: String,
        sinceMillis: Long,
        skipIds: Set<String>,
        maxResults: Int = 20,
        maxMessages: Int = MAX_MESSAGES_PER_SCAN
    ): List<Email> = withContext(Dispatchers.IO) {
        val bearer = "Bearer $accessToken"
        val afterSeconds = sinceMillis / 1000
        val query = "is:unread category:primary after:$afterSeconds"

        egressLogger.record("gmail.googleapis.com", "Gmail fetch (unread primary)")
        val emails = mutableListOf<Email>()
        // Follow nextPageToken until the whole window is covered — a single page (maxResults) would
        // permanently drop the oldest emails when more than one page of unread Primary mail is in the
        // window (they're never added to the processed-id ledger and fall outside the next scan's window).
        var pageToken: String? = null
        var pages = 0
        do {
            val list = runCatching { api.listMessages(bearer, query, maxResults, pageToken) }
                .onFailure { android.util.Log.e(TAG, "listMessages failed for query=$query", it) }
                .getOrNull() ?: break
            pages++
            for (ref in list.messages) {
                if (emails.size >= maxMessages) break
                if (ref.id in skipIds) continue
                val msg = runCatching { api.getMessage(bearer, ref.id, "full") }
                    .onFailure { android.util.Log.e(TAG, "getMessage ${ref.id} failed", it) }
                    .getOrNull() ?: continue
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
            android.util.Log.w(TAG, "hit a scan cap (messages=${emails.size}, pages=$pages); more unread remain in the window")
        }
        android.util.Log.i(TAG, "Gmail fetch: ${emails.size} email(s) over $pages page(s) for query=$query")
        emails
    }

    /** The connected account's email address (for display in Settings/Sources). */
    suspend fun profileEmail(accessToken: String): String? = withContext(Dispatchers.IO) {
        egressLogger.record("gmail.googleapis.com", "Gmail profile lookup")
        runCatching { api.getProfile("Bearer $accessToken").emailAddress }.getOrNull()
    }
}
