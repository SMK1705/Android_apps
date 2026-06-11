package com.rajasudhan.taskmind.data.source

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.rajasudhan.taskmind.data.source.understanding.UnderstandingPipeline
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TaskMindNotificationListener : NotificationListenerService() {

    @Inject
    lateinit var sourceManager: SourceManager

    @Inject
    lateinit var understandingPipeline: UnderstandingPipeline

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    companion object {
        // System/UI sources that only emit noise (incl. the "Screenshot saved" notification).
        private val IGNORED_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.samsung.android.app.smartcapture",
            "com.samsung.android.smartcapture",
            "com.sec.android.app.launcher",
            "com.google.android.apps.nexuslauncher"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        // Never process our own notifications, system/UI noise, or filtered categories.
        if (sbn.packageName == applicationContext.packageName) return
        if (sbn.packageName in IGNORED_PACKAGES) return
        if (!isRelevant(sbn)) return

        val notification = sbn.notification
        val title = notification.extras.getString(Notification.EXTRA_TITLE) ?: return
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return

        scope.launch {
            if (!sourceManager.isNotificationsEnabled.first()) return@launch
            // Per-app allowlist: empty = monitor all; otherwise only the chosen apps.
            val allowlist = sourceManager.notificationAllowlist.first()
            if (allowlist.isNotEmpty() && sbn.packageName !in allowlist) return@launch
            // NOTE: do not log notification content — it is sensitive user data.
            understandingPipeline.processText("Notification from $title", text)
        }
    }

    /**
     * Keeps only conversational / actionable notifications and drops the noise
     * (ongoing media & navigation, progress bars, group summaries, system chatter)
     * so the Inbox isn't flooded with everything on screen.
     */
    private fun isRelevant(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return false
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        return when (n.category) {
            Notification.CATEGORY_MESSAGE,
            Notification.CATEGORY_EMAIL,
            Notification.CATEGORY_EVENT,
            Notification.CATEGORY_REMINDER,
            Notification.CATEGORY_SOCIAL -> true
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_SYSTEM,
            Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_CALL -> false
            // Unknown/uncategorized: only accept if it actually has body text.
            else -> n.extras.getCharSequence(Notification.EXTRA_TEXT) != null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
