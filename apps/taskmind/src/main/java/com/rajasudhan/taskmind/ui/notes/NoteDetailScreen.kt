package com.rajasudhan.taskmind.ui.notes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rajasudhan.taskmind.ui.common.CategoryBadge
import com.rajasudhan.taskmind.ui.common.categoryFor

/**
 * Full view of a single approved item: heading, summary, the complete body, and metadata.
 * Reached by tapping a row in [NotesScreen]. [onBack] is invoked after a delete to pop the screen.
 */
@Composable
fun NoteDetailScreen(
    onBack: () -> Unit,
    viewModel: NoteDetailViewModel = hiltViewModel()
) {
    val note by viewModel.note.collectAsState()
    val n = note
    var deleting by remember { mutableStateOf(false) }

    if (n == null) {
        // A delete makes the note flow emit null; render nothing until we pop, rather than
        // flashing a "not found" error in the brief window before navigation completes.
        if (!deleting) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Note not found.", style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }

    val category = categoryFor(n.type, n.dueDate, n.dueTime)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(category)
            if (n.dueDate != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${n.dueDate} ${n.dueTime ?: ""}".trim(),
                    style = MaterialTheme.typography.labelLarge,
                    color = category.accent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = n.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "from ${n.source}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = n.completed, onCheckedChange = { viewModel.setCompleted(it) })
            Text(
                text = if (n.completed) "Completed" else "Mark complete",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (n.summary.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = n.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Checklist: shown for to-dos whose content is list-like; ticks persist to the note.
        val checklistItems = if (!n.checklist.isNullOrBlank()) Checklist.decode(n.checklist!!)
            else if (n.type == "todo") Checklist.derive(n.summary) else emptyList()
        if (checklistItems.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Checklist", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            checklistItems.forEachIndexed { i, item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = item.checked,
                        onCheckedChange = { viewModel.updateChecklist(Checklist.toggleEncoded(checklistItems, i)) }
                    )
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (item.checked) TextDecoration.LineThrough else null
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Details",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = linkifyNoteBody(n.body, MaterialTheme.colorScheme.primary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = { deleting = true; viewModel.deleteNote(onBack) }) {
            Icon(Icons.Default.DeleteOutline, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Delete")
        }
    }
}
