package com.rajasudhan.taskmind.data.source.email

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class GmailTextExtractorTest {

    private fun b64url(s: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray(Charsets.UTF_8))

    @Test
    fun headerLookupIsCaseInsensitive() {
        val headers = listOf(GmailHeader("From", "a@b.com"), GmailHeader("Subject", "Hi"))
        assertEquals("a@b.com", GmailTextExtractor.header(headers, "from"))
        assertEquals("Hi", GmailTextExtractor.header(headers, "SUBJECT"))
        assertNull(GmailTextExtractor.header(headers, "Cc"))
    }

    @Test
    fun decodesBase64UrlWithAndWithoutPadding() {
        assertEquals("Hello world", GmailTextExtractor.decodeBody(b64url("Hello world")))
        assertEquals(
            "Lunch?",
            GmailTextExtractor.decodeBody(Base64.getUrlEncoder().encodeToString("Lunch?".toByteArray()))
        )
        assertNull(GmailTextExtractor.decodeBody(null))
        assertNull(GmailTextExtractor.decodeBody(""))
    }

    @Test
    fun decodesBodyUsingThePartsDeclaredCharset_notAlwaysUtf8() {
        // #G2: "Café at 3pm" in ISO-8859-1 — 'é' is a single 0xE9 byte; decoding those bytes as UTF-8
        // mojibakes it. The part's Content-Type charset must drive the decode.
        val latin1 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("Café at 3pm".toByteArray(Charsets.ISO_8859_1))
        val payload = GmailPayload(
            mimeType = "text/plain",
            headers = listOf(GmailHeader("Content-Type", "text/plain; charset=ISO-8859-1")),
            body = GmailBody(data = latin1)
        )
        assertEquals("Café at 3pm", GmailTextExtractor.extractBodyText(payload))
    }

    @Test
    fun decodeBody_defaultsToUtf8_andHonoursAnExplicitCharset() {
        val eLatin1 = Base64.getUrlEncoder().withoutPadding().encodeToString("é".toByteArray(Charsets.ISO_8859_1))
        // Default UTF-8 can't decode the lone 0xE9 byte -> replacement char...
        assertEquals("�", GmailTextExtractor.decodeBody(eLatin1))
        // ...but the right charset recovers it.
        assertEquals("é", GmailTextExtractor.decodeBody(eLatin1, Charsets.ISO_8859_1))
    }

    @Test
    fun charsetFor_readsContentType_andFallsBackToUtf8() {
        assertEquals(Charsets.ISO_8859_1, GmailTextExtractor.charsetFor(listOf(GmailHeader("Content-Type", "text/plain; charset=iso-8859-1"))))
        assertEquals(Charsets.UTF_8, GmailTextExtractor.charsetFor(listOf(GmailHeader("Content-Type", "text/plain"))))
        assertEquals(Charsets.UTF_8, GmailTextExtractor.charsetFor(emptyList()))
        assertEquals(Charsets.UTF_8, GmailTextExtractor.charsetFor(listOf(GmailHeader("Content-Type", "text/plain; charset=totally-bogus"))))
    }

    @Test
    fun extractsPlainTextDirectly() {
        val payload = GmailPayload(
            mimeType = "text/plain",
            body = GmailBody(data = b64url("Meeting at 3pm tomorrow"))
        )
        assertEquals("Meeting at 3pm tomorrow", GmailTextExtractor.extractBodyText(payload))
    }

    @Test
    fun prefersPlainTextInMultipart() {
        val payload = GmailPayload(
            mimeType = "multipart/alternative",
            parts = listOf(
                GmailPayload(mimeType = "text/html", body = GmailBody(data = b64url("<p>ignore me</p>"))),
                GmailPayload(mimeType = "text/plain", body = GmailBody(data = b64url("plain wins")))
            )
        )
        assertEquals("plain wins", GmailTextExtractor.extractBodyText(payload))
    }

    @Test
    fun fallsBackToHtmlStripped() {
        val payload = GmailPayload(
            mimeType = "text/html",
            body = GmailBody(data = b64url("<p>Call <b>Sam</b> at 5</p>"))
        )
        assertEquals("Call Sam at 5", GmailTextExtractor.extractBodyText(payload))
    }

    @Test
    fun returnsNullWhenNoText() {
        assertNull(GmailTextExtractor.extractBodyText(null))
        assertNull(
            GmailTextExtractor.extractBodyText(
                GmailPayload(mimeType = "image/png", body = GmailBody(data = null))
            )
        )
    }

    @Test
    fun stripHtmlCollapsesAndUnescapes() {
        val out = GmailTextExtractor.stripHtml("<div>Hi&nbsp;&amp;&nbsp;bye<br>line2</div>")
        assertEquals("Hi & bye\nline2", out)
    }

    @Test
    fun parsesCalendarInviteFromIcsPart() {
        // TZID (not Z) time so the assertion doesn't depend on the test machine's timezone.
        val ics = """
            BEGIN:VCALENDAR
            METHOD:REQUEST
            BEGIN:VEVENT
            SUMMARY:Project sync
            DTSTART;TZID=America/New_York:20260625T150000
            LOCATION:Google Meet
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val payload = GmailPayload(
            mimeType = "multipart/mixed",
            parts = listOf(
                GmailPayload(mimeType = "text/plain", body = GmailBody(data = b64url("You have an invitation."))),
                GmailPayload(mimeType = "text/calendar", body = GmailBody(data = b64url(ics)))
            )
        )
        assertEquals(
            "Calendar invitation. Title: Project sync. When: 2026-06-25 15:00. Where: Google Meet.",
            GmailTextExtractor.extractCalendarText(payload)
        )
    }

    @Test
    fun calendarTextIsNullWithoutAnInvite() {
        assertNull(
            GmailTextExtractor.extractCalendarText(
                GmailPayload(mimeType = "text/plain", body = GmailBody(data = b64url("just a normal email")))
            )
        )
    }
}
