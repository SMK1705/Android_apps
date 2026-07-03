package com.rajasudhan.taskmind.data.source

import com.rajasudhan.taskmind.data.local.TaskMindDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Auto-resolves a "waiting on <someone>" note the moment that counterparty next gets in touch — the
 * whole point of a waiting list is to stop chasing once they reply. Matching is deliberately
 * conservative (whole-word name tokens, not loose substrings) so an unrelated notification never
 * closes a real item; when in doubt it does nothing and the user resolves it by hand.
 */
@Singleton
class WaitingOnResolver @Inject constructor(
    private val dao: TaskMindDao,
    private val alarmScheduler: AlarmScheduler,
) {
    /**
     * Marks any open waiting-on note done whose counterparty matches [sender] (the display name /
     * title of an incoming message), cancelling its follow-up alarm. Returns how many were resolved;
     * a no-op for a blank/too-short sender or when nothing matches.
     */
    suspend fun resolveFrom(sender: String): Int {
        val who = normalize(sender)
        if (who.length < MIN_LEN) return 0
        val senderTokens = tokens(who)
        var resolved = 0
        for (note in dao.getActiveWaitingOn()) {
            val cp = normalize(note.counterparty ?: "")
            if (cp.length < MIN_LEN) continue
            if (matches(senderTokens, tokens(cp))) {
                dao.setNoteCompleted(note.id, true, System.currentTimeMillis())
                alarmScheduler.cancel(note.id)
                resolved++
            }
        }
        return resolved
    }

    /**
     * True when the counterparty plausibly names the sender: every name-word of the counterparty is a
     * whole word in the sender. Whole-word (not substring) matching keeps "Dave" from resolving a
     * "David" item, while "John" still resolves against a "John Doe" notification.
     */
    private fun matches(senderTokens: Set<String>, counterpartyTokens: Set<String>): Boolean =
        counterpartyTokens.isNotEmpty() && counterpartyTokens.all { it in senderTokens }

    private fun normalize(s: String): String =
        s.trim().lowercase().removePrefix("the ").trim()

    private fun tokens(s: String): Set<String> =
        s.split(WHITESPACE).map { it.trim(*PUNCT) }.filter { it.length >= MIN_LEN }.toSet()

    private companion object {
        // Guards against trivially-short matches ("Al" resolving "Alex", "PA" a payment alert).
        const val MIN_LEN = 3
        val WHITESPACE = Regex("\\s+")
        val PUNCT = charArrayOf('.', ',', ':', ';', '!', '?', '(', ')', '"', '\'')
    }
}
