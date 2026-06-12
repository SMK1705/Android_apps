package com.rajasudhan.taskmind.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUsageDigestTest {

    private fun min(m: Long) = m * 60_000L

    @Test
    fun humanDurationFormats() {
        assertEquals("0m", AppUsageDigest.humanDuration(0))
        assertEquals("45m", AppUsageDigest.humanDuration(min(45)))
        assertEquals("1h 30m", AppUsageDigest.humanDuration(min(90)))
        assertEquals("2h 0m", AppUsageDigest.humanDuration(min(120)))
    }

    @Test
    fun titleFormats() {
        assertEquals("Screen time — Jun 11", AppUsageDigest.title("Jun 11"))
    }

    @Test
    fun bodyRanksAndLimitsToTopN() {
        val stats = listOf(
            AppUsageStat("Chrome", min(60)),
            AppUsageStat("Instagram", min(90)),
            AppUsageStat("WhatsApp", min(30)),
            AppUsageStat("Maps", min(5)),
            AppUsageStat("Gmail", min(10)),
            AppUsageStat("Slack", min(20))
        )
        val body = AppUsageDigest.body(stats, topN = 3)
        val lines = body.lines()
        assertEquals("Total screen time: 3h 35m.", lines[0]) // 215 min total
        assertEquals("Top apps:", lines[1])
        assertEquals("• Instagram — 1h 30m", lines[2])
        assertEquals("• Chrome — 1h 0m", lines[3])
        assertEquals("• WhatsApp — 30m", lines[4])
        assertEquals(5, lines.size) // header + label + 3 apps
    }

    @Test
    fun emptyOrZeroStatsReportNothing() {
        assertEquals("No app usage was recorded.", AppUsageDigest.body(emptyList()))
        assertEquals("No app usage was recorded.", AppUsageDigest.body(listOf(AppUsageStat("X", 0))))
    }
}
