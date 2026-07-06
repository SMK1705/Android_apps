package com.rajasudhan.taskmind.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
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
import com.rajasudhan.taskmind.ui.common.isOverdue
import com.rajasudhan.taskmind.ui.common.overdueLabel
import com.rajasudhan.taskmind.ui.theme.BoldOnAccent
import com.rajasudhan.taskmind.ui.theme.BoldTheme
import com.rajasudhan.taskmind.ui.theme.BoldType
import com.rajasudhan.taskmind.ui.theme.ShapeCard
import com.rajasudhan.taskmind.ui.theme.ShapeField
import java.time.LocalDate

@Composable
fun NotesScreen(
    isDark: Boolean = true,
    onToggleTheme: () -> Unit = {},
    onNoteClick: (Int) -> Unit = {},
    viewModel: NotesViewModel = hiltViewModel()
) {
    val c = BoldTheme.colors
    val notes by viewModel.notes.collectAsState()
    val query by viewModel.query.collectAsState()
    val showCompleted by viewModel.showCompleted.collectAsState()
    val counts by viewModel.kindCounts.collectAsState()
    val completedCount by viewModel.completedCount.collectAsState()
    val kindFilter by viewModel.kindFilter.collectAsState()

    Column(Modifier.fillMaxSize().background(c.screen)) {
        // Header / search / segment share the spec's 22dp inset; the card list uses 16dp.
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp)) {
            BoldPageHeader(
                title = "Notes",
                subtitle = "Approved · encrypted at rest",
                isDark = isDark,
                onToggleTheme = onToggleTheme
            )
            Spacer(Modifier.height(14.dp))
            BoldSearchField(query, viewModel::setQuery)
            Spacer(Modifier.height(12.dp))
            NotesSegment(
                showCompleted = showCompleted,
                activeCount = counts["all"] ?: 0,
                completedCount = completedCount,
                onSelect = { viewModel.setShowCompleted(it) }
            )
            // Kind filter applies to the Active list only; the Done view is unfiltered.
            if (!showCompleted) {
                Spacer(Modifier.height(12.dp))
                NotesKindFilter(kind = kindFilter, counts = counts, onSelect = viewModel::setKindFilter)
            }
            Spacer(Modifier.height(14.dp))
        }

        val current = notes
        when {
            current == null -> SkeletonList(modifier = Modifier.weight(1f))
            current.isEmpty() -> NotesEmpty(Modifier.weight(1f), query, showCompleted)
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(current, key = { it.id }) { note ->
                    BoldNoteCard(
                        modifier = Modifier.animateItem(),
                        note = note,
                        onClick = { onNoteClick(note.id) },
                        onToggleComplete = { viewModel.setCompleted(note, !note.completed) },
                        onReschedule = { viewModel.reschedule(note, it) }
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
        Modifier.fillMaxWidth().height(44.dp).clip(ShapeField).background(c.surface).border(1.dp, c.line, ShapeField)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = c.ink3, modifier = Modifier.size(17.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Search notes, sources…", style = BoldType.searchInput.copy(fontSize = 14.5.sp), color = c.ink3)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = BoldType.searchInput.copy(fontSize = 14.5.sp, color = c.ink),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search notes" }
            )
        }
    }
}

/** Active / Done segmented control — a raised pill within a recessed track (the design's segStyle). */
@Composable
private fun NotesSegment(showCompleted: Boolean, activeCount: Int, completedCount: Int, onSelect: (Boolean) -> Unit) {
    val c = BoldTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(c.bg2)
            .border(1.dp, c.line, RoundedCornerShape(13.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SegTab("Active · $activeCount", selected = !showCompleted, modifier = Modifier.weight(1f)) { onSelect(false) }
        SegTab("Done · $completedCount", selected = showCompleted, modifier = Modifier.weight(1f)) { onSelect(true) }
    }
}

/** All / Tasks / Reminders / Notes chips that filter the Active list by kind (design's filter row). */
@Composable
private fun NotesKindFilter(kind: String?, counts: Map<String, Int>, onSelect: (String?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BoldFilterChip("All", kind == null, { onSelect(null) }, count = counts["all"] ?: 0)
        // Waiting-on items whose person just got back in touch — front of the row, they need a
        // one-tap "did they deliver?" answer.
        if ((counts["ready_to_close"] ?: 0) > 0) {
            BoldFilterChip("Ready to close", kind == "ready_to_close", { onSelect("ready_to_close") }, count = counts["ready_to_close"] ?: 0)
        }
        if ((counts["overdue"] ?: 0) > 0) {
            BoldFilterChip("Overdue", kind == "overdue", { onSelect("overdue") }, count = counts["overdue"] ?: 0)
        }
        BoldFilterChip("Tasks", kind == "todo", { onSelect("todo") }, count = counts["todo"] ?: 0)
        if ((counts["waiting_on"] ?: 0) > 0) {
            BoldFilterChip("Waiting on", kind == "waiting_on", { onSelect("waiting_on") }, count = counts["waiting_on"] ?: 0)
        }
        BoldFilterChip("Reminders", kind == "reminder", { onSelect("reminder") }, count = counts["reminder"] ?: 0)
        BoldFilterChip("Notes", kind == "note", { onSelect("note") }, count = counts["note"] ?: 0)
    }
}

@Composable
private fun SegTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = BoldTheme.colors
    Box(
        modifier.height(38.dp)
            .shadow(if (selected) 3.dp else 0.dp, RoundedCornerShape(10.dp), clip = false)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) c.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { this.selected = selected; role = Role.Tab },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = BoldType.detailMeta.copy(fontSize = 12.sp, letterSpacing = 0.4.sp),
            color = if (selected) c.ink else c.muted
        )
    }
}

