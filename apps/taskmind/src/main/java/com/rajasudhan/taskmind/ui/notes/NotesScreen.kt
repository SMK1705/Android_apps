package com.rajasudhan.taskmind.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.ui.common.*

@Composable
fun NotesScreen(
    viewModel: NotesViewModel = hiltViewModel()
) {
    val notes by viewModel.notes.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("All Items (${notes.size})", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        CategoryLegend()
        Spacer(Modifier.height(12.dp))

        if (notes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No approved items yet.\nApprove suggestions in the Inbox to see them here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(notes, key = { it.id }) { note ->
                    NoteRow(note = note, onDelete = { viewModel.deleteNote(note) })
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: Note, onDelete: () -> Unit) {
    val category = categoryFor(note.type, note.dueDate, note.dueTime)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = category.container),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Colored accent bar on the left edge.
            Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(category.accent))

            Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(category)
                    if (note.dueDate != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${note.dueDate} ${note.dueTime ?: ""}".trim(),
                            style = MaterialTheme.typography.labelMedium,
                            color = category.accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnLightCard
                )
                Text(
                    text = "from ${note.source}",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnLightCardMuted
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = OnLightCardMuted
                )
            }
        }
    }
}
