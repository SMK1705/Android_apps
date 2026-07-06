package com.rajasudhan.taskmind.data.source

/**
 * Pure helpers for turning a raw notification title into a STABLE conversation identity (#199).
 *
 * Group-chat summaries (WhatsApp, Telegram, …) title themselves like `"<group> (N messages): <latest
 * sender>"`, where the count AND the latest sender rotate on every notification. Using that raw title
 * as the source/sender identity fragments everything keyed on it — most visibly the on-device
 * rejection learning ([RejectionLearner]), which then mints a brand-new pattern per notification and
 * never crosses its threshold, so a noisy group is never actually learned (and the "What TaskMind
 * knows" screen fills with duplicates). Collapsing to the group name lets those signals accumulate.
 * No Android deps, so it's covered by a plain-JVM unit test.
 */
object NotificationText {

    // The volatile "(N messages)" summary marker and everything after it (the ": <sender>" preview).
    // The count is what makes it a *summary* of several messages — exactly the case that rotates and
    // fragments the key. A 1:1 title / SMS number / email name has no such marker, so it's untouched.
    private val SUMMARY_TAIL = Regex("""\s*\(\d+\s+messages?\).*$""", RegexOption.IGNORE_CASE)

    /**
     * The stable conversation/sender identity from a notification [title]: strips a group summary's
     * rotating `"(N messages): <sender>"` tail so repeated notifications from the same group share one
     * identity. Returns a non-group title unchanged, and falls back to the trimmed raw title if
     * stripping would leave nothing (a pathological `"(5 messages)"`-only title).
     */
    fun conversationTitle(title: String): String =
        title.replace(SUMMARY_TAIL, "").trim().ifBlank { title.trim() }
}
