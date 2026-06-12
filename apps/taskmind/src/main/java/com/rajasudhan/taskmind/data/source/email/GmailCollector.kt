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

    suspend fun fetchUnreadPrimary(
        accessToken: String,
        sinceMillis: Long,
        skipIds: Set<String>,
        maxResults: Int = 20
    ): List<Email> = withContext(Dispatchers.IO) {
        val bearer = "Bearer $accessToken"
        val afterSeconds = sinceMillis / 1000
        val query = "is:unread category:primary after:$afterSeconds"

        egressLogger.record("gmail.googleapis.com", "Gmail fetch (unread primary)")
        val list = runCatching { api.listMessages(bearer, query, maxResults) }.getOrNull()
            ?: return@withContext emptyList()

        val emails = mutableListOf<Email>()
        for (ref in list.messages) {
            if (ref.id in skipIds) continue
            val msg = runCatching { api.getMessage(bearer, ref.id, "full") }.getOrNull() ?: continue
            val headers = msg.payload?.headers ?: emptyList()
            val sender = GmailTextExtractor.header(headers, "From") ?: "unknown sender"
            val subject = GmailTextExtractor.header(headers, "Subject") ?: "(no subject)"
            val body = GmailTextExtractor.extractBodyText(msg.payload) ?: msg.snippet
            emails.add(Email(ref.id, sender, subject, body))
        }
        emails
    }

    /** The connected account's email address (for display in Settings/Sources). */
    suspend fun profileEmail(accessToken: String): String? = withContext(Dispatchers.IO) {
        egressLogger.record("gmail.googleapis.com", "Gmail profile lookup")
        runCatching { api.getProfile("Bearer $accessToken").emailAddress }.getOrNull()
    }
}
