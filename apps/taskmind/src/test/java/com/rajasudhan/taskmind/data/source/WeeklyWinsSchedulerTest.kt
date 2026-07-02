package com.rajasudhan.taskmind.data.source

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

/** The day-of-week + hour math for the Sunday recap's first fire. */
class WeeklyWinsSchedulerTest {

    private val oneHourMs = 60 * 60 * 1000L
    private val oneDayMs = 24 * oneHourMs

    // 2026-07-05 is a Sunday.
    @Test
    fun sundayEarlier_schedulesLaterSameDay() {
        val now = LocalDateTime.of(2026, 7, 5, 9, 0) // Sunday 09:00
        // 18:00 the same Sunday is 9h ahead.
        assertEquals(9 * oneHourMs, WeeklyWinsScheduler.initialDelayMillis(now, DayOfWeek.SUNDAY, 18))
    }

    @Test
    fun sundayHourPassed_schedulesNextSunday() {
        val now = LocalDateTime.of(2026, 7, 5, 20, 0) // Sunday 20:00
        // 18:00 today is behind us → next Sunday 18:00 is 7 days minus 2h out.
        assertEquals(7 * oneDayMs - 2 * oneHourMs, WeeklyWinsScheduler.initialDelayMillis(now, DayOfWeek.SUNDAY, 18))
    }

    @Test
    fun weekday_schedulesTheComingSunday() {
        val now = LocalDateTime.of(2026, 7, 1, 18, 0) // Wednesday 18:00
        // Wed → Sun is 4 days ahead; same hour so exactly 4 days.
        assertEquals(4 * oneDayMs, WeeklyWinsScheduler.initialDelayMillis(now, DayOfWeek.SUNDAY, 18))
    }

    @Test
    fun sundayExactlyAtHour_schedulesNextSunday_notZero() {
        val now = LocalDateTime.of(2026, 7, 5, 18, 0) // Sunday 18:00 sharp
        // Not strictly after → roll a full week (a 0ms delay would fire immediately).
        assertEquals(7 * oneDayMs, WeeklyWinsScheduler.initialDelayMillis(now, DayOfWeek.SUNDAY, 18))
    }
}
