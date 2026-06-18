package com.rajasudhan.taskmind.ui.notes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
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
    // null = first load not finished (skeleton); empty list = nothing matches (empty state).
    val notes by viewModel.notes.collectAsState()
    val query by viewModel.query.collectAsState()
    val showCompleted by viewModel.showCompleted.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Search notes") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
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
            Spacer(Modifier.height(12.dp))
            CategoryLegend()
            Spacer(Modifier.height(8.dp))
        }

        val current = notes
        when {
            current == null -> SkeletonList(modifier = Modifier.weight(1f))
            current.isEmpty() -> {
                val (icon, title, subtitle) = when {
                    query.isNotBlank() -> Triple(Icons.Default.SearchOff, "No matches", "Nothing matches “$query”.")
                    showCompleted -> Triple(Icons.Default.DoneAll, "Nothing completed yet", "Items you tick off will collect here.")
                    else -> Triple(Icons.Default.Description, "No items yet", "Approve suggestions in the Inbox and they'll land here.")
                }
                EmptyState(modifier = Modifier.weight(1f), icon = icon, title = title, subtitle = subtitle)
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(current, key = { it.id }) { note ->
                    NoteRow(
                        modifier = Modifier.animateItem(),
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
private fun NoteRow(
    note: Note,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val category = categoryFor(note.type, note.dueDate, note.dueTime)
    TmCard(modifier = modifier, accent = category.accent(), onClick = onClick) {
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
