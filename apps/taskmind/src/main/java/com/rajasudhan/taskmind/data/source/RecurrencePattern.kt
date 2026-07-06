package com.rajasudhan.taskmind.data.source

import com.rajasudhan.taskmind.data.source.understanding.NearDuplicate
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Auto-detected recurrence (#124 Part B). A deterministic, pure pattern-miner over the user's captured
 * history: when the same task keeps showing up on a steady weekly/monthly cadence (e.g. "pay rent"
 * three month-starts running), it emits a [Detected] pattern that the worker turns into a "repeat
 * automatically?" Inbox offer. No LLM, no Android deps — so it's precise, explainable, and unit-tested.
 *
 * Deliberately CONSERVATIVE: a false positive is a card the user taps away, but a stream of them erodes
 * trust, so it only fires on a tight, well-supported cadence. Daily is intentionally NOT detected — a
 * daily habit re-captured by hand is rare, and 1-day gaps are too noisy to tell a real daily pattern
 * from a run of unrelated same-topic captures.
 */
object RecurrencePattern {

    /** One historical sighting of a task: its [title] and the day it fell on (its due date, else capture). */
    data class Occurrence(val title: String, val date: LocalDate)

    data class Detected(
        val title: String,        // a representative title for the cluster (its earliest sighting — see [detectOne])
        val recurrence: String,   // "weekly" | "monthly"
        val occurrences: Int,
        val nextDate: LocalDate,  // first occurrence on/after today, to seed the suggestion with
        val anchorDay: Int?,      // day-of-month for a monthly repeat (keeps it off the 28th after Feb)
        val confidence: Double,
    )

    /** Need at least this many sightings before a cadence is trustworthy. */
    const val MIN_OCCURRENCES = 3

    private data class Period(val recurrence: String, val lo: Long, val hi: Long)
    // Gap tolerances in days. Weekly: 5–10 (a few days' slack around 7). Monthly: 25–35 (month length
    // 28–31 plus slack). Non-overlapping, so a median gap maps to at most one cadence.
    private val PERIODS = listOf(
        Period("weekly", 5, 10),
        Period("monthly", 25, 35),
    )

    /** Stable clustering / backoff key for a title: its sorted content tokens ("pay the rent" → "pay rent"). */
    fun key(title: String): String = NearDuplicate.tokens(title).sorted().joinToString(" ")

    /**
     * Detect steady weekly/monthly patterns in [history], as of [today]. Groups sightings by content-word
     * similarity, then reports each cluster whose sorted, de-duplicated dates fall on a consistent cadence.
     */
    fun detect(history: List<Occurrence>, today: LocalDate): List<Detected> =
        cluster(history).mapNotNull { members -> detectOne(members, today) }

    /** Greedy single-link clustering by [NearDuplicate.tokenOverlap] at/above its threshold. */
    private fun cluster(history: List<Occurrence>): List<List<Occurrence>> {
        val clusters = mutableListOf<MutableList<Occurrence>>()
        for (occ in history) {
            if (key(occ.title).isBlank()) continue // no content tokens → unclusterable
            val hit = clusters.firstOrNull { c ->
                NearDuplicate.tokenOverlap(occ.title, c.first().title) >= NearDuplicate.TOKEN_OVERLAP
            }
            if (hit != null) hit.add(occ) else clusters.add(mutableListOf(occ))
        }
        return clusters
    }

    private fun detectOne(members: List<Occurrence>, today: LocalDate): Detected? {
        // One sighting per day — same-day re-captures mustn't look like a 0-day cadence.
        val dates = members.map { it.date }.distinct().sorted()
        if (dates.size < MIN_OCCURRENCES) return null

        val gaps = dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }
        val median = gaps.sorted()[gaps.size / 2]
        val period = PERIODS.firstOrNull { median in it.lo..it.hi } ?: return null
        // Precision guard: most gaps must sit inside the cadence window, not just the median.
        val consistent = gaps.count { it in period.lo..period.hi }
        val consistency = consistent.toDouble() / gaps.size
        if (consistency < 0.6) return null

        val recurrence = period.recurrence
        val last = dates.last()
        val anchor = if (recurrence == "monthly") last.dayOfMonth else null

        // The next occurrence on/after today: step from the last sighting until it's no longer in the past.
        var next = RecurrenceUtil.next(last.toString(), recurrence, anchor)?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        } ?: return null
        var guard = 0
        while (next.isBefore(today) && guard++ < GUARD) {
            next = RecurrenceUtil.next(next.toString(), recurrence, anchor)?.let {
                runCatching { LocalDate.parse(it) }.getOrNull()
            } ?: break
        }

        // Confidence: a base offer, nudged up by extra sightings and tight consistency — capped well
        // below 1, since it's a suggestion, not a certainty.
        val confidence = (0.6 + 0.05 * (dates.size - MIN_OCCURRENCES).coerceAtMost(4) + 0.1 * (consistency - 0.6))
            .coerceIn(0.6, 0.85)

        // Label the offer with the cluster's EARLIEST sighting (members are createdDate-DESC, so
        // members.last() is the oldest). That title is stable across worker runs — a later re-capture of
        // the same task, however it's worded, is always newer and joins at the front — so the backoff key
        // ([key]) a dismissal records stays put and the detector won't re-offer a pattern the user nixed.
        return Detected(members.last().title, recurrence, dates.size, next, anchor, confidence)
    }

    // Bound the roll-forward loop against a pathological last-sighting far in the past.
    private const val GUARD = 1200
}
