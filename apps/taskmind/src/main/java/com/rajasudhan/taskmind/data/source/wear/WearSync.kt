package com.rajasudhan.taskmind.data.source.wear

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.rajasudhan.taskmind.data.source.appfunctions.DueTodayResult
import com.rajasudhan.taskmind.data.source.appfunctions.TaskMindAppFunctions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes the phone's next-due item to the watch tile (#216) as a Data Layer DataItem at
 * [WearContract.PATH_NEXT_DUE]. Reuses the existing "due today" query (soonest-first) so the phone stays
 * the single source of truth for what's due — the watch never computes it, it renders what's pushed.
 */
@Singleton
class WearSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appFunctions: TaskMindAppFunctions,
) {
    /** Query the next-due item and push it to the tile; a no-op-ish local write when no watch is paired. */
    suspend fun publishNextDue(now: Long = System.currentTimeMillis(), today: LocalDate = LocalDate.now()) {
        val (title, whenText) = nextDuePayload(appFunctions.getItemsDueToday(today))
        val request = PutDataMapRequest.create(WearContract.PATH_NEXT_DUE).apply {
            dataMap.putString(WearContract.KEY_TITLE, title)
            dataMap.putString(WearContract.KEY_WHEN, whenText)
            // Bump a timestamp so the DataItem actually changes (and re-syncs) even when the title/when are
            // identical to last time — the Data Layer drops a putDataItem whose bytes are unchanged.
            dataMap.putLong(WearContract.KEY_UPDATED_AT, now)
        }
        Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent()).await()
    }

    companion object {
        /**
         * The (title, when) the tile should show for a due-today result. Pure + unit-testable. Empty
         * strings when nothing is due, which the tile renders as its "All clear" state.
         */
        internal fun nextDuePayload(result: DueTodayResult): Pair<String, String> {
            val next = result.items.firstOrNull() ?: return "" to ""
            return next.title to formatWhen(next.dueTime)
        }

        /** "Today · 15:00" when timed, plain "Today" when the item has no specific time. */
        internal fun formatWhen(dueTime: String?): String =
            if (dueTime.isNullOrBlank()) "Today" else "Today · $dueTime"
    }
}
