package com.rajasudhan.taskmind.ui.inbox

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rajasudhan.taskmind.data.model.Suggestion
import com.rajasudhan.taskmind.ui.common.*

import androidx.compose.material.icons.filled.Refresh

private val ApproveGreen = Color(0xFF2E7D32)
private val RejectRed = Color(0xFFD32F2F)

@Composable
fun InboxScreen(
    viewModel: InboxViewModel = hiltViewModel()
) {
    val suggestions by viewModel.pendingSuggestions.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var showApproveAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.refreshRecentData() }) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Recent Data")
                }
            }
        }
    ) { paddingValues ->
        if (suggestions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text(text = "No pending suggestions. All caught up!", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${suggestions.size} pending",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.rejectAll() }) {
                            Text("Reject all", color = RejectRed)
                        }
                        Button(onClick = { showApproveAllDialog = true }) {
                            Text("Approve all")
                        }
                    }
                }
                items(suggestions) { suggestion ->
                    SuggestionCard(
                        suggestion = suggestion,
                        onApprove = { viewModel.approveSuggestion(it) },
                        onReject = { viewModel.rejectSuggestion(it) },
                        onEdit = { viewModel.updateSuggestion(it) }
                    )
                }
            }
        }

        if (showApproveAllDialog) {
            AlertDialog(
                onDismissRequest = { showApproveAllDialog = false },
                title = { Text("Approve all ${suggestions.size}?") },
                text = { Text("This saves every pending suggestion and schedules any reminders/calendar events.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.approveAll()
                        showApproveAllDialog = false
                    }) { Text("Approve all") }
                },
                dismissButton = {
                    TextButton(onClick = { showApproveAllDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun SuggestionCard(
    suggestion: Suggestion,
    onApprove: (Suggestion) -> Unit,
    onReject: (Suggestion) -> Unit,
    onEdit: (Suggestion) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editedTitle by remember { mutableStateOf(suggestion.extractedTitle) }
    var editedDueDate by remember { mutableStateOf(suggestion.dueDate ?: "") }
    var editedDueTime by remember { mutableStateOf(suggestion.dueTime ?: "") }

    val category = categoryFor(suggestion.type, suggestion.dueDate, suggestion.dueTime)
    val darkFieldStyle = TextStyle(color = OnLightCard)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = category.container),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Colored accent bar on the left edge.
            Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(category.accent))

            Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                // Source Context
                Text(
                    text = "From ${suggestion.source}",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnLightCardMuted
                )
                Text(
                    text = "\"${suggestion.rawSnippet}\"",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = OnLightCard,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                HorizontalDivider(
                    color = OnLightCardMuted.copy(alpha = 0.25f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (isEditing) {
                    OutlinedTextField(
                        value = editedTitle,
                        onValueChange = { editedTitle = it },
                        label = { Text("Title") },
                        textStyle = darkFieldStyle,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editedDueDate,
                            onValueChange = { editedDueDate = it },
                            label = { Text("Date (YYYY-MM-DD)") },
                            textStyle = darkFieldStyle,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editedDueTime,
                            onValueChange = { editedDueTime = it },
                            label = { Text("Time (HH:MM)") },
                            textStyle = darkFieldStyle,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { isEditing = false }) {
                            Text("Cancel", color = OnLightCardMuted)
                        }
                        Button(onClick = {
                            val updated = suggestion.copy(
                                extractedTitle = editedTitle,
                                dueDate = editedDueDate.ifBlank { null },
                                dueTime = editedDueTime.ifBlank { null }
                            )
                            onEdit(updated)
                            isEditing = false
                        }) {
                            Text("Save")
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(category)
                        if (suggestion.dueDate != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${suggestion.dueDate} ${suggestion.dueTime ?: ""}".trim(),
                                style = MaterialTheme.typography.labelMedium,
                                color = category.accent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = suggestion.extractedTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnLightCard
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { onReject(suggestion) }) {
                            Icon(Icons.Default.Close, contentDescription = "Reject", tint = RejectRed)
                        }
                        Row {
                            IconButton(onClick = { isEditing = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = OnLightCardMuted)
                            }
                            IconButton(onClick = { onApprove(suggestion) }) {
                                Icon(Icons.Default.Check, contentDescription = "Approve", tint = ApproveGreen)
                            }
                        }
                    }
                }
            }
        }
    }
}
