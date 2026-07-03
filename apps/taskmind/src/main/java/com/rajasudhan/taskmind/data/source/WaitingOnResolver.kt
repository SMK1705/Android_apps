package com.rajasudhan.taskmind.data.source

import com.rajasudhan.taskmind.data.local.TaskMindDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * When a "waiting on <someone>" counterparty next gets in touch, this raises a one-tap "did they
 * deliver?" check ([WaitingConfirmNotifier]) instead of closing the item. It deliberately does NOT
 * complete the note or cancel its nudge — the counterparty resurfacing is the right *moment* to act,
 * but not proof the awaited thing arrived (they may be asking a question or talking about something
 * else entirely). Matching is deliberately conservative (whole-word name tokens, not loose
 * substrings) so an unrelated notification never even raises the prompt; when in doubt it does
 * nothing. The actual close is an explicit user tap, handled by [WaitingConfirmReceiver].
 */
@Singleton
class WaitingOnResolver @Inject constructor(
    private val dao: TaskMindDao,
    private val confirmNotifier: WaitingConfirmNotifier,
) {
    /**
     * For every open waiting-on note whose counterparty matches [sender] (the display name / title of
     * an incoming message), flags it as awaiting confirmation and raises the "did they deliver?"
     * prompt — carrying [message] (the incoming body, if any) as a snippet. Never completes a note or
     * cancels an alarm here. Returns how many prompts were raised; a no-op for a blank/too-short
     * sender or when nothing matches.
     */
    suspend fun resolveFrom(sender: String, message: String? = null): Int {
        val who = normalize(sender)
        if (who.length < MIN_LEN) return 0
        val senderTokens = tokens(who)
        var prompted = 0
        for (note in dao.getActiveWaitingOn()) {
            val cp = normalize(note.counterparty ?: "")
            if (cp.length < MIN_LEN) continue
            if (matches(senderTokens, tokens(cp))) {
                // First contact flips it into the "awaiting your confirmation" state (surfaced by the
                // Ready-to-close filter); later messages just refresh the prompt without re-stamping.
                if (note.pendingConfirmSince == null) {
                    dao.setPendingConfirm(note.id, System.currentTimeMillis())
                }
                confirmNotifier.prompt(note, message)
                prompted++
            }
        }
        return prompted
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
