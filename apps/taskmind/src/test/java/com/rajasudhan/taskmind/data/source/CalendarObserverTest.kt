package com.rajasudhan.taskmind.data.source

import com.rajasudhan.taskmind.data.source.CalendarObserver.CalendarEventState
import com.rajasudhan.taskmind.data.source.CalendarObserver.Reconciled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The pure back-sync reconcile core (#205): given a note's fields and a mirrored event's current state,
 * what (if anything) to write back onto the note. A fixed zone keeps the timed conversions deterministic
 * regardless of the CI machine's zone. The observer/provider plumbing is device-verified separately.
 */
class CalendarObserverTest {

    private val zone = ZoneId.of("America/New_York")

    private fun timed(y: Int, mo: Int, d: Int, h: Int, mi: Int, title: String? = "Meeting"): CalendarEventState {
        val ms = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()
        return CalendarEventState(ms, allDay = false, title = title)
    }

    private fun allDay(y: Int, mo: Int, d: Int, title: String? = "Meeting"): CalendarEventState {
        // All-day events store DTSTART at UTC midnight — same convention CalendarMirror writes.
        val ms = LocalDate.of(y, mo, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return CalendarEventState(ms, allDay = true, title = title)
    }

    @Test
    fun timedEventMoved_reschedulesTheNote() {
        val r = CalendarObserver.reconcile("Meeting", "2026-07-07", "15:00", timed(2026, 7, 8, 16, 30), zone)
        assertEquals(Reconciled("2026-07-08", "16:30", "Meeting"), r)
    }

    @Test
    fun unchangedEvent_isIdempotentNull() {
        // Event matches the note exactly (e.g. our own mirror write) -> nothing to do, so no feedback loop.
        assertNull(CalendarObserver.reconcile("Meeting", "2026-07-07", "15:00", timed(2026, 7, 7, 15, 0), zone))
    }

    @Test
    fun nonPaddedNoteTime_isIdempotent_noSpuriousWrite() {
        // The forward mirror wrote the event from a note stored non-canonically as "9:00"; reading it back
        // yields "09:00". That is the same clock time, so reconcile must report NO change (#205 review).
        assertNull(CalendarObserver.reconcile("Meeting", "2026-07-07", "9:00", timed(2026, 7, 7, 9, 0), zone))
    }

    @Test
    fun whitespacePaddedNoteTitle_isIdempotent() {
        // Note title has trailing whitespace; the event title is the trimmed form — not a rename.
        assertNull(CalendarObserver.reconcile("Meeting ", "2026-07-07", "15:00", timed(2026, 7, 7, 15, 0, "Meeting"), zone))
    }

    @Test
    fun renamedEvent_syncsTitleBack() {
        val r = CalendarObserver.reconcile("Meeting", "2026-07-07", "15:00", timed(2026, 7, 7, 15, 0, "Team sync"), zone)
        assertEquals(Reconciled("2026-07-07", "15:00", "Team sync"), r)
    }

    @Test
    fun blankEventTitle_keepsNoteTitle() {
        val r = CalendarObserver.reconcile("Meeting", "2026-07-07", "15:00", timed(2026, 7, 8, 9, 0, "  "), zone)
        assertEquals("Meeting", r?.title)
        assertEquals("2026-07-08", r?.dueDate)
    }

    @Test
    fun nullEventTitle_keepsNoteTitle() {
        val r = CalendarObserver.reconcile("Meeting", "2026-07-07", "15:00", timed(2026, 7, 8, 9, 0, null), zone)
        assertEquals("Meeting", r?.title)
    }

    @Test
    fun allDayEvent_hasNoTime() {
        val r = CalendarObserver.reconcile("Meeting", "2026-07-07", "15:00", allDay(2026, 7, 10), zone)
        assertEquals(Reconciled("2026-07-10", null, "Meeting"), r)
    }

    @Test
    fun timedNoteBecameAllDay_clearsTheTime() {
        val r = CalendarObserver.reconcile("Meeting", "2026-07-07", "15:00", allDay(2026, 7, 7), zone)
        assertEquals(Reconciled("2026-07-07", null, "Meeting"), r)
    }

    @Test
    fun singleDigitHour_padsToTwoDigit24h() {
        val r = CalendarObserver.reconcile("Meeting", "2026-07-07", "15:00", timed(2026, 7, 7, 9, 5), zone)
        assertEquals("09:05", r?.dueTime)
    }

    @Test
    fun eventDateTime_allDayReadsInUtc_notLocalZone() {
        // UTC midnight of the 10th must resolve to the 10th, not the 9th, even in a negative-offset zone.
        assertEquals("2026-07-10" to null, CalendarObserver.eventDateTime(allDay(2026, 7, 10), zone))
    }

    @Test
    fun eventDateTime_timedReadsInLocalZone() {
        assertEquals("2026-07-08" to "16:30", CalendarObserver.eventDateTime(timed(2026, 7, 8, 16, 30), zone))
    }
}
