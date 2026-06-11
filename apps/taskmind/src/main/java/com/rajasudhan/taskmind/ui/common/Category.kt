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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Shared color category so Inbox, Notes (and beyond) speak the same visual language. */
data class Category(val label: String, val accent: Color, val container: Color)

val OverdueCategory = Category("OVERDUE", Color(0xFFB71C1C), Color(0xFFFFE1E1))
val ReminderCategory = Category("REMINDER", Color(0xFFD32F2F), Color(0xFFFDECEA))
val TodoCategory = Category("TO-DO", Color(0xFFF57C00), Color(0xFFFFF4E5))
val NoteCategory = Category("NOTE", Color(0xFF1976D2), Color(0xFFE8F1FB))

// Card backgrounds are always light pastels, so card text uses fixed dark colors for contrast.
val OnLightCard = Color(0xFF1B1B1B)
val OnLightCardMuted = Color(0xFF5F5F5F)

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

/** Maps an item's type + due date to its color category (overdue reminders/todos escalate). */
fun categoryFor(type: String, dueDate: String?, dueTime: String?): Category = when {
    (type == "reminder" || type == "todo") && isOverdue(dueDate, dueTime) -> OverdueCategory
    type == "reminder" -> ReminderCategory
    type == "todo" -> TodoCategory
    else -> NoteCategory
}

@Composable
fun CategoryBadge(category: Category) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(category.accent)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = category.label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CategoryLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot("Reminder", ReminderCategory.accent)
        LegendDot("To-do", TodoCategory.accent)
        LegendDot("Note", NoteCategory.accent)
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
