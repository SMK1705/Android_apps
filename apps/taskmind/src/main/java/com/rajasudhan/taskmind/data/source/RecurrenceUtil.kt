package com.rajasudhan.taskmind.data.source

import java.time.LocalDate

/** Pure recurrence maths for repeating reminders. No Android deps, so it's unit-tested. */
object RecurrenceUtil {
    val OPTIONS = listOf("None", "Daily", "Weekly", "Monthly")

    /** The next due date (yyyy-MM-dd) after [dueDate] for [recurrence], or null if not repeating. */
    fun next(dueDate: String, recurrence: String?): String? {
        val base = runCatching { LocalDate.parse(dueDate) }.getOrNull() ?: return null
        val next = when (recurrence?.lowercase()) {
            "daily" -> base.plusDays(1)
            "weekly" -> base.plusWeeks(1)
            "monthly" -> base.plusMonths(1)
            else -> return null
        }
        return next.toString()
    }
}
