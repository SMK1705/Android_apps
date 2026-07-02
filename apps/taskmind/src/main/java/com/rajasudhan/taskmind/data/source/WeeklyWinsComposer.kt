package com.rajasudhan.taskmind.data.source

/** One completed item that fed into the weekly recap. */
data class WeeklyWin(val title: String, val source: String)

/** A composed weekly recap: a glanceable title and a short multi-line body. */
data class WeeklyWins(val title: String, val body: String)

/**
 * Turns the week's completed items into a short "done list" recap — deliberately streak-free.
 *
 * Streaks are a proven retention hook but churn spikes the day a first streak breaks; a plain
 * count of what got done takes the reward without the cliff. It doubles as a weekly trust report
 * for the capture engine: it names how many wins were auto-caught from a source (SMS, a
 * notification, an email…) rather than typed in by hand — the value TaskMind quietly added.
 *
 * Pure and deterministic so the wording is unit-testable and the worker stays trivial. Returns null
 * for an empty week: a "you did nothing" ping would be noise, and the recap should earn its place.
 */
object WeeklyWinsComposer {

    /**
     * @param wins everything completed in the window, most-recent first. Empty → no recap.
     */
    fun compose(wins: List<WeeklyWin>): WeeklyWins? {
        if (wins.isEmpty()) return null

        val count = wins.size
        // "Caught" = auto-captured from a source, not typed in by hand — the capture engine's value.
        val caught = wins.filter { sourceLabel(it.source) != MANUAL }
        val title = "$count ${plural(count, "win", "wins")} this week 🎉"

        val parts = buildList {
            add("You finished $count ${plural(count, "thing", "things")} this week.")
            if (caught.isNotEmpty()) {
                // Name the channels the caught wins came from (most common first) — the trust report.
                val channels = caught
                    .groupingBy { sourceLabel(it.source) }.eachCount()
                    .entries.sortedByDescending { it.value }
                    .map { it.key }.take(MAX_CHANNELS)
                add("${caught.size} caught from ${humanJoin(channels)} you'd have forgotten.")
            }
            val names = wins.map { it.title.trim() }.filter { it.isNotBlank() }.take(MAX_NAMED)
            if (names.isNotEmpty()) add("Including: ${names.joinToString(", ")}.")
        }

        return WeeklyWins(title = title, body = parts.joinToString("\n"))
    }

    /**
     * Collapses a free-form `source` string to a short channel label, mirroring the app's source
     * icons. Kept here (not the Compose [com.rajasudhan.taskmind.ui.common.sourceVisual]) so the
     * composer stays pure and JVM-testable.
     */
    fun sourceLabel(source: String): String {
        val s = source.lowercase()
        return when {
            "notification" in s -> "a notification"
            "gmail" in s || "email" in s || "mail" in s -> "email"
            "sms" in s || "message" in s || "text" in s -> "SMS"
            "voice" in s || "call" in s || "recording" in s || "audio" in s -> "a voice note"
            "screenshot" in s || "ocr" in s || "image" in s -> "a screenshot"
            "calendar" in s -> "your calendar"
            "manual" in s -> MANUAL
            else -> "a source"
        }
    }

    /** Joins labels as "A", "A and B", or "A, B and C". */
    private fun humanJoin(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        2 -> "${items[0]} and ${items[1]}"
        else -> "${items.dropLast(1).joinToString(", ")} and ${items.last()}"
    }

    private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many

    private const val MANUAL = "manual entry"
    private const val MAX_CHANNELS = 3
    private const val MAX_NAMED = 3
}
