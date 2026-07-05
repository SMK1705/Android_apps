package com.rajasudhan.taskmind.data.source

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Pure recurrence maths for repeating reminders. No Android deps, so it's unit-tested. */
object RecurrenceUtil {
    val OPTIONS = listOf("None", "Daily", "Weekly", "Monthly")

    // Hard cap on how many periods we'll skip forward; on hitting it we give up (return null) rather
    // than spin forever or arm a past slot. 100k daily steps is ~270 years — unreachable for any real
    // missed-reminder gap.
    private const val MAX_STEPS = 100_000

    /**
     * The next due date (yyyy-MM-dd) after [dueDate] for [recurrence], or null if not repeating.
     *
     * For "monthly" the day-of-month is taken from [anchorDay] (the intended day, e.g. the 31st),
     * clamped to the target month's length — NOT stepped from [dueDate]'s day, which may itself already
     * have been clamped by a short month. Without an anchor it falls back to [dueDate]'s day (the legacy
     * behaviour that drifts a 29–31 reminder permanently down to the 28th after February).
     */
    fun next(dueDate: String, recurrence: String?, anchorDay: Int? = null): String? {
        val base = runCatching { LocalDate.parse(dueDate) }.getOrNull() ?: return null
        val next = when (recurrence?.lowercase()) {
            "daily" -> base.plusDays(1)
            "weekly" -> base.plusWeeks(1)
            "monthly" -> {
                val month = base.plusMonths(1)
                month.withDayOfMonth((anchorDay ?: base.dayOfMonth).coerceIn(1, month.lengthOfMonth()))
            }
            else -> return null
        }
        return next.toString()
    }

    /** The day-of-month (1–31) of a "yyyy-MM-dd" date — the monthly [next] anchor; null if absent/malformed. */
    fun dayOfMonth(dueDate: String?): Int? =
        runCatching { LocalDate.parse(dueDate).dayOfMonth }.getOrNull()

    /**
     * The first occurrence on/after [fromDate] (at [time]) that is strictly later than [now], stepping
     * by [recurrence]. Returns [fromDate] unchanged when it's already in the future, advances period by
     * period when it's in the past, and returns null if [recurrence] isn't a known repeat or the inputs
     * don't parse.
     *
     * This is what stops a recurring reminder from dying when its alarm is delivered late (device
     * asleep/off) or re-armed after a reboot: [AlarmScheduler.schedule] silently drops any instant in
     * the past, so the caller must hand it a future occurrence, not a stale one.
     */
    fun firstFutureOccurrence(fromDate: String, time: String?, recurrence: String?, now: LocalDateTime, anchorDay: Int? = null): String? {
        // Reject unknown/non-repeating recurrence and unparseable dates up front.
        if (next(fromDate, recurrence, anchorDay) == null) return null
        var date = runCatching { LocalDate.parse(fromDate) }.getOrNull() ?: return null
        val at = parseTime(time) ?: LocalTime.MIN
        var steps = 0
        while (!LocalDateTime.of(date, at).isAfter(now)) {
            if (steps++ >= MAX_STEPS) return null // pathological clock; give up rather than arm a past slot
            val advanced = next(date.toString(), recurrence, anchorDay) ?: return null
            date = LocalDate.parse(advanced)
        }
        return date.toString()
    }

    /**
     * Parses a stored due time ("9:30", "09:30", "14:05") into a [LocalTime]; null if absent or
     * malformed. Tolerant of single-digit hours, so callers that arm alarms / calendar events agree
     * with [firstFutureOccurrence] on which times are valid (a strict HH parser would silently reject
     * "9:30" and drop the alarm). Public so [AlarmScheduler] and [SuggestionApprover] share it.
     */
    fun parseTime(time: String?): LocalTime? {
        val parts = time?.trim()?.split(":") ?: return null
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return runCatching { LocalTime.of(h, m) }.getOrNull()
    }
}
