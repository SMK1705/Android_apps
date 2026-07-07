package com.rajasudhan.taskmind.ui.capture

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.rajasudhan.taskmind.MainActivity
import com.rajasudhan.taskmind.R

/**
 * Publishes the launcher long-press shortcuts — **Type**, **Speak**, and **Inbox** (with a live pending
 * count) — as *dynamic* shortcuts.
 *
 * They are dynamic rather than static (`shortcuts.xml`) for two reasons: the Inbox label carries a
 * count that changes at runtime, and building the launch [Intent] in code with a component
 * (`Intent(context, …::class.java)`) avoids hard-coding the package — so nothing breaks if the app is
 * ever given a build-variant `applicationId` suffix.
 *
 * The **Type** shortcut also doubles as the app's Direct Share target: it is long-lived and tagged with
 * [CATEGORY_SHARE], which matches the `<share-target>` in `shortcuts.xml`, so "share to TaskMind" ranks
 * in the system share sheet's Direct Share row (the share itself is handled by [ShareTargetActivity]).
 *
 * [refresh] replaces the whole set, so it is safe to call repeatedly: once on app launch and again
 * whenever the pending-suggestion count changes (alongside [QuickAddWidget.refresh]).
 */
object CaptureShortcuts {

    const val ID_TYPE = "capture_type"
    const val ID_SPEAK = "capture_speak"
    const val ID_INBOX = "capture_inbox"

    /** Category shared with the `<share-target>` in `shortcuts.xml` so the Type shortcut ranks in Direct Share. */
    const val CATEGORY_SHARE = "com.rajasudhan.taskmind.category.SHARE_TARGET"

    /** Inbox shortcut label with the live pending count folded in. Pure, so the count logic is unit-tested. */
    fun inboxLabel(count: Int): String = if (count > 0) "Inbox · $count" else "Inbox"

    /**
     * (Re)publishes the three capture shortcuts, stamping the Inbox one with [pendingCount]. Safe to
     * call from any thread — [ShortcutManagerCompat] wraps a thread-safe Binder API with no main-thread
     * requirement — so the notifier's IO coroutine and the main-thread app-launch path both call it
     * directly. The shortcuts are always (re)created on the main-thread launch path, so even if a
     * background count-refresh were ever dropped, the shortcuts themselves still exist.
     */
    fun refresh(context: Context, pendingCount: Int) {
        val shortcuts = listOf(
            typeShortcut(context),
            speakShortcut(context),
            inboxShortcut(context, pendingCount),
        )
        // Never let a shortcut-publishing hiccup (e.g. exceeding the launcher's shortcut budget) crash a
        // capture or a notification refresh — this is a convenience surface, not a critical path.
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    private fun typeShortcut(context: Context): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, ID_TYPE)
            .setShortLabel(context.getString(R.string.shortcut_type_short))
            .setLongLabel(context.getString(R.string.shortcut_type_long))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_type))
            .setIntent(captureIntent(context, mode = null))
            // Long-lived + the share category so this same shortcut is offered as a Direct Share target.
            .setLongLived(true)
            .setCategories(setOf(CATEGORY_SHARE))
            .build()

    private fun speakShortcut(context: Context): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, ID_SPEAK)
            .setShortLabel(context.getString(R.string.shortcut_speak_short))
            .setLongLabel(context.getString(R.string.shortcut_speak_long))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_speak))
            .setIntent(captureIntent(context, mode = QuickCaptureActivity.MODE_SPEAK))
            .build()

    private fun inboxShortcut(context: Context, pendingCount: Int): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, ID_INBOX)
            .setShortLabel(inboxLabel(pendingCount))
            .setLongLabel(context.getString(R.string.shortcut_inbox_long))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_inbox))
            .setIntent(
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .putExtra(MainActivity.EXTRA_OPEN_INBOX, true)
            )
            .build()

    /** Opens the lock-free quick-capture dialog; [mode] = "speak" jumps straight to dictation. */
    private fun captureIntent(context: Context, mode: String?): Intent =
        Intent(context, QuickCaptureActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .apply { if (mode != null) putExtra(QuickCaptureActivity.EXTRA_MODE, mode) }
}
