package com.rajasudhan.taskmind.data.source

import android.app.usage.UsageStatsManager
import android.content.Context
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Suggestion
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Once per day, builds a screen-time digest (total + top apps) for the previous day from
 * UsageStatsManager and drops it into the Inbox as a note-type [Suggestion] the user can approve
 * into Notes. Fully on-device; no network, no LLM. A date gate ([SourceManager.lastAppUsageDigestDate])
 * ensures it runs only once a day even though the scanner ticks every ~30 minutes.
 */
@Singleton
class AppUsageCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sourceManager: SourceManager,
    private val dao: TaskMindDao,
    private val notifier: SuggestionNotifier
) {
    suspend fun generateDailyDigestIfDue() {
        val today = LocalDate.now().toString()
        if (sourceManager.lastAppUsageDigestDate.first() == today) return

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        val zone = ZoneId.systemDefault()
        val yesterday = LocalDate.now().minusDays(1)
        val start = yesterday.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = yesterday.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        // Returns empty if Usage Access isn't granted (or there's no data) — skip silently.
        val raw = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end) ?: emptyList()
        if (raw.isEmpty()) return

        val pm = context.packageManager
        val stats = raw
            .filter { it.totalTimeInForeground > 0 && it.packageName != context.packageName }
            .groupBy { it.packageName }
            .map { (pkg, entries) ->
                val total = entries.sumOf { it.totalTimeInForeground }
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
                AppUsageStat(label, total)
            }

        // Mark done regardless so we don't re-query all day; only insert if there's something to show.
        sourceManager.setLastAppUsageDigestDate(today)
        if (stats.isEmpty()) return

        val dateLabel = yesterday.format(DateTimeFormatter.ofPattern("MMM d"))
        val suggestion = Suggestion(
            source = "App Usage",
            rawSnippet = AppUsageDigest.body(stats),
            extractedTitle = AppUsageDigest.title(dateLabel),
            dueDate = null,
            dueTime = null,
            type = "note",
            confidence = 1.0,
            status = "pending"
        )
        dao.insertSuggestion(suggestion)
        notifier.notifyPending()
    }
}
