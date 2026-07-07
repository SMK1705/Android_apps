package com.rajasudhan.taskmind.data.source

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A schedule parsed out of free text. [spans] are the character ranges in the source that produced it
 * (so the capture UI can highlight them). Every field is independent and any may be null.
 */
data class ParsedSchedule(
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val recurrence: String? = null, // "daily" | "weekly" | "monthly"
    // #124: a "!" on the repeat phrase ("daily!", "every! week") — reschedule from when the task is
    // finished, not its due date. Only ever set alongside [recurrence].
    val repeatFromCompletion: Boolean = false,
    val spans: List<IntRange> = emptyList(),
) {
    val isEmpty: Boolean get() = date == null && time == null && recurrence == null

    /** yyyy-MM-dd — the Note/Suggestion dueDate format. */
    fun dueDate(): String? = date?.toString()

    /** HH:mm (24-hour) — the Note/Suggestion dueTime format. */
    fun dueTime(): String? = time?.let { "%02d:%02d".format(it.hour, it.minute) }
}

/**
 * Deterministic, on-device natural-language date/time/recurrence parser (#116).
 *
 * Rule-based (no LLM): predictable, instant, offline, and free — the right trade-off for dates, where
 * "tomorrow" must ALWAYS mean tomorrow. Relative phrases are resolved against an injected [now], so the
 * whole thing is a pure function and fully unit-testable. Case-insensitive.
 *
 * Because the parse OVERRIDES the LLM downstream, it is deliberately CONSERVATIVE — it favours a miss
 * (null, so the model still gets a shot) over a false positive that would stamp a bogus schedule. So a
 * bare `2/3` (a fraction) is NOT a date (a numeric date must carry a year), and "at 3 people" is not a
 * time. Matching is done case-insensitively over the ORIGINAL text so [spans] align 1:1 with the field.
 */
object NaturalDate {

    private val WEEKDAYS = mapOf(
        "monday" to 1, "mon" to 1, "tuesday" to 2, "tue" to 2, "tues" to 2, "wednesday" to 3, "wed" to 3,
        "thursday" to 4, "thu" to 4, "thurs" to 4, "friday" to 5, "fri" to 5, "saturday" to 6, "sat" to 6,
        "sunday" to 7, "sun" to 7,
    )
    private val MONTHS = mapOf(
        "january" to 1, "jan" to 1, "february" to 2, "feb" to 2, "march" to 3, "mar" to 3, "april" to 4,
        "apr" to 4, "may" to 5, "june" to 6, "jun" to 6, "july" to 7, "jul" to 7, "august" to 8, "aug" to 8,
        "september" to 9, "sep" to 9, "sept" to 9, "october" to 10, "oct" to 10, "november" to 11, "nov" to 11,
        "december" to 12, "dec" to 12,
    )
    private val WD = WEEKDAYS.keys.joinToString("|")
    private val MO = MONTHS.keys.joinToString("|")
    private val I = setOf(RegexOption.IGNORE_CASE)

