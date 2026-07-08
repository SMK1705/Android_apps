package com.rajasudhan.taskmind.data.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The live-path rapid-repost suppressor behind the notification listener (#N5). */
class RecentRepostGuardTest {

    private val windowMs = 2 * 60 * 1000L

    @Test
    fun seen_isFalse_untilRecorded_andPeekingNeverRegisters() {
        val guard = RecentRepostGuard(windowMs)
        val t0 = 1_000_000L

        // The core #N5 fix: seen() only peeks. Checking a token (before the message is actually handled)
        // must NOT mark it — otherwise a re-post arriving while the first attempt is still failing would
        // be suppressed as a "recent re-post" and dropped instead of retried.
        assertFalse(guard.seen("tok", t0))
        assertFalse(guard.seen("tok", t0 + 1_000)) // still unrecorded — peeking never registered it
    }

    @Test
    fun seen_isTrue_afterRecord_withinTheWindow() {
        val guard = RecentRepostGuard(windowMs)
        val t0 = 1_000_000L
        guard.record("tok", t0)

        assertTrue(guard.seen("tok", t0 + 5_000)) // a rapid unchanged re-post is suppressed once handled
    }

    @Test
    fun seen_isFalse_afterTheWindowExpires() {
        val guard = RecentRepostGuard(windowMs)
        val t0 = 1_000_000L
        guard.record("tok", t0)

        // Window passed: a genuinely-new later event with identical content on the same key is re-captured.
        assertFalse(guard.seen("tok", t0 + windowMs + 1))
    }

    @Test
    fun distinctTokens_areIndependent() {
        val guard = RecentRepostGuard(windowMs)
        val t0 = 1_000_000L
        guard.record("a", t0)

        assertTrue(guard.seen("a", t0 + 1))
        assertFalse(guard.seen("b", t0 + 1))
    }
}
