package com.rajasudhan.taskmind.data.source.understanding

import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.data.model.Tags
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Pure query execution for Ask TaskMind (#128): filters a note list by the intent's slots (type / tag
 * / due-window / keyword). No Android or LLM deps, so it's fully unit-tested. Windows are date-only
 * (dueDate is `YYYY-MM-DD`); an unparseable or unknown value simply doesn't constrain the result.
 */
object AskQuery {

    /** True if [note] satisfies every slot the intent actually set (a null slot is "don't care"). */
    fun matches(note: Note, intent: AskIntent, today: LocalDate): Boolean {
        if (intent.type != null && note.type != canonicalType(intent.type)) return false
        if (intent.tag != null) {
            val want = Tags.canonical(intent.tag) ?: intent.tag
            if (Tags.decode(note.tags).none { it.equals(want, ignoreCase = true) }) return false
        }
        if (intent.window != null && !inWindow(note.dueDate, intent.window, today)) return false
        if (!intent.keyword.isNullOrBlank() && !containsKeyword(note, intent.keyword)) return false
        return true
    }

    /** Maps a model-supplied type ("task", "tasks", "todos", …) to the app's stored kind, else itself. */
    fun canonicalType(raw: String?): String? = when (raw?.trim()?.lowercase()) {
        null -> null
        "task", "tasks", "todo", "todos", "to-do", "to-dos" -> "todo"
        "reminder", "reminders" -> "reminder"
        "note", "notes" -> "note"
        "waiting_on", "waiting", "waiting on", "waiting-on" -> "waiting_on"
        else -> raw
    }

    fun inWindow(dueDate: String?, window: String, today: LocalDate): Boolean {
        val date = dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return when (window.trim().lowercase()) {
            "today" -> date == today
            "tomorrow" -> date == today.plusDays(1)
            "overdue" -> date != null && date.isBefore(today)
            "upcoming", "future" -> date != null && !date.isBefore(today)
            "this_week", "week", "this week" -> date != null && !date.isBefore(today) && !date.isAfter(today.plusDays(7))
            "this_weekend", "weekend", "this weekend" -> date != null && date in weekend(today)
            else -> true // unknown window → don't filter on it
        }
    }

    /** The current/upcoming Saturday+Sunday (if today is already the weekend, that same weekend). */
    private fun weekend(today: LocalDate): List<LocalDate> {
        val saturday = when (today.dayOfWeek) {
            DayOfWeek.SATURDAY -> today
            DayOfWeek.SUNDAY -> today.minusDays(1)
            else -> today.plusDays((DayOfWeek.SATURDAY.value - today.dayOfWeek.value).toLong())
        }
        return listOf(saturday, saturday.plusDays(1))
    }

    private fun containsKeyword(note: Note, keyword: String): Boolean {
        val k = keyword.trim().lowercase()
        return note.title.lowercase().contains(k) ||
            note.summary.lowercase().contains(k) ||
            note.body.lowercase().contains(k)
    }
}
