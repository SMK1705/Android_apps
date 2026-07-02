package com.rajasudhan.taskmind.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure recap wording: counts, the "caught from …" trust line, and staying silent on an empty week. */
class WeeklyWinsComposerTest {

    @Test
    fun emptyWeek_composesNothing() {
        assertNull(WeeklyWinsComposer.compose(emptyList()))
    }

    @Test
    fun countsWinsAndNamesThem() {
        val recap = WeeklyWinsComposer.compose(
            listOf(
                WeeklyWin("Pay rent", "Manual entry"),
                WeeklyWin("Call plumber", "Manual entry"),
            )
        )!!
        assertEquals("2 wins this week 🎉", recap.title)
        assertTrue(recap.body.contains("You finished 2 things this week."))
        assertTrue(recap.body.contains("Including: Pay rent, Call plumber."))
    }

    @Test
    fun singularWording_forOneWin() {
        val recap = WeeklyWinsComposer.compose(listOf(WeeklyWin("Ship it", "Manual entry")))!!
        assertEquals("1 win this week 🎉", recap.title)
        assertTrue(recap.body.contains("You finished 1 thing this week."))
    }

    @Test
    fun allManual_hasNoCaughtLine() {
        val recap = WeeklyWinsComposer.compose(
            listOf(WeeklyWin("A", "Manual entry"), WeeklyWin("B", "Manual entry"))
        )!!
        assertTrue(!recap.body.contains("caught from"))
    }

    @Test
    fun caughtLine_countsAutoCapturedAndNamesChannels() {
        val recap = WeeklyWinsComposer.compose(
            listOf(
                WeeklyWin("Reply to Sam", "SMS from +1 555 0100"),
                WeeklyWin("Renew licence", "Notification from Gmail"),
                WeeklyWin("Buy milk", "Manual entry"),
            )
        )!!
        // 2 of 3 were auto-caught (the manual one doesn't count).
        assertTrue(recap.body.contains("2 caught from"))
        assertTrue(recap.body.contains("SMS"))
        assertTrue(recap.body.contains("a notification"))
        assertTrue(recap.body.contains("you'd have forgotten."))
    }

    @Test
    fun channelsListed_mostCommonFirst() {
        val recap = WeeklyWinsComposer.compose(
            listOf(
                WeeklyWin("a", "SMS"),
                WeeklyWin("b", "SMS"),
                WeeklyWin("c", "Notification from X"),
            )
        )!!
        val caughtLine = recap.body.lines().first { it.contains("caught from") }
        // SMS (2) should be named before the notification (1).
        assertTrue(caughtLine.indexOf("SMS") < caughtLine.indexOf("a notification"))
    }

    @Test
    fun namesAtMostThreeWins() {
        val recap = WeeklyWinsComposer.compose(
            (1..5).map { WeeklyWin("Task $it", "Manual entry") }
        )!!
        assertTrue(recap.body.contains("Including: Task 1, Task 2, Task 3."))
        assertTrue(!recap.body.contains("Task 4"))
    }

    @Test
    fun sourceLabel_mapsChannels() {
        assertEquals("SMS", WeeklyWinsComposer.sourceLabel("SMS from +1"))
        assertEquals("a notification", WeeklyWinsComposer.sourceLabel("Notification from Slack"))
        assertEquals("email", WeeklyWinsComposer.sourceLabel("Gmail message"))
        assertEquals("a voice note", WeeklyWinsComposer.sourceLabel("Voice recording"))
        assertEquals("manual entry", WeeklyWinsComposer.sourceLabel("Manual entry"))
    }
}
