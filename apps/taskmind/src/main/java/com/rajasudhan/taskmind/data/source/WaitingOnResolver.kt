package com.rajasudhan.taskmind.data.source

import com.rajasudhan.taskmind.data.local.TaskMindDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * When a "waiting on <someone>" counterparty next gets in touch, this raises a one-tap "did they
 * deliver?" check ([WaitingConfirmNotifier]) instead of closing the item. It deliberately does NOT
 * complete the note or cancel its nudge — the counterparty resurfacing is the right *moment* to act,
 * but not proof the awaited thing arrived (they may be asking a question or talking about something
 * else entirely). Matching is deliberately conservative (whole-word name tokens via [PersonMatch],
 * not loose substrings) so an unrelated notification never even raises the prompt. The actual close
 * is an explicit user tap, handled by [WaitingConfirmReceiver].
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
     * cancels an alarm here. Returns how many prompts were raised; a no-op when nothing matches.
     */
    suspend fun resolveFrom(sender: String, message: String? = null): Int {
        var prompted = 0
        for (note in dao.getActiveWaitingOn()) {
            if (PersonMatch.matches(sender, note.counterparty ?: continue)) {
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
}