@Composable
private fun BoldNoteCard(
    note: Note,
    onClick: () -> Unit,
    onToggleComplete: () -> Unit,
    onReschedule: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = BoldTheme.colors
    val kind = boldKindFor(note.type, note.dueDate != null)
    val checklist = remember(note.checklist) { note.checklist?.let { Checklist.decode(it) }.orEmpty() }
    val hasChecklist = checklist.isNotEmpty()
    val due = remember(note.dueDate, note.dueTime) { listOfNotNull(note.dueDate, note.dueTime).joinToString(" · ") }
    // Recomputed each composition (isOverdue depends on "now"): only unfinished reminders/todos escalate.
    val overdue = !note.completed && (note.type == "reminder" || note.type == "todo") &&
        isOverdue(note.dueDate, note.dueTime)
    val overdueRel = if (overdue) overdueLabel(note.dueDate, note.dueTime) else null

    Box(
        modifier.fillMaxWidth().clip(ShapeCard).background(c.surface).border(1.dp, c.line, ShapeCard)
            .clickable(onClickLabel = "Open note", role = Role.Button, onClick = onClick)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // Left kind colour bar, inset 12dp top/bottom.
            Box(
                Modifier.fillMaxHeight().padding(vertical = 12.dp).width(3.dp)
                    .clip(RoundedCornerShape(2.dp)).background(if (overdue) c.skip else kind.color())
            )
            Row(
                Modifier.weight(1f).padding(start = 15.dp, end = 15.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BoldCheckSquare(note.completed, onToggleComplete, size = 22.dp)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BoldKindChip(kind)
                        if (note.priority == "high") {
                            Box(
                                Modifier.clip(RoundedCornerShape(4.dp)).background(c.skipBg)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text("HIGH", style = BoldType.detailMeta.copy(fontSize = 8.5.sp, letterSpacing = 0.5.sp), color = c.skip)
                            }
                        } else if (note.priority == "low") {
                            // Muted, so a low item reads as de-prioritised instead of identical to normal.
                            Box(
                                Modifier.clip(RoundedCornerShape(4.dp)).background(c.ink3.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text("LOW", style = BoldType.detailMeta.copy(fontSize = 8.5.sp, letterSpacing = 0.5.sp), color = c.ink3)
                            }
                        }
                        Text(
                            note.source,
                            style = BoldType.noteSrcMeta.copy(fontSize = 10.sp),
                            color = c.ink3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        note.title,
                        style = BoldType.sugTitle.copy(fontSize = 15.5.sp, lineHeight = 20.sp),
                        color = if (note.completed) c.ink3 else c.ink,
                        textDecoration = if (note.completed) TextDecoration.LineThrough else null
                    )
                    if (note.body.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            note.body,
                            style = BoldType.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
                            color = c.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (due.isNotBlank() || hasChecklist) {
                        Spacer(Modifier.height(9.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (due.isNotBlank()) {
                                if (overdue) NoteMetaChip(
                                    Icons.Outlined.Schedule,
                                    if (overdueRel != null) "Overdue · $overdueRel" else "Overdue",
                                    c.skip
                                ) else NoteMetaChip(Icons.Outlined.Schedule, due, c.reminder)
                            }
                            if (hasChecklist) {
                                NoteMetaChip(Icons.Outlined.Checklist, "${checklist.count { it.checked }}/${checklist.size}", c.muted)
                            }
                        }
                    }
                    if (overdue) {
                        Spacer(Modifier.height(9.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RescheduleChip("Today") { onReschedule(LocalDate.now().toString()) }
                            RescheduleChip("Tomorrow") { onReschedule(LocalDate.now().plusDays(1).toString()) }
                        }
                    }
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = c.ink3,
                    modifier = Modifier.padding(top = 2.dp).size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun NoteMetaChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
        Text(text, style = BoldType.detailMeta.copy(fontSize = 10.5.sp), color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A small tappable pill to bump an overdue item forward (e.g. "Today" / "Tomorrow"). */
@Composable
private fun RescheduleChip(label: String, onClick: () -> Unit) {
    val c = BoldTheme.colors
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(c.surface2)
            .clickable(onClick = onClick, onClickLabel = "Reschedule to $label", role = Role.Button)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, style = BoldType.detailMeta.copy(fontSize = 11.5.sp), color = c.ink2)
    }
}

@Composable
private fun BoldCheckSquare(checked: Boolean, onToggle: () -> Unit, size: Dp) {
    val c = BoldTheme.colors
    val base = Modifier.size(size).clip(RoundedCornerShape(7.dp)).clickable { onToggle() }
        .semantics { role = Role.Checkbox; this.selected = checked }
    Box(
        if (checked) base.background(c.accent) else base.border(1.5.dp, c.line2, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Icon(Icons.Default.Check, contentDescription = null, tint = BoldOnAccent, modifier = Modifier.size(size * 0.62f))
    }
}

@Composable
private fun NotesEmpty(modifier: Modifier, query: String, completed: Boolean) {
    val c = BoldTheme.colors
    val searching = query.isNotBlank()
    val (title, subtitle) = when {
        searching -> "No matches" to "Nothing matches “$query”. Try another word or source."
        completed -> "Nothing done yet" to "Items you tick off collect here."
        else -> "Nothing here yet" to "Approve items from your Inbox and they land here."
    }
    Column(
        modifier.fillMaxSize().padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (searching) Icons.Default.Search else Icons.Outlined.Description,
                contentDescription = null, tint = c.ink3,
                modifier = Modifier.size(if (searching) 24.dp else 26.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = BoldType.emptyTitle.copy(fontSize = 24.sp), color = c.ink)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = BoldType.body.copy(fontSize = 14.sp),
            color = c.muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
