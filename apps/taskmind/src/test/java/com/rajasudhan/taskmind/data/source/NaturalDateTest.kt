package com.rajasudhan.taskmind.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The deterministic NL date/time/recurrence parser (#116). Pure, so a plain JVM test with a fixed now. */
class NaturalDateTest {

    // Tuesday, 9 June 2026, noon.
    private val now = LocalDateTime.of(2026, 6, 9, 12, 0)
    private val today: LocalDate = now.toLocalDate()

    private fun parse(text: String) = NaturalDate.parse(text, now)

    // ---- relative dates ----

    @Test fun today_isToday() = assertEquals(today, parse("do it today").date)
    @Test fun tomorrow_isPlusOne() = assertEquals(today.plusDays(1), parse("call mom tomorrow").date)
    @Test fun inNDays() = assertEquals(today.plusDays(3), parse("submit in 3 days").date)
    @Test fun inNWeeks() = assertEquals(today.plusWeeks(2), parse("review in 2 weeks").date)
    @Test fun inNMonths() = assertEquals(today.plusMonths(1), parse("renew in 1 month").date)
    @Test fun nextWeek() = assertEquals(today.plusWeeks(1), parse("ship next week").date)
    @Test fun nextMonth() = assertEquals(today.plusMonths(1), parse("rent next month").date)

    @Test fun inNHours_pinsDateAndTime() {
        val r = parse("call back in 2 hours")
        assertEquals(now.plusHours(2).toLocalDate(), r.date)
        assertEquals(LocalTime.of(14, 0), r.time)
    }

    // ---- weekdays: assert the resolved day-of-week + that it lands in the right week ----

    @Test fun bareWeekday_isTheUpcomingOne() {
        val d = parse("gym friday").date!!
        assertEquals(DayOfWeek.FRIDAY, d.dayOfWeek)
        assertTrue(!d.isBefore(today) && d.isBefore(today.plusDays(7))) // this coming week (today allowed)
    }

    @Test fun nextWeekday_isTheFollowingWeek() {
        val d = parse("meeting next friday").date!!
        assertEquals(DayOfWeek.FRIDAY, d.dayOfWeek)
        assertTrue(d.isAfter(today.plusDays(6))) // at least a full week out
    }

    // ---- times ----

    @Test fun pm() = assertEquals(LocalTime.of(17, 0), parse("call at 5pm").time)
    @Test fun pmWithMinutes() = assertEquals(LocalTime.of(17, 30), parse("call 5:30pm").time)
    @Test fun am() = assertEquals(LocalTime.of(9, 0), parse("standup 9am").time)
    @Test fun twelvePm_isNoon() = assertEquals(LocalTime.of(12, 0), parse("lunch 12pm").time)
    @Test fun twelveAm_isMidnight() = assertEquals(LocalTime.of(0, 0), parse("cutoff 12am").time)
    @Test fun twentyFourHour() = assertEquals(LocalTime.of(17, 0), parse("depart 17:00").time)
    @Test fun namedNoon() = assertEquals(LocalTime.NOON, parse("meet at noon").time)
    @Test fun namedMorning() = assertEquals(LocalTime.of(9, 0), parse("run in the morning").time)
    @Test fun atN_assumesPmForEarlyHours() = assertEquals(LocalTime.of(17, 0), parse("dinner at 5").time)
    @Test fun atN_keepsMorningHours() = assertEquals(LocalTime.of(9, 0), parse("call at 9").time)

    // ---- recurrence (and the weekday-contains-"day" guard) ----

    @Test fun daily() = assertEquals("daily", parse("stretch every day").recurrence)
    @Test fun weekly() = assertEquals("weekly", parse("team sync every week").recurrence)
    @Test fun monthly() = assertEquals("monthly", parse("pay rent every month").recurrence)
    @Test fun bareDaily() = assertEquals("daily", parse("daily vitamins").recurrence)

    @Test fun everyWeekday_isWeekly_notDaily_andSetsThatDay() {
        // "monday"/"friday"/"sunday" all contain the substring "day" — must NOT resolve to daily.
        val r = parse("standup every friday")
        assertEquals("weekly", r.recurrence)
        assertEquals(DayOfWeek.FRIDAY, r.date!!.dayOfWeek)
    }

    // ---- absolute dates ----

    @Test fun monthDay_thisYear() = assertEquals(LocalDate.of(2026, 6, 12), parse("party june 12").date)
    @Test fun monthDay_rollsToNextYear_whenPast() = assertEquals(LocalDate.of(2027, 1, 5), parse("taxes jan 5").date)
    @Test fun dayMonth() = assertEquals(LocalDate.of(2026, 6, 20), parse("trip 20 june").date)
    @Test fun numeric_withYear() = assertEquals(LocalDate.of(2027, 6, 20), parse("call 6/20/2027").date)

    // Conservative guards: because the parse OVERRIDES the LLM, a fraction/quantity or a "count" must NOT
    // be mistaken for a date/time (the LLM still resolves a bare "6/20" itself).
    @Test fun bareNumericWithoutYear_isNotADate() = assertNull(parse("call 6/20").date)
    @Test fun fraction_isNotMistakenForADate() {
        assertNull(parse("buy 2/3 cup sugar").date)
        assertTrue(parse("buy 2/3 cup sugar").isEmpty)
    }
    @Test fun atN_followedByANoun_isNotATime() {
        assertNull(parse("invite the team, meet at 3 people").time)
        assertNull(parse("stay at 5 star hotel").time)
    }

    // ---- combined + formatting ----

    @Test fun fullPhrase_dateAndTime_withSpans() {
        val r = parse("call mom tomorrow 5pm")
        assertEquals(today.plusDays(1), r.date)
        assertEquals(LocalTime.of(17, 0), r.time)
        assertEquals("17:00", r.dueTime())
        assertEquals(today.plusDays(1).toString(), r.dueDate())
        assertTrue(r.spans.isNotEmpty()) // "tomorrow" and "5pm" ranges, for highlighting
    }

    @Test fun recurringWithDayAndTime() {
        val r = parse("yoga every tuesday 6am")
        assertEquals("weekly", r.recurrence)
        assertEquals(DayOfWeek.TUESDAY, r.date!!.dayOfWeek)
        assertEquals(LocalTime.of(6, 0), r.time)
    }

    // ---- negatives: plain text must not misfire ----

    @Test fun plainText_isEmpty() {
        val r = parse("call mom about the invoice")
        assertTrue(r.isEmpty)
        assertNull(r.date); assertNull(r.time); assertNull(r.recurrence)
    }

    @Test fun quantityWithoutInKeyword_isNotADate() {
        assertFalse(parse("buy 2 apples").date != null) // "2" alone is not "in 2 days"
    }

    // ---- case-insensitivity + span alignment (highlighting) ----

    @Test fun mixedCase_parses() {
        val r = parse("Call Mom TOMORROW 5PM")
        assertEquals(today.plusDays(1), r.date)
        assertEquals(LocalTime.of(17, 0), r.time)
    }

    @Test fun spans_indexTheOriginalText_soHighlightAligns() {
        val text = "call mom tomorrow"
        val r = parse(text)
        // Every span must map onto the literal source substring (spans index the ORIGINAL, not a lowercased copy).
        assertTrue(r.spans.isNotEmpty())
        assertTrue(r.spans.all { it.first >= 0 && it.last < text.length })
        assertTrue(r.spans.any { text.substring(it.first, it.last + 1).equals("tomorrow", ignoreCase = true) })
    }
}
