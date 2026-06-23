package com.rajasudhan.taskmind.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.ui.bold.*
import com.rajasudhan.taskmind.ui.common.SkeletonList
import com.rajasudhan.taskmind.ui.common.sourceVisual
import com.rajasudhan.taskmind.ui.theme.BoldOnAccent
import com.rajasudhan.taskmind.ui.theme.BoldTheme
import com.rajasudhan.taskmind.ui.theme.BoldType
import com.rajasudhan.taskmind.ui.theme.ShapeField

@Composable
fun NotesScreen(
    onNoteClick: (Int) -> Unit = {},
    viewModel: NotesViewModel = hiltViewModel()
) {
    val c = BoldTheme.colors
    val notes by viewModel.notes.collectAsState()
    val query by viewModel.query.collectAsState()
    val showCompleted by viewModel.showCompleted.collectAsState()
    val kindFilter by viewModel.kindFilter.collectAsState()
    val counts by viewModel.kindCounts.collectAsState()

    Column(Modifier.fillMaxSize().background(c.screen)) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 6.dp)) {
            BoldEyebrow("${counts["all"] ?: 0} kept items")
            Text("Notes", style = BoldType.screenTitle, color = c.ink, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(14.dp))
            BoldSearchField(query, viewModel::setQuery)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BoldFilterChip("All", kindFilter == null && !showCompleted, { viewModel.setKindFilter(null) }, count = counts["all"])
                BoldFilterChip("Tasks", kindFilter == "todo", { viewModel.setKindFilter("todo") }, count = counts["todo"])
                BoldFilterChip("Reminders", kindFilter == "reminder", { viewModel.setKindFilter("reminder") }, count = counts["reminder"])
                BoldFilterChip("Notes", kindFilter == "note", { viewModel.setKindFilter("note") }, count = counts["note"])
                BoldFilterChip("Done", showCompleted, { viewModel.setShowCompleted(true) })
            }
            Spacer(Modifier.height(14.dp))
        }

        val current = notes
        when {
            current == null -> SkeletonList(modifier = Modifier.weight(1f))
            current.isEmpty() -> NotesEmpty(Modifier.weight(1f), query.isNotBlank(), showCompleted)
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                items(current, key = { it.id }) { note ->
                    BoldNoteCard(
                        modifier = Modifier.animateItem(),
                        note = note,
                        onClick = { onNoteClick(note.id) },
                        onToggleComplete = { viewModel.setCompleted(note, !note.completed) },
                        onToggleChecklistItem = { i -> viewModel.toggleChecklistItem(note, i) },
                        onDelete = { viewModel.deleteNote(note) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BoldSearchField(query: String, onChange: (String) -> Unit) {
    val c = BoldTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(ShapeField).background(c.surface).border(1.dp, c.line, ShapeField)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = c.ink3, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Search everything kept…", style = BoldType.searchInput, color = c.ink3)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = BoldType.searchInput.copy(color = c.ink),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BoldNoteCard(
    note: Note,
    onClick: () -> Unit,
    onToggleComplete: () -> Unit,
    onToggleChecklistItem: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = BoldTheme.colors
    val kind = boldKindFor(note.type, note.dueDate != null)
    val items = remember(note.checklist) { note.checklist?.let { Checklist.decode(it) }.orEmpty() }
    val hasChecklist = items.isNotEmpty()
    val completable = note.type == "todo" || note.type == "reminder"

    BoldCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoldKindDot(kind)
                BoldKindChip(kind)
                if (note.dueDate != null) {
                    Text(
                        "${note.dueDate} ${note.dueTime ?: ""}".trim(),
                        style = BoldType.detailMeta, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = c.ink3, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                if (!hasChecklist && completable) {
                    BoldCheckSquare(note.completed, onToggleComplete, size = 22.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    note.title,
                    style = BoldType.noteTitle,
                    color = if (note.completed) c.ink3 else c.ink,
                    textDecoration = if (note.completed) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f)
                )
            }
            if (hasChecklist) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items.forEachIndexed { i, item ->
                        Row(
                            Modifier.clickable { onToggleChecklistItem(i) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BoldCheckSquare(item.checked, { onToggleChecklistItem(i) }, size = 20.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                item.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (item.checked) c.ink3 else c.ink,
                                textDecoration = if (item.checked) TextDecoration.LineThrough else null
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(sourceVisual(note.source).icon, contentDescription = null, tint = c.ink3, modifier = Modifier.size(12.dp))
                Text("FROM ${note.source.uppercase()}", style = BoldType.noteSrcMeta, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun BoldCheckSquare(checked: Boolean, onToggle: () -> Unit, size: Dp) {
    val c = BoldTheme.colors
    val base = Modifier.size(size).clip(RoundedCornerShape(6.dp)).clickable { onToggle() }
    Box(
        if (checked) base.background(c.accent) else base.border(2.dp, c.line, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Icon(Icons.Default.Check, contentDescription = null, tint = BoldOnAccent, modifier = Modifier.size(size * 0.65f))
    }
}

@Composable
private fun NotesEmpty(modifier: Modifier, searching: Boolean, completed: Boolean) {
    val c = BoldTheme.colors
    val (title, subtitle) = when {
        searching -> "Nothing matches that." to "Try a different word."
        completed -> "Nothing completed yet." to "Items you tick off collect here."
        else -> "No items yet." to "Keep suggestions in the Inbox and they land here."
    }
    Column(
        modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(c.surface2),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.SearchOff, contentDescription = null, tint = c.ink3, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.height(18.dp))
        Text(title, style = BoldType.emptyTitle.copy(fontSize = 24.sp), color = c.ink)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = BoldType.body, color = c.ink2, modifier = Modifier, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
