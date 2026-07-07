package com.rajasudhan.taskmind.data.source

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.rajasudhan.taskmind.data.source.understanding.UnderstandingPipeline
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Handles the Approve/Reject actions on the review notification. Approves through the shared
 * [SuggestionApprover] (same path as the Inbox) or rejects (recording the rejection for learning),
 * then refreshes the notification to the next pending item — all without opening the app.
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var dao: com.rajasudhan.taskmind.data.local.TaskMindDao
    @Inject lateinit var approver: SuggestionApprover
    @Inject lateinit var notifier: SuggestionNotifier
    @Inject lateinit var rejectionLearner: RejectionLearner
    @Inject lateinit var pipeline: UnderstandingPipeline

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val id = intent.getIntExtra(EXTRA_ID, -1)
        // Voice capture (#130) carries its spoken text via RemoteInput and no suggestion id; every other
        // action targets a specific pending suggestion.
        val voice = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_VOICE_REPLY)?.toString()
        if (action != ACTION_CAPTURE && id < 0) return

        // Keep the receiver alive while the DB work runs.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(action, id, voice)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Applies the notification action and refreshes the prompt. Extracted from [onReceive] so it's
     * unit-testable without the broadcast/goAsync plumbing.
     *
     * [ACTION_RESURFACE] is the Bounce-Back alarm [SuggestionNotifier.scheduleResurface] sets for a
     * snoozed suggestion's return time. It re-posts the ORIGINAL captured message so the content
     * itself reappears — not a generic "N to review" — unless the item was meanwhile approved,
     * rejected, or un-snoozed, in which case we just reconcile the review prompt.
     */
    internal suspend fun handle(action: String, id: Int, voiceText: String? = null) {
        // Wrist voice capture (#130): the spoken text lands in the Inbox as a pending suggestion via the
        // same agent-capture path as AppFunctions (#127) — no watch-side LLM, so extraction stays on the
        // phone and the on-device privacy claim holds.
        if (action == ACTION_CAPTURE) {
            val text = voiceText?.trim().orEmpty()
            if (text.isNotBlank()) pipeline.captureFromAgent(title = text, source = "Watch")
            else notifier.notifyPending()
            return
        }

        val suggestion = dao.getSuggestionById(id)
        if (action == ACTION_RESURFACE) {
            if (suggestion != null && suggestion.status == "pending" && suggestion.snoozedUntil != null) {
                notifier.notifyBounceBack(suggestion)
            } else {
                notifier.notifyPending()
            }
            return
        }
        if (suggestion != null && suggestion.status == "pending") {
            when (action) {
                ACTION_APPROVE -> approver.approve(suggestion)
                ACTION_REJECT -> {
                    dao.updateSuggestion(suggestion.copy(status = "rejected"))
                    rejectionLearner.recordRejection(suggestion)
                }
                // Snooze from the wrist (#130): hide it until tomorrow morning and arm the bounce-back.
                ACTION_SNOOZE -> {
                    val until = snoozeUntil()
                    dao.updateSuggestion(suggestion.copy(snoozedUntil = until))
                    notifier.scheduleResurface(id, until)
                }
            }
        }
        notifier.notifyPending()
    }

    /** Tomorrow at 09:00 — the one-tap "deal with it in the morning" snooze target. */
    private fun snoozeUntil(): Long =
        LocalDate.now().plusDays(1).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    companion object {
        const val ACTION_APPROVE = "com.rajasudhan.taskmind.action.APPROVE"
        const val ACTION_REJECT = "com.rajasudhan.taskmind.action.REJECT"
        const val ACTION_SNOOZE = "com.rajasudhan.taskmind.action.SUGGESTION_SNOOZE"
        const val ACTION_CAPTURE = "com.rajasudhan.taskmind.action.VOICE_CAPTURE"
        const val ACTION_RESURFACE = "com.rajasudhan.taskmind.action.RESURFACE"
        const val EXTRA_ID = "suggestion_id"
        /** RemoteInput key for the spoken task text (#130). */
        const val KEY_VOICE_REPLY = "voice_reply"
    }
}
