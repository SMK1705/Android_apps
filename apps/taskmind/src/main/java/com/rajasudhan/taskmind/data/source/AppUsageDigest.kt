package com.rajasudhan.taskmind.data.source

/** A single app's foreground time, used to build the daily digest. */
data class AppUsageStat(val label: String, val foregroundMillis: Long)

/**
 * Pure formatting for the daily app-usage digest — no Android dependencies, so it is unit-testable
 * on the plain JVM. [AppUsageCollector] supplies the [AppUsageStat]s from UsageStatsManager.
 */
object AppUsageDigest {

    fun title(dateLabel: String): String = "Screen time — $dateLabel"

    /** Total screen time plus the top [topN] apps by foreground time, ranked descending. */
    fun body(stats: List<AppUsageStat>, topN: Int = 5): String {
        val ranked = stats.filter { it.foregroundMillis > 0 }.sortedByDescending { it.foregroundMillis }
        if (ranked.isEmpty()) return "No app usage was recorded."
        val total = ranked.sumOf { it.foregroundMillis }
        return buildString {
            append("Total screen time: ${humanDuration(total)}.\n")
            append("Top apps:")
            ranked.take(topN).forEach { append("\n• ${it.label} — ${humanDuration(it.foregroundMillis)}") }
        }
    }

    /** Human-friendly duration: "1h 30m", "45m", "0m". */
    fun humanDuration(millis: Long): String {
        val totalMinutes = millis / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
