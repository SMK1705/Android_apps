package com.rajasudhan.taskmind.data.source

import com.rajasudhan.taskmind.data.source.RecurrencePattern.Occurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Pure tests for the auto-recurrence pattern miner (#124 Part B). */
class RecurrencePatternTest {

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun detectsMonthly_fromThreeMonthStarts_clusteringNearTitles() {
        val history = listOf(
            Occurrence("Pay rent", d("2026-04-01")),
            Occurrence("Pay the rent", d("2026-05-01")), // "the" is a stopword → same cluster
            Occurrence("Pay rent", d("2026-06-01")),
        )

        val detected = RecurrencePattern.detect(history, d("2026-07-01"))

        assertEquals(1, detected.size)
        val p = detected[0]
        assertEquals("monthly", p.recurrence)
        assertEquals(3, p.occurrences)
        assertEquals(1, p.anchorDay)               // day-of-month
        assertEquals(d("2026-07-01"), p.nextDate)  // first occurrence on/after today
        assertTrue(p.confidence in 0.6..0.85)
    }

    @Test
    fun detectsWeekly() {
        val history = (0..3).map { Occurrence("Team sync", d("2026-06-01").plusWeeks(it.toLong())) }

        val detected = RecurrencePattern.detect(history, d("2026-06-25"))

        assertEquals(1, detected.size)
        assertEquals("weekly", detected[0].recurrence)
        assertEquals(d("2026-06-29"), detected[0].nextDate) // last was Jun 22 → +7
    }

    @Test
    fun ignoresTooFewOccurrences() {
        val history = listOf(
            Occurrence("Pay rent", d("2026-05-01")),
            Occurrence("Pay rent", d("2026-06-01")),
        )
        assertTrue(RecurrencePattern.detect(history, d("2026-07-01")).isEmpty())
    }

    @Test
    fun ignoresInconsistentGaps() {
        // One weekly gap, one monthly gap — no single steady cadence.
        val history = listOf(
            Occurrence("Thing", d("2026-06-01")),
            Occurrence("Thing", d("2026-06-08")),
            Occurrence("Thing", d("2026-07-08")),
        )
        assertTrue(RecurrencePattern.detect(history, d("2026-07-20")).isEmpty())
    }

    @Test
    fun doesNotDetectDaily() {
        // Daily is intentionally excluded — 1-day gaps are too noisy.
        val history = (0..4).map { Occurrence("Standup", d("2026-06-01").plusDays(it.toLong())) }
        assertTrue(RecurrencePattern.detect(history, d("2026-06-10")).isEmpty())
    }

    @Test
    fun collapsesSameDayReCaptures() {
        // A duplicate same-day capture mustn't read as a 0-day gap; distinct dates still make a monthly.
        val history = listOf(
            Occurrence("Pay rent", d("2026-04-01")),
            Occurrence("Pay rent", d("2026-04-01")),
            Occurrence("Pay rent", d("2026-05-01")),
            Occurrence("Pay rent", d("2026-06-01")),
        )
        val detected = RecurrencePattern.detect(history, d("2026-07-01"))
        assertEquals(1, detected.size)
        assertEquals(3, detected[0].occurrences) // deduped to 3 distinct days
    }

    @Test
    fun distinctTasksAreNotMerged() {
        // "Call Alice" vs "Call Bob" share only "call" → below the overlap threshold → separate clusters,
        // neither with enough sightings.
        val history = listOf(
            Occurrence("Call Alice", d("2026-04-01")),
            Occurrence("Call Bob", d("2026-05-01")),
            Occurrence("Call Alice", d("2026-06-01")),
        )
        assertTrue(RecurrencePattern.detect(history, d("2026-07-01")).isEmpty())
    }

    @Test
    fun key_normalisesToSortedContentTokens() {
        assertEquals("pay rent", RecurrencePattern.key("Pay the Rent!"))
        assertEquals(RecurrencePattern.key("rent pay"), RecurrencePattern.key("Pay rent"))
    }
}
