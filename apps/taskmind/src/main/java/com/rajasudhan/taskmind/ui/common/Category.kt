package com.rajasudhan.taskmind.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Shared color category so Inbox, Notes (and beyond) speak the same visual language.
 *
 * Each category carries a light **and** a dark pairing. Previously the containers were fixed light
 * pastels with hardcoded dark text, which looked jarring once the rest of the app was in Dark Mode;
 * now [container]/[accent] resolve to the right tone for the active theme.
 */
data class Category(
    val label: String,
    val accentLight: Color,
    val containerLight: Color,
    val accentDark: Color,
    val containerDark: Color
)

// Light pairs keep the original palette. Dark pairs use deep-toned containers with brighter accents
// so the left bar, badges, and colored dates stay legible against them.
val OverdueCategory = Category("OVERDUE", Color(0xFFB71C1C), Color(0xFFFFE1E1), Color(0xFFFF8A80), Color(0xFF3A1414))
val ReminderCategory = Category("REMINDER", Color(0xFFD32F2F), Color(0xFFFDECEA), Color(0xFFFF8A80), Color(0xFF3A1A18))
val TodoCategory = Category("TO-DO", Color(0xFFF57C00), Color(0xFFFFF4E5), Color(0xFFFFB74D), Color(0xFF3A2A12))
val NoteCategory = Category("NOTE", Color(0xFF1976D2), Color(0xFFE8F1FB), Color(0xFF82B1FF), Color(0xFF132A3F))

/**
 * Accent (left bar, badge background, colored date) for the active theme. Defaults to the *applied*
 * theme rather than the system setting, so a user-forced light/dark theme keeps accents in step.
 */
@Composable
fun Category.accent(dark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f): Color =
    if (dark) accentDark else accentLight

// Cards are neutral tonal surfaces; the category color reads only through the accent bar, badge, and
// the colored due-date. So the on-card text colors resolve to the theme's standard surface roles.

/** Primary text/icon color on a card. */
@Composable
fun onCard(): Color = MaterialTheme.colorScheme.onSurface

/** Muted/secondary text/icon color on a card. */
@Composable
fun onCardMuted(): Color = MaterialTheme.colorScheme.onSurfaceVariant

fun isOverdue(dueDate: String?, dueTime: String?): Boolean {
    val date = dueDate ?: return false
    return try {
        val due = if (dueTime != null) {
            LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(dueTime))
        } else {
            LocalDate.parse(date).atTime(23, 59)
        }
        due.isBefore(LocalDateTime.now())
    } catch (e: Exception) {
        false
    }
}

/** For an overdue item: how long ago it was due — "2d" / "yesterday" / "3h" / "5m". Null if not overdue. */
fun overdueLabel(dueDate: String?, dueTime: String?): String? {
    val date = dueDate ?: return null
    return try {
        val due = if (dueTime != null) LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(dueTime))
        else LocalDate.parse(date).atTime(23, 59)
        val mins = java.time.Duration.between(due, LocalDateTime.now()).toMinutes()
        when {
            mins < 1 -> null
            mins < 60 -> "${mins}m"
            mins < 1440 -> "${mins / 60}h"
            mins < 2880 -> "yesterday"
            else -> "${mins / 1440}d"
        }
    } catch (e: Exception) {
        null
    }
}

/** Maps an item's type + due date to its color category (overdue reminders/todos escalate). */
fun categoryFor(type: String, dueDate: String?, dueTime: String?): Category = when {
    (type == "reminder" || type == "todo") && isOverdue(dueDate, dueTime) -> OverdueCategory
    type == "reminder" -> ReminderCategory
    type == "todo" -> TodoCategory
    else -> NoteCategory
}

@Composable
fun CategoryBadge(category: Category) {
    val accent = category.accent()
    // Pick black/white label text by accent luminance: dark accents (light theme) read with white,
    // bright accents (dark theme) read with near-black.
    val textColor = if (accent.luminance() > 0.4f) Color(0xFF1B1B1B) else Color.White
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(accent)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = category.label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CategoryLegend(modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendDot("Reminder", ReminderCategory.accent())
        LegendDot("To-do", TodoCategory.accent())
        LegendDot("Note", NoteCategory.accent())
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(5.dp))
        // Single line, no wrap — a cramped legend must never break a word into "N\not\ne".
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}
