package com.rajasudhan.taskmind.data.source.email

import com.squareup.moshi.JsonClass

/** Minimal Gmail REST DTOs (only the fields TaskMind reads). */

@JsonClass(generateAdapter = true)
data class GmailMessageList(
    val messages: List<GmailRef> = emptyList(),
    val resultSizeEstimate: Int = 0,
    /** Cursor for the next page of results; null/absent on the last page. */
    val nextPageToken: String? = null
)

@JsonClass(generateAdapter = true)
data class GmailRef(
    val id: String,
    val threadId: String = ""
)

@JsonClass(generateAdapter = true)
data class GmailMessage(
    val id: String = "",
    val threadId: String = "",
    val snippet: String = "",
    val payload: GmailPayload? = null
)

/** A MIME node — recursive: a multipart payload contains child [parts]. */
@JsonClass(generateAdapter = true)
data class GmailPayload(
    val mimeType: String = "",
    val headers: List<GmailHeader> = emptyList(),
    val body: GmailBody? = null,
    val parts: List<GmailPayload> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GmailHeader(
    val name: String = "",
    val value: String = ""
)

@JsonClass(generateAdapter = true)
data class GmailBody(
    val size: Int = 0,
    /** base64url-encoded content. */
    val data: String? = null
)

@JsonClass(generateAdapter = true)
data class GmailProfile(
    val emailAddress: String = ""
)
