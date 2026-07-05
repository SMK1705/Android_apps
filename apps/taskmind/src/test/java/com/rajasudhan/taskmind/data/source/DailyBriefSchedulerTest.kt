package com.rajasudhan.taskmind.data.source

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** The day-boundary math for the morning brief's first fire. */
class DailyBriefSchedulerTest {

    private val oneHourMs = 60 * 60 * 1000L

    @Test
    fun targetLaterToday_schedulesToday() {
        val now = LocalDateTime.of(2026, 7, 2, 6, 0)
        // 08:00 is 2h ahead.
        assertEquals(2 * oneHourMs, DailyBriefScheduler.initialDelayMillis(now, 8, 0))
    }

    @Test
    fun targetAlreadyPassedToday_schedulesTomorrow() {
        val now = LocalDateTime.of(2026, 7, 2, 9, 0)
        // 08:00 today is behind us → next is 08:00 tomorrow, 23h out.
        assertEquals(23 * oneHourMs, DailyBriefScheduler.initialDelayMillis(now, 8, 0))
    }

    @Test
    fun targetExactlyNow_schedulesTomorrow_notZero() {
        val now = LocalDateTime.of(2026, 7, 2, 8, 0)
        // Not strictly after → roll to tomorrow (a 0ms delay would fire immediately).
        assertEquals(24 * oneHourMs, DailyBriefScheduler.initialDelayMillis(now, 8, 0))
    }

    @Test
    fun handlesMinutes() {
        val now = LocalDateTime.of(2026, 7, 2, 7, 30)
        // 08:15 is 45 min ahead.
        assertEquals(45 * 60 * 1000L, DailyBriefScheduler.initialDelayMillis(now, 8, 15))
    }

    @Test
    fun dstSpringForward_delayIsRealElapsed_notWallClock() {
        // US DST begins 2026-03-08: at 02:00 the clock jumps to 03:00, so 02:00–02:59 don't exist.
        val ny = ZoneId.of("America/New_York")
        val now = LocalDateTime.of(2026, 3, 8, 1, 0) // 01:00, before the jump
        // Wall-clock says 01:00→04:00 = 3h, but only 2h of REAL time elapses across the skipped hour.
        assertEquals(2 * oneHourMs, DailyBriefScheduler.initialDelayMillis(now, 4, 0, ny))
    }
}
