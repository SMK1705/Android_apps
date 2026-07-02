package com.rajasudhan.taskmind.data.source.understanding

/**
 * Pure, dependency-free heuristics used by [UnderstandingPipeline] to filter and sanitize what the
 * LLM produces. Kept separate (no Android/Room/Moshi deps) so the safety-critical logic — the stuff
 * that decides what becomes a suggestion — is unit-testable on the plain JVM.
 *
 * Behavior here is intentionally conservative: the approval gate is the real safety net, but these
 * heuristics keep obvious junk (OTPs, promos) and malformed values out of the Inbox in the first place.
 */
object ExtractionHeuristics {

    /** Only keep suggestions the model is reasonably sure about. */
    const val MIN_CONFIDENCE = 0.6

    /** A valid `due_date` looks like `2026-06-11`; anything else is dropped. */
    val DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")

    /** A valid `due_time` looks like `9:30` / `14:05`; a datetime stuffed into due_time is dropped. */
    val TIME_REGEX = Regex("""\d{1,2}:\d{2}""")

    /** One-time codes, marketing, and opt-out boilerplate are never action items. */
    val NOISE_PATTERNS = listOf(
        Regex("(verification|one[- ]?time|security|login|otp)\\b.{0,20}code", RegexOption.IGNORE_CASE),
        Regex("\\bcode\\b.{0,20}\\b\\d{4,8}\\b", RegexOption.IGNORE_CASE),
        Regex("\\b\\d{4,8}\\b.{0,20}\\bcode\\b", RegexOption.IGNORE_CASE),
        Regex("do not share", RegexOption.IGNORE_CASE),
        Regex("reply stop|opt[- ]?out|unsubscribe", RegexOption.IGNORE_CASE),
        Regex("% off|\\bsale\\b|\\bdeal(s)?\\b|coupon|promo|cashback", RegexOption.IGNORE_CASE)
    )

    /**
     * Scheduling / invite cues. A meeting invite or calendar invitation — in an email or a
     * LinkedIn-style notification — routinely carries an "unsubscribe"/opt-out footer, which the
     * marketing patterns above would otherwise treat as noise and drop before the model ever sees it.
     * When one of these is present we let the text through to the LLM, which makes the real call.
     */
    val ACTIONABLE_HINTS = Regex(
        "\\b(invit\\w+|meeting|calendar|rsvp|appointment|interview|webinar|schedul\\w+)\\b|" +
            "when\\s*:|where\\s*:|zoom\\.us|meet\\.google|teams\\.microsoft",
        RegexOption.IGNORE_CASE
    )

    /** True if the raw text is obvious non-actionable noise and should skip the LLM entirely. */
    fun isLikelyNoise(text: String): Boolean =
        !ACTIONABLE_HINTS.containsMatchIn(text) && NOISE_PATTERNS.any { it.containsMatchIn(text) }

    /** Returns the date only if it is a well-formed `yyyy-MM-dd`, else null. */
    fun sanitizeDate(raw: String?): String? = raw?.takeIf { it.matches(DATE_REGEX) }

    /** Returns the time only if it is a well-formed `H:mm`/`HH:mm`, else null. */
    fun sanitizeTime(raw: String?): String? = raw?.takeIf { it.matches(TIME_REGEX) }

    /** The repeat values the app understands (must match [com.rajasudhan.taskmind.data.source.RecurrenceUtil]). */
    private val RECURRENCE_VALUES = setOf("daily", "weekly", "monthly")

    /** Normalizes a model-supplied recurrence to `daily`/`weekly`/`monthly`, else null (no repeat). */
    fun sanitizeRecurrence(raw: String?): String? =
        raw?.trim()?.lowercase()?.takeIf { it in RECURRENCE_VALUES }

    /**
     * Clamps a model-supplied priority to the two values extraction is allowed to produce: only an
     * explicit `high` survives; everything else — null, blank, "normal", "low", or anything unknown —
     * floors to `normal`. This is the safety net for the "never low from text, don't over-flag high"
     * rule, independent of whether the prompt or schema behaved. Never returns null (the column is
     * NOT NULL, default `normal`).
     */
    fun sanitizePriority(raw: String?): String =
        if (raw?.trim()?.lowercase() == "high") "high" else "normal"

    /** Strips markdown code fences the LLM sometimes wraps JSON in, despite instructions. */
    fun stripJsonFences(json: String): String =
        json.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

    /** A model item is worth keeping only if it has a title and clears the confidence bar. */
    fun isAcceptable(item: LlmItem): Boolean =
        item.title.isNotBlank() && item.confidence >= MIN_CONFIDENCE

    /**
     * Duplicate check by (title, dueDate) against already-known items. [existing] is a list of
     * (title, dueDate) pairs drawn from pending suggestions and approved notes.
     */
    fun isDuplicate(title: String, dueDate: String?, existing: List<Pair<String, String?>>): Boolean =
        existing.any { it.first == title && it.second == dueDate }
}
