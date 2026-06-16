package com.rajasudhan.taskmind.data.source

import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.RejectedPattern
import com.rajasudhan.taskmind.data.model.Suggestion
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device learning loop: remembers senders the user repeatedly rejects and, once a sender crosses
 * a threshold, down-ranks future suggestions from it (a soft confidence penalty, never a hard block).
 * Entirely local — nothing leaves the device.
 */
@Singleton
class RejectionLearner @Inject constructor(
    private val dao: TaskMindDao
) {
    /** Record one rejection against the suggestion's sender (no-op for sources without a sender). */
    suspend fun recordRejection(suggestion: Suggestion) {
        val key = senderKey(suggestion.source) ?: return
        val existing = dao.rejectedPatternFor("sender", key)
        dao.upsertRejectedPattern(
            RejectedPattern("sender", key, (existing?.count ?: 0) + 1, System.currentTimeMillis())
        )
    }

    /** Confidence penalty to apply to items from [source] (0 unless its sender is past the threshold). */
    suspend fun confidencePenalty(source: String): Double {
        val key = senderKey(source) ?: return 0.0
        val count = dao.rejectedPatternFor("sender", key)?.count ?: 0
        return if (count >= REJECT_THRESHOLD) PENALTY else 0.0
    }

    companion object {
        const val REJECT_THRESHOLD = 3
        const val PENALTY = 0.3

        /**
         * The sender/origin from a source label such as "SMS from +91…", "Email (a@x) from Alice",
         * or "Notification from WhatsApp". Returns null for sources with no sender (Recording:,
         * Screenshot:, Call Log, App Usage, Manual entry, Shared, …) so they're never penalised.
         */
        fun senderKey(source: String): String? {
            val idx = source.indexOf(" from ")
            if (idx < 0) return null
            return source.substring(idx + 6).trim().lowercase().ifBlank { null }
        }
    }
}
