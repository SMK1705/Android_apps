package com.rajasudhan.taskmind.data.source

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import com.rajasudhan.taskmind.data.source.understanding.UnderstandingPipeline
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
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

    @Inject
    lateinit var personContextNotifier: PersonContextNotifier

    private val job = SupervisorJob()
    // A handler here is what keeps a failure in the async work (e.g. the cloud LLM path throwing on a
    // network blip) from propagating uncaught and CRASHING the listener process — which would take
    // down notification capture entirely until the system rebinds, losing messages in the meantime.
    private val exceptionHandler = CoroutineExceptionHandler { _, e ->
        Log.w(TAG, "notification handling failed", e)
    }
    private val scope = CoroutineScope(Dispatchers.IO + job + exceptionHandler)

    // In-memory, time-boxed suppression of RAPID unchanged re-posts (token -> last-handled millis).
    // Deliberately NOT persisted: it must forget quickly so a later distinct event with identical
    // content on the same key is re-captured (the persistent ledger is the sweep's job, not this).
    private val recentLiveTokens = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** True if [token] was handled on the live path within [RECENT_REPOST_WINDOW_MS]; records it as now. */
    private fun isRecentLiveRepost(token: String, now: Long): Boolean {
        recentLiveTokens.values.removeIf { now - it > RECENT_REPOST_WINDOW_MS } // prune expired
        val prev = recentLiveTokens.put(token, now)
        return prev != null && now - prev <= RECENT_REPOST_WINDOW_MS
    }

    companion object {
        private const val TAG = "TaskMindNotifListener"

        // A live re-post of the SAME content on the SAME key within this window is treated as a
        // duplicate (an app re-posting an unchanged notification seconds apart) and skipped. Kept
        // short and in-memory so a genuinely-new later event with identical content on the same key
        // (e.g. a second missed call from the same person after the first was rejected) is NOT
        // permanently suppressed — the persistent ledger is consulted only by the reconnect sweep.
        private const val RECENT_REPOST_WINDOW_MS = 2 * 60 * 1000L // 2 min

        // The catch-up sweep recovers only RECENT notifications — a realistic listener-downtime gap
        // (battery-kill, reboot, rebind) — not the entire historical shade. Without this, the first
        // grant of access (empty ledger) would mine days-old messages still sitting in the shade and
        // turn them into fresh, wrongly-dated tasks (processText always stamps "today").
        @VisibleForTesting
        internal const val SWEEP_MAX_AGE_MS = 6 * 60 * 60 * 1000L // 6h

        @VisibleForTesting
        internal fun isWithinSweepWindow(postTime: Long, now: Long): Boolean =
            now - postTime <= SWEEP_MAX_AGE_MS

        /**
         * Dedup token: the notification's stable key plus a hash of the content we're about to process.
         * Re-running the LLM is skipped when the SAME key re-posts the SAME content (an app re-posting
         * an unchanged "2 new messages"), while genuinely-new content (an evolving chat) yields a new
         * token and is still captured. Only the hash is stored — never the message text — so the ledger
         * holds no sensitive content.
         */
        @VisibleForTesting
        internal fun notificationDedupToken(key: String?, content: String?): String =
            // Length + hash so a hash collision alone can't drop a distinct message (there is no
            // periodic re-scan to recover a wrongly-skipped notification).
            "${key ?: "?"}#${content?.length ?: 0}:${content?.hashCode() ?: 0}"

        /**
         * Whether a notification is conversational/actionable enough to capture. [hasBody] must be the
         * result of the full [bestNotificationText] extraction (MessagingStyle / big-text / inbox lines
         * / EXTRA_TEXT), NOT a bare EXTRA_TEXT check — otherwise an uncategorised app's DM whose body
         * lives only in a MessagingStyle payload is wrongly dropped.
         */
        @VisibleForTesting
        internal fun isRelevantNotification(flags: Int, category: String?, hasBody: Boolean): Boolean {
            if (flags and Notification.FLAG_ONGOING_EVENT != 0) return false
            if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
            return when (category) {
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
                // Unknown/uncategorized: accept only if it actually carries body text (any style).
                else -> hasBody
            }
        }

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
        handle(sbn, fromSweep = false)
    }

    /**
     * Catch-up sweep for notifications missed while we were unbound. The system does NOT re-deliver
     * notifications posted while the listener was disconnected (an OEM battery-kill, the window
     * between boot and rebind, or before the user granted access), so without this a WhatsApp/Telegram
     * DM that lands in any of those gaps is lost forever — unlike an SMS, which the periodic scanner
     * recovers. Here we replay whatever is still in the shade at (re)connect through the same path,
     * deduped against [processedNotificationKeys] so we don't re-run the LLM on ones already handled.
     */
    override fun onListenerConnected() {
        super.onListenerConnected()
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        // Isolate each row: one malformed notification must not abort the whole sweep.
        active.forEach { sbn -> runCatching { handle(sbn, fromSweep = true) } }
    }

    /** Parsed + relevance-filtered notification content, ready for the async pipeline. */
    private data class Parsed(
        val notification: Notification,
        val packageName: String,
        val title: String?,
        val text: String?,
        val missedCaller: String?,
        val token: String,
    )

    private fun handle(sbn: StatusBarNotification, fromSweep: Boolean) {
        // Never process our own notifications or system/UI noise.
        if (sbn.packageName == applicationContext.packageName) return
        if (sbn.packageName in IGNORED_PACKAGES) return
        // On the catch-up sweep, recover only recent notifications — never mine the historical shade
        // (a days-old message would become a fresh, mis-dated task). The live path has no age gate.
        if (fromSweep && !isWithinSweepWindow(sbn.postTime, System.currentTimeMillis())) return

        // Parse defensively ON THE BINDER THREAD: a malformed notification (null extras, or a custom
        // app Parcelable that fails to unmarshal inside getCharSequence/MessagingStyle extraction) must
        // be skipped, not throw — a crash here kills the listener and drops every subsequent message
        // until the system rebinds.
        val parsed = runCatching { extract(sbn) }
            .onFailure { Log.w(TAG, "notification parse failed", it) }
            .getOrNull() ?: return

        scope.launch {
            if (!sourceManager.isNotificationsEnabled.first()) return@launch
            // Per-app allowlist: empty = monitor all; otherwise only the chosen apps.
            val allowlist = sourceManager.notificationAllowlist.first()
            if (allowlist.isNotEmpty() && parsed.packageName !in allowlist) return@launch
            // Dedup. On the reconnect SWEEP, skip anything already handled (the persistent ledger
            // recovers a downtime gap without re-running the LLM). On the LIVE path, skip only a rapid
            // unchanged re-post (in-memory, time-boxed) — never permanently, so a later distinct event
            // with identical content on the same key is still captured and the pipeline's state-aware
            // dedup gets to decide. Evolving content hashes to a new token and is always processed.
            if (fromSweep) {
                if (parsed.token in sourceManager.processedNotificationKeys.first()) return@launch
            } else if (isRecentLiveRepost(parsed.token, System.currentTimeMillis())) {
                return@launch
            }
            // A "waiting on <this sender>" item: now they've been in touch, raise a one-tap "did they
            // deliver?" check (WaitingOnResolver never closes it on its own — they might be messaging
            // about anything). Gate strictly on a genuine person-to-person message (or a missed call):
            // only there is the title actually a sender name. An app-generated title ("Your bank
            // statement is ready") must NEVER even prompt against a "waiting on the bank" item.
            val resolveSender = when {
                parsed.missedCaller != null -> parsed.missedCaller
                isPersonMessage(parsed.notification) -> parsed.title
                else -> null
            }
            resolveSender?.let {
                // Pass the message body so the "did they deliver?" prompt can show a short snippet;
                // a missed call has none.
                waitingOnResolver.resolveFrom(it, parsed.text)
                // Person-context: surface any open item tied to this sender, right when they message.
                personContextNotifier.notifyForContact(it)
            }
            // NOTE: do not log notification content — it is sensitive user data.
            if (parsed.missedCaller != null) {
                // No number in the notification; the Call button resolves the name via Contacts.
                understandingPipeline.addCallback(displayName = parsed.missedCaller, number = null)
            } else {
                // Normalise a group summary's rotating "(N messages): <sender>" title to the stable
                // group name (#199), so the source — and the rejection-learning key derived from it —
                // is the same across the group's notifications instead of a fresh one each time. The
                // raw title is still used above for person/waiting-on resolution, which wants the sender.
                val source = "Notification from ${NotificationText.conversationTitle(parsed.title!!)}"
                understandingPipeline.processText(source, parsed.text!!)
            }
            // Record the token (after successful handling) so an unchanged re-post or a later sweep
            // won't reprocess it. A throw above skips this — the exception handler logs it — so it's
            // retried next time.
            sourceManager.addProcessedNotificationKeys(listOf(parsed.token))
        }
    }

    /**
     * Synchronous parse + relevance filter. Returns null to drop the notification. MAY THROW on a
     * malformed notification — the caller wraps it so a bad notification can't crash the listener.
     */
    private fun extract(sbn: StatusBarNotification): Parsed? {
        val notification = sbn.notification
        val extras = notification.extras ?: return null
        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = bestNotificationText(notification)

        // A chat-app missed call (WhatsApp, Telegram, …) is only ever a notification — it never hits
        // the call log. It's also CATEGORY_CALL, which isRelevantNotification drops, so detect it up
        // front and let it through. Dialer apps are handled by the call-log scan (see PHONE_PACKAGES).
        val missedCaller =
            if (sbn.packageName in PHONE_PACKAGES) null else PhoneUtil.missedCallName(title, text)

        if (missedCaller == null) {
            // Consult the FULL extracted body (bestNotificationText), not just EXTRA_TEXT, so a
            // MessagingStyle-only DM from an uncategorised app isn't dropped.
            if (!isRelevantNotification(notification.flags, notification.category, text != null)) return null
            if (title == null || text == null) return null
        }

        // Hash the caller (missed call) or the message body so an unchanged re-post is skipped.
        val token = notificationDedupToken(sbn.key, missedCaller ?: text)
        return Parsed(notification, sbn.packageName, title, text, missedCaller, token)
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

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
