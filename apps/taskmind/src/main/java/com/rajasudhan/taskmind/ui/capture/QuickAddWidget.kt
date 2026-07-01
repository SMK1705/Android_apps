package com.rajasudhan.taskmind.ui.capture

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.rajasudhan.taskmind.R
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Suggestion
import com.rajasudhan.taskmind.data.source.resolveCallNumber
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Home-screen widget. Its primary control is a quick-add button that opens [QuickCaptureActivity];
 * when there is a pending suggestion it also surfaces the top item's title plus a contextual **Call**
 * or **Directions** action — the same act-from-outside affordance as the review notification — so a
 * ready-to-act item can be handled without opening the app. Refreshed via [refresh] whenever the
 * pending set changes.
 */
class QuickAddWidget : AppWidgetProvider() {

    /** Pulls the Hilt-provided DAO into this non-injected provider. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun dao(): TaskMindDao
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val captureIntent = PendingIntent.getActivity(
            context, CAPTURE_RC,
            Intent(context, QuickCaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Data load touches the (encrypted) DB + contacts, so hop off the main thread. goAsync keeps the
        // broadcast alive until the coroutine finishes binding every instance.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val top = runCatching { topPending(context) }.getOrNull()
            val number = top?.let {
                runCatching {
                    resolveCallNumber(context, it.extractedTitle, it.summary, it.rawSnippet, it.source)
                }.getOrNull()
            }
            val place = top?.location?.trim()?.takeUnless { it.isBlank() }
            try {
                for (id in ids) {
                    val views = RemoteViews(context.packageName, R.layout.quick_add_widget).apply {
                        setOnClickPendingIntent(R.id.widget_root, captureIntent)
                        setOnClickPendingIntent(R.id.widget_add_button, captureIntent)
                        bindSuggestion(context, top, number, place)
                    }
                    manager.updateAppWidget(id, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Shows the top item + its contextual action, or hides both when nothing is pending / actionable. */
    private fun RemoteViews.bindSuggestion(
        context: Context,
        top: Suggestion?,
        number: String?,
        place: String?
    ) {
        if (top == null) {
            setTextViewText(R.id.widget_header, context.getString(R.string.app_name))
            setViewVisibility(R.id.widget_suggestion_title, View.GONE)
            setViewVisibility(R.id.widget_action_button, View.GONE)
            return
        }
        setTextViewText(R.id.widget_header, context.getString(R.string.app_name))
        setTextViewText(R.id.widget_suggestion_title, top.extractedTitle)
        setViewVisibility(R.id.widget_suggestion_title, View.VISIBLE)

        val action = when {
            number != null -> "Call" to Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
            place != null -> "Directions" to Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(place)}")
            )
            else -> null
        }
        if (action == null) {
            setViewVisibility(R.id.widget_action_button, View.GONE)
        } else {
            val (label, intent) = action
            setTextViewText(R.id.widget_action_button, label)
            setViewVisibility(R.id.widget_action_button, View.VISIBLE)
            val pi = PendingIntent.getActivity(
                context, top.id + ACTION_RC_BASE, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setOnClickPendingIntent(R.id.widget_action_button, pi)
        }
    }

    private suspend fun topPending(context: Context): Suggestion? {
        val dao = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .dao()
        val now = System.currentTimeMillis()
        return dao.getPendingSuggestions().first()
            .firstOrNull { it.snoozedUntil == null || it.snoozedUntil!! <= now }
    }

    companion object {
        // Dedicated request code for the capture PendingIntent — kept distinct from every other
        // PendingIntent in the app (the notifier's tap/Approve/Reject, and the action base below).
        private const val CAPTURE_RC = 4_000_000
        // Offset so the widget's Call/Directions PendingIntents don't collide with other request codes.
        private const val ACTION_RC_BASE = 3_000_000

        /** Re-renders every placed widget; call whenever the pending-suggestion set changes. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, QuickAddWidget::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, QuickAddWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
            )
        }
    }
}
