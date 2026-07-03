package com.rajasudhan.taskmind.data.source

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val id = intent.getIntExtra(EXTRA_ID, -1)
        if (id < 0) return

        // Keep the receiver alive while the DB work runs.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(action, id)
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
    internal suspend fun handle(action: String, id: Int) {
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
            }
        }
        notifier.notifyPending()
    }

    companion object {
        const val ACTION_APPROVE = "com.rajasudhan.taskmind.action.APPROVE"
        const val ACTION_REJECT = "com.rajasudhan.taskmind.action.REJECT"
        const val ACTION_RESURFACE = "com.rajasudhan.taskmind.action.RESURFACE"
        const val EXTRA_ID = "suggestion_id"
    }
}