    // Recurrence: "every day/week/month", "daily/weekly/monthly", "every <weekday>". A "!" on the phrase
    // ("daily!", "every! week") marks it completion-based (#124): reschedule from finish. The trailing
    // (?:!|\b) keeps the word boundary for the plain form while also accepting the bang.
    private val RE_RECURRENCE = Regex("""\b(daily|weekly|monthly|every!?\s+(day|week|month|$WD))(?:!|\b)""", I)
    // Relative offsets. Hours/minutes also pin the time.
    private val RE_IN_N = Regex("""\bin\s+(\d{1,3})\s+(day|days|week|weeks|month|months|hour|hours|hr|hrs|minute|minutes|min|mins)\b""", I)
    private val RE_NAMED_DAY = Regex("""\b(today|tonight|tomorrow|tmrw|next\s+week|next\s+month)\b""", I)
    private val RE_WEEKDAY = Regex("""\b(next|this)\s+($WD)\b|\b($WD)\b""", I)
    private val RE_MONTH_DAY = Regex("""\b($MO)\.?\s+(\d{1,2})(?:st|nd|rd|th)?\b""", I)
    private val RE_DAY_MONTH = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+($MO)\b""", I)
    // A NUMERIC date must carry a year — otherwise a fraction/quantity like "2/3 cup" or "1/2 tank" would
    // be misread as a date and (because we override the LLM) stamp a bogus reminder. Bare "6/20" is left
    // to the LLM, which still resolves it.
    private val RE_NUMERIC = Regex("""\b(\d{1,2})/(\d{1,2})/(\d{2,4})\b""")
    private val RE_TONIGHT = Regex("""\btonight\b""", I)
    // Times.
    // Accept the periarded forms too ("3 p.m.", "9 a.m.") — speech-to-text (e.g. wrist capture, #216) emits
    // "p.m.", which the old `(am|pm)` missed, so "3:00 p.m." fell through to RE_24H and stamped 03:00 (#235).
    // Group 3 is the "a"/"p" indicator; the trailing (?![a-z]) keeps "meet 3 amber" from reading as 3am.
    private val RE_MERIDIEM = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*([ap])\.?m\.?(?![a-z])""", I)
    private val RE_24H = Regex("""\b(\d{1,2}):(\d{2})\b""")
    // "at N" is a time only when it's NOT followed by a word — "at 3 people"/"stay at 5 star" are counts,
    // not 3pm/5pm. (am/pm and HH:mm are already handled above.)
    private val RE_AT_N = Regex("""\bat\s+(\d{1,2})\b(?!\s*\p{L})""", I)
    private val RE_NAMED_TIME = Regex("""\b(noon|midday|midnight|morning|afternoon|evening|tonight)\b""", I)

    fun parse(text: String, now: LocalDateTime): ParsedSchedule {
        val today = now.toLocalDate()
        val spans = mutableListOf<IntRange>()
        var date: LocalDate? = null
        var time: LocalTime? = null
        var recurrence: String? = null
        var repeatFromCompletion = false

        RE_RECURRENCE.find(text)?.let { m ->
            val whole = m.groupValues[1].lowercase()       // "daily"|"weekly"|"monthly"|"every …"
            val unit = m.groupValues[2].lowercase()        // "day"|"week"|"month"|<weekday>|""
            // Classify off the capture group, NOT a substring — weekday names ("monday") contain "day".
            recurrence = when {
                unit in WEEKDAYS -> "weekly"
                whole == "daily" || unit == "day" -> "daily"
                whole == "weekly" || unit == "week" -> "weekly"
                whole == "monthly" || unit == "month" -> "monthly"
                else -> "weekly"
            }
            // A "!" anywhere in the matched phrase ("daily!", "every! week") = reschedule from finish (#124).
            repeatFromCompletion = m.value.contains('!')
            if (unit in WEEKDAYS && date == null) date = nextWeekday(today, WEEKDAYS.getValue(unit), next = false)
            spans += m.range
        }

        // "in N hours/minutes" pins both date and time from now; "in N days/weeks/months" is date-only.
        RE_IN_N.find(text)?.let { m ->
            val n = m.groupValues[1].toLong()
            when (m.groupValues[2].lowercase().trimEnd('s')) {
                "day" -> date = today.plusDays(n)
                "week" -> date = today.plusWeeks(n)
                "month" -> date = today.plusMonths(n)
                "hour", "hr" -> now.plusHours(n).let { date = it.toLocalDate(); time = it.toLocalTime().withSecond(0).withNano(0) }
                "minute", "min" -> now.plusMinutes(n).let { date = it.toLocalDate(); time = it.toLocalTime().withSecond(0).withNano(0) }
            }
            spans += m.range
        }

        if (date == null) date = matchDate(text, today, spans)

        time = time ?: matchTime(text, spans)
        if (date == null && RE_TONIGHT.containsMatchIn(text)) date = today // "tonight" pins today

        return ParsedSchedule(date, time, recurrence, repeatFromCompletion, spans.distinct().sortedBy { it.first })
    }

    private fun matchDate(text: String, today: LocalDate, spans: MutableList<IntRange>): LocalDate? {
        RE_NAMED_DAY.find(text)?.let { m ->
            spans += m.range
            val v = m.value.lowercase()
            return when {
                v.startsWith("today") || v.startsWith("tonight") -> today
                v.startsWith("tomorrow") || v.startsWith("tmrw") -> today.plusDays(1)
                v.startsWith("next week") -> today.plusWeeks(1)
                v.startsWith("next month") -> today.plusMonths(1)
                else -> today
            }
        }
        RE_WEEKDAY.find(text)?.let { m ->
            val prefix = m.groupValues[1].lowercase()          // "next" | "this" | ""
            val name = m.groupValues[2].ifBlank { m.groupValues[3] }.lowercase()
            val wd = WEEKDAYS[name] ?: return@let
            spans += m.range
            return nextWeekday(today, wd, next = prefix == "next")
        }
        RE_MONTH_DAY.find(text)?.let { m ->
            val month = MONTHS[m.groupValues[1].lowercase()] ?: return@let
            spans += m.range
            return onOrAfterToday(today, month, m.groupValues[2].toInt())
        }
        RE_DAY_MONTH.find(text)?.let { m ->
            val month = MONTHS[m.groupValues[2].lowercase()] ?: return@let
            spans += m.range
            return onOrAfterToday(today, month, m.groupValues[1].toInt())
        }
        RE_NUMERIC.find(text)?.let { m ->
            val month = m.groupValues[1].toInt()
            val day = m.groupValues[2].toInt()
            val year = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
            spans += m.range
            return runCatching { LocalDate.of(year, month, day) }.getOrNull()
        }
        return null
    }

    private fun matchTime(text: String, spans: MutableList<IntRange>): LocalTime? {
        RE_MERIDIEM.find(text)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toIntOrNull() ?: 0
            val pm = m.groupValues[3].equals("p", ignoreCase = true)
            if (h in 1..12 && min in 0..59) {
                spans += m.range
                val hour = when { h == 12 && !pm -> 0; h == 12 && pm -> 12; pm -> h + 12; else -> h }
                return LocalTime.of(hour, min)
            }
        }
        RE_24H.find(text)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            if (h in 0..23 && min in 0..59) { spans += m.range; return LocalTime.of(h, min) }
        }
        RE_NAMED_TIME.find(text)?.let { m ->
            spans += m.range
            return when (m.value.lowercase()) {
                "noon", "midday" -> LocalTime.NOON
                "midnight" -> LocalTime.MIDNIGHT
                "morning" -> LocalTime.of(9, 0)
                "afternoon" -> LocalTime.of(15, 0)
                "evening", "tonight" -> LocalTime.of(20, 0)
                else -> return@let
            }
        }
        RE_AT_N.find(text)?.let { m ->
            val h = m.groupValues[1].toInt()
            spans += m.range
            // Bare "at 5" is ambiguous; assume PM for 1–7 (the usual task hours), else the hour as given.
            return when (h) { in 1..7 -> LocalTime.of(h + 12, 0); in 8..23 -> LocalTime.of(h, 0); 0 -> LocalTime.MIDNIGHT; else -> return@let }
        }
        return null
    }

    // Connective words that dangle at the title's edges once the date phrase is cut out ("call mom at" → "call mom").
    private val TRIM_WORDS = setOf("at", "on", "by", "for", "in", "the", "this", "next", "every", "starting", "a")

    /**
     * The input with the parsed schedule phrases ([spans]) removed and tidied — a clean task title, used
     * when the deterministic parse must stand up a reminder on its own (extractor unavailable). Pure.
     */
    fun stripSchedule(text: String, spans: List<IntRange>): String {
        if (spans.isEmpty()) return text.trim()
        val sb = StringBuilder()
        var i = 0
        for (r in spans.sortedBy { it.first }) {
            val start = r.first.coerceIn(0, text.length)
            val end = (r.last + 1).coerceIn(start, text.length)
            if (start > i) sb.append(text, i, start)
            if (end > i) i = end
        }
        if (i < text.length) sb.append(text, i, text.length)
        val words = sb.toString().split(Regex("\\s+")).filter { it.isNotBlank() }
            .dropLastWhile { it.trim(',', '.', '!').lowercase() in TRIM_WORDS }
            .dropWhile { it.trim(',', '.', '!').lowercase() in TRIM_WORDS }
        return words.joinToString(" ").trim().trimEnd(',', '.', '-', ' ')
    }

    /** The [wd] (1=Mon…7=Sun) occurring this week if still ahead (today allowed), else next week; [next] adds a week. */
    private fun nextWeekday(today: LocalDate, wd: Int, next: Boolean): LocalDate {
        val delta = ((wd - today.dayOfWeek.value) % 7 + 7) % 7 // 0 = today
        return today.plusDays((delta + if (next) 7 else 0).toLong())
    }

    /** [month]/[day] this year, or next year if it's already past — a captured date is always in the future. */
    private fun onOrAfterToday(today: LocalDate, month: Int, day: Int): LocalDate? = runCatching {
        val thisYear = LocalDate.of(today.year, month, day)
        if (thisYear.isBefore(today)) LocalDate.of(today.year + 1, month, day) else thisYear
    }.getOrNull()
}
