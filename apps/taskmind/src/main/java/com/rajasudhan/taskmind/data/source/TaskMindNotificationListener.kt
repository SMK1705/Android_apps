package com.rajasudhan.taskmind.data.source

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
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

    @Inject
    lateinit var waitingOnResolver: WaitingOnResolver

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

        // Phone/dialer apps: their missed calls are captured from the call log (with the real
        // number), so we skip their notifications here to avoid a duplicate that has only the name.
        private val PHONE_PACKAGES = setOf(
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.phone",
            "com.android.server.telecom"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        // Never process our own notifications or system/UI noise.
        if (sbn.packageName == applicationContext.packageName) return
        if (sbn.packageName in IGNORED_PACKAGES) return

        val notification = sbn.notification
        val title = notification.extras.getString(Notification.EXTRA_TITLE)
        val text = bestNotificationText(notification)

        // A chat-app missed call (WhatsApp, Telegram, …) is only ever a notification — it never hits
        // the call log. It's also CATEGORY_CALL, which isRelevant() drops, so detect it up front and
        // let it through. Dialer apps are handled by the call-log scan instead (see PHONE_PACKAGES).
        val missedCaller =
            if (sbn.packageName in PHONE_PACKAGES) null else PhoneUtil.missedCallName(title, text)

        if (missedCaller == null) {
            if (!isRelevant(sbn)) return
            if (title == null || text == null) return
        }

        scope.launch {
            if (!sourceManager.isNotificationsEnabled.first()) return@launch
            // Per-app allowlist: empty = monitor all; otherwise only the chosen apps.
            val allowlist = sourceManager.notificationAllowlist.first()
            if (allowlist.isNotEmpty() && sbn.packageName !in allowlist) return@launch
            // A "waiting on <this sender>" item: now they've been in touch, raise a one-tap "did they
            // deliver?" check (WaitingOnResolver never closes it on its own — they might be messaging
            // about anything). Gate strictly on a genuine person-to-person message (or a missed call):
            // only there is the title actually a sender name. An app-generated title ("Your bank
            // statement is ready") must NEVER even prompt against a "waiting on the bank" item.
            val resolveSender = when {
                missedCaller != null -> missedCaller
                isPersonMessage(notification) -> title
                else -> null
            }
            // Pass the message body so the prompt can show a short snippet; a missed call has none.
            resolveSender?.let { waitingOnResolver.resolveFrom(it, text) }
            // NOTE: do not log notification content — it is sensitive user data.
            if (missedCaller != null) {
                // No number in the notification; the Call button resolves the name via Contacts.
                understandingPipeline.addCallback(displayName = missedCaller, number = null)
            } else {
                understandingPipeline.processText("Notification from ${title!!}", text!!)
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Aggressive battery management (esp. on Samsung) can kill the listener; ask the system to
        // rebind so we keep seeing notifications — otherwise WhatsApp calls/messages slip past.
        runCatching {
            requestRebind(ComponentName(this, TaskMindNotificationListener::class.java))
        }
    }

    /**
     * The richest readable text in a notification. Messaging apps (LinkedIn, WhatsApp, …) put the
     * actual message(s) in a MessagingStyle payload, while EXTRA_TEXT is often only a "2 new messages"
     * summary — which then reads as non-actionable noise and a real DM ("can we meet at 3?") is lost.
     * So prefer the MessagingStyle messages, then the expanded big-text / inbox lines, then EXTRA_TEXT.
     */
    /**
     * True only for a genuine person-to-person message — a MessagingStyle payload or an explicit
     * CATEGORY_MESSAGE — where the notification title is reliably the *sender*. App-generated
     * notifications (statements, promos, order updates) have an app-authored title, so they must not
     * drive counterparty auto-resolution.
     */
    private fun isPersonMessage(n: Notification): Boolean =
        n.category == Notification.CATEGORY_MESSAGE ||
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)?.messages?.isNotEmpty() == true

    private fun bestNotificationText(n: Notification): String? {
        val messages = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)?.messages
        if (!messages.isNullOrEmpty()) {
            val joined = messages.takeLast(6).mapNotNull { m ->
                val body = m.text?.toString()?.trim()
                if (body.isNullOrBlank()) {
                    null
                } else {
                    val who = m.person?.name?.toString()?.trim()
                    if (!who.isNullOrBlank()) "$who: $body" else body
                }
            }.joinToString("\n").trim()
            if (joined.isNotBlank()) return joined
        }

        val extras = n.extras
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        if (!big.isNullOrBlank()) return big

        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (lines != null) {
            val joined = lines.mapNotNull { cs ->
                val s = cs?.toString()?.trim()
                if (s.isNullOrBlank()) null else s
            }.joinToString("\n").trim()
            if (joined.isNotBlank()) return joined
        }

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        return if (text.isNullOrBlank()) null else text
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
