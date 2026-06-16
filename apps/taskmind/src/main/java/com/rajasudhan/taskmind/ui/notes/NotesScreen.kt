package com.rajasudhan.taskmind.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.ui.common.*

@Composable
fun NotesScreen(
    onNoteClick: (Int) -> Unit = {},
    viewModel: NotesViewModel = hiltViewModel()
) {
    val notes by viewModel.notes.collectAsState()
    val query by viewModel.query.collectAsState()
    val showCompleted by viewModel.showCompleted.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            label = { Text("Search notes") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !showCompleted,
                onClick = { viewModel.setShowCompleted(false) },
                label = { Text("Active") }
            )
            FilterChip(
                selected = showCompleted,
                onClick = { viewModel.setShowCompleted(true) },
                label = { Text("Completed") }
            )
        }
        Spacer(Modifier.height(8.dp))
        CategoryLegend()
        Spacer(Modifier.height(12.dp))

        if (notes.isEmpty()) {
            val message = when {
                query.isNotBlank() -> "No items match \"$query\"."
                showCompleted -> "Nothing completed yet."
                else -> "No approved items yet.\nApprove suggestions in the Inbox to see them here."
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(notes, key = { it.id }) { note ->
                    NoteRow(
                        note = note,
                        onToggleComplete = { viewModel.setCompleted(note, !note.completed) },
                        onClick = { onNoteClick(note.id) },
                        onDelete = { viewModel.deleteNote(note) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: Note, onToggleComplete: () -> Unit, onClick: () -> Unit, onDelete: () -> Unit) {
    val category = categoryFor(note.type, note.dueDate, note.dueTime)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = category.container()),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            // Colored accent bar on the left edge.
            Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(category.accent()))

            Checkbox(checked = note.completed, onCheckedChange = { onToggleComplete() })

            Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp, horizontal = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(category)
                    if (note.dueDate != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${note.dueDate} ${note.dueTime ?: ""}".trim(),
                            style = MaterialTheme.typography.labelMedium,
                            color = category.accent(),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onCard(),
                    textDecoration = if (note.completed) TextDecoration.LineThrough else null
                )
                Text(
                    text = "from ${note.source}",
                    style = MaterialTheme.typography.labelSmall,
                    color = onCardMuted()
                )
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = onCardMuted())
            }
        }
    }
}
