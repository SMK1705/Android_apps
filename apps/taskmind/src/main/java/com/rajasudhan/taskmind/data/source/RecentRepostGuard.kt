package com.rajasudhan.taskmind.data.source

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory, time-boxed suppression of RAPID unchanged re-posts on the notification live path
 * (token -> last-handled millis). Deliberately NOT persisted: it forgets quickly so a genuinely-new
 * later event with identical content on the same key (e.g. a second missed call from the same person)
 * is re-captured — the persistent ledger is the reconnect sweep's job, not this.
 *
 * [seen] only PEEKS; a token is registered only via [record], which the listener calls AFTER a message
 * is actually handled. Recording during the dedup check (the old behaviour) meant a first attempt that
 * threw — e.g. the cloud LLM path failing on a network blip — left the token marked, so an identical
 * re-post within the window was dropped instead of retried, losing the message for up to the window (#N5).
 */
internal class RecentRepostGuard(private val windowMs: Long) {
    private val seenAt = ConcurrentHashMap<String, Long>()

    /** True if [token] was [record]ed as handled within the window ending at [now]. Peek only — no write. */
    fun seen(token: String, now: Long): Boolean {
        seenAt.values.removeIf { now - it > windowMs } // prune expired
        val prev = seenAt[token]
        return prev != null && now - prev <= windowMs
    }

    /** Records that [token] was successfully handled at [now], so a rapid unchanged re-post is skipped. */
    fun record(token: String, now: Long) {
        seenAt[token] = now
    }
}
