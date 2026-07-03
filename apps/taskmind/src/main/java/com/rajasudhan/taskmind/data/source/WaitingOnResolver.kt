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
        var resolved = 0
        for (note in dao.getActiveWaitingOn()) {
            if (PersonMatch.matches(sender, note.counterparty ?: continue)) {
                dao.setNoteCompleted(note.id, true, System.currentTimeMillis())
                alarmScheduler.cancel(note.id)
                resolved++
            }
        }
        return resolved
    }
}
