package com.rajasudhan.taskmind.ui.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.rajasudhan.taskmind.data.model.SavedFilter
import com.rajasudhan.taskmind.data.model.Tags
import com.rajasudhan.taskmind.ui.bold.*
import com.rajasudhan.taskmind.ui.common.SkeletonList
import com.rajasudhan.taskmind.ui.common.isOverdue
import com.rajasudhan.taskmind.ui.common.overdueLabel
import com.rajasudhan.taskmind.ui.theme.BoldOnAccent
import com.rajasudhan.taskmind.ui.theme.BoldTheme
import com.rajasudhan.taskmind.ui.theme.BoldType
import com.rajasudhan.taskmind.ui.theme.ShapeCard
import com.rajasudhan.taskmind.ui.theme.ShapeChip
import com.rajasudhan.taskmind.ui.theme.ShapeField
import java.time.LocalDate

@Composable
fun NotesScreen(
    isDark: Boolean = true,
    onToggleTheme: () -> Unit = {},
    onNoteClick: (Int) -> Unit = {},
    onOpenGuide: () -> Unit = {},
    onLock: (() -> Unit)? = null,
    viewModel: NotesViewModel = hiltViewModel()
) {
    val c = BoldTheme.colors
    val notes by viewModel.notes.collectAsState()
    val query by viewModel.query.collectAsState()
    val showCompleted by viewModel.showCompleted.collectAsState()
    val counts by viewModel.kindCounts.collectAsState()
    val completedCount by viewModel.completedCount.collectAsState()
    val kindFilter by viewModel.kindFilter.collectAsState()
    val tagFilter by viewModel.tagFilter.collectAsState()
    val tagCounts by viewModel.tagCounts.collectAsState()
    val savedFilters by viewModel.savedFilters.collectAsState()
    val archivedCount by viewModel.archivedCount.collectAsState()

    // Tags actually present on active notes, in taxonomy order — the filter row only offers real ones.
    val presentTags = Tags.TAXONOMY.filter { (tagCounts[it] ?: 0) > 0 }
    val canSaveFilter = kindFilter != null || tagFilter.isNotEmpty()
    var showSaveDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SavedFilter?>(null) }
    var showBankruptcyDialog by remember { mutableStateOf(false) } // #125 batch-archive confirm

    val listState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        BoldCollapsingHeader(
            title = "Notes",
            subtitle = "Approved · encrypted at rest",
            isDark = isDark,
            onToggleTheme = onToggleTheme,
            onOpenGuide = onOpenGuide,
            onLock = onLock,
            listState = listState,
            hasScrollableContent = notes?.isNotEmpty() == true,
            resetKey = listOf(showCompleted, kindFilter, tagFilter, query),
            collapsible = {
                Spacer(Modifier.height(14.dp))
                BoldSearchField(query, viewModel::setQuery)
                Spacer(Modifier.height(12.dp))
                NotesSegment(
                    showCompleted = showCompleted,
                    activeCount = counts["all"] ?: 0,
                    completedCount = completedCount,
                    onSelect = { viewModel.setShowCompleted(it) }
                )
            },
            pinned = {
                // Filter chips (Option 2): stay put while the cards scroll under them.
                if (!showCompleted) {
                    Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 12.dp)) {
                        NotesKindFilter(kind = kindFilter, counts = counts, archivedCount = archivedCount, onSelect = viewModel::setKindFilter)
                        if (presentTags.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            NotesTagFilter(presentTags, tagFilter, tagCounts, viewModel::toggleTag)
                        }
                        if (savedFilters.isNotEmpty() || canSaveFilter) {
                            Spacer(Modifier.height(8.dp))
                            SavedFilterRow(
                                saved = savedFilters,
                                activeKind = kindFilter,
                                activeTags = tagFilter,
                                canSave = canSaveFilter,
                                onApply = viewModel::applySavedFilter,
                                onRequestDelete = { pendingDelete = it },
                                onSaveClick = { showSaveDialog = true }
                            )
                        }
                    }
                }
            },
        ) {
            val current = notes
            when {
                current == null -> SkeletonList(modifier = Modifier.fillMaxSize())
                current.isEmpty() -> NotesEmpty(Modifier.fillMaxSize(), query, showCompleted)
                else -> {
                    val now = System.currentTimeMillis()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // #125: the Fading shelf offers one-tap bankruptcy; the Archived shelf offers restore.
                        if (kindFilter == "fading") {
                            item {
                                LifecycleBanner(
                                    "${current.size} task${if (current.size == 1) "" else "s"} untouched for weeks",
                                    "Archive all", c.skip
                                ) { showBankruptcyDialog = true }
                            }
                        } else if (kindFilter == "archived") {
                            item {
                                LifecycleBanner("${current.size} archived · kept, not deleted", "Restore all", c.accent) {
                                    viewModel.restoreAllArchived()
                                }
                            }
                        }
                        items(current, key = { it.id }) { note ->
                            // Stale to-dos render faded in every list; archived items are always faded.
                            val faded = kindFilter == "archived" ||
                                TaskFade.isFading(note.type, note.dueDate, note.completed, note.archived, note.createdDate, now)
                            BoldNoteCard(
                                modifier = Modifier.animateItem().then(if (faded) Modifier.alpha(0.5f) else Modifier),
                                note = note,
                                onClick = { onNoteClick(note.id) },
                                onToggleComplete = { if (kindFilter != "archived") viewModel.setCompleted(note, !note.completed) },
                                onReschedule = { viewModel.reschedule(note, it) }
                            )
                        }
                    }
                }
            }
        }

        if (showSaveDialog) {
            SaveFilterDialog(
                onSave = { name -> viewModel.saveCurrentFilter(name); showSaveDialog = false },
                onDismiss = { showSaveDialog = false }
            )
        }
        pendingDelete?.let { filter ->
            ConfirmDeleteFilterDialog(
                name = filter.name,
                onConfirm = { viewModel.deleteSavedFilter(filter.name); pendingDelete = null },
                onDismiss = { pendingDelete = null }
            )
        }
        if (showBankruptcyDialog) {
            val fadingCount = counts["fading"] ?: 0
            AlertDialog(
                onDismissRequest = { showBankruptcyDialog = false },
                confirmButton = {
                    TextButton(onClick = { viewModel.declareBankruptcy(); showBankruptcyDialog = false }) {
                        Text("Archive $fadingCount", color = c.skip)
                    }
                },
                dismissButton = { TextButton(onClick = { showBankruptcyDialog = false }) { Text("Cancel", color = c.muted) } },
                title = { Text("Archive stale tasks", style = BoldType.emptyTitle.copy(fontSize = 18.sp), color = c.ink) },
                text = {
                    Text(
                        "Move $fadingCount undated task${if (fadingCount == 1) "" else "s"} you haven't touched in weeks to the Archived shelf. They're kept — never deleted — and you can restore them anytime.",
                        style = BoldType.body.copy(fontSize = 14.sp), color = c.muted
                    )
                },
                containerColor = c.surface
            )
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
private fun NotesKindFilter(kind: String?, counts: Map<String, Int>, archivedCount: Int, onSelect: (String?) -> Unit) {
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
        // Task Fade (#125): stale undated to-dos, and the recoverable Archived shelf — only shown when non-empty.
        if ((counts["fading"] ?: 0) > 0) {
            BoldFilterChip("Fading", kind == "fading", { onSelect("fading") }, count = counts["fading"] ?: 0)
        }
        if (archivedCount > 0) {
            BoldFilterChip("Archived", kind == "archived", { onSelect("archived") }, count = archivedCount)
        }
    }
}

/** Auto-tag chips (#123): multi-select, AND-ed with the kind filter, OR within the selection. */
@Composable
private fun NotesTagFilter(
    tags: List<String>,
    selected: Set<String>,
    counts: Map<String, Int>,
    onToggle: (String) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tags.forEach { tag ->
            BoldFilterChip(tag, tag in selected, { onToggle(tag) }, count = counts[tag] ?: 0)
        }
    }
}

/** Pinned saved-filter chips + a "Save" affordance. Tap a chip to apply it; long-press to remove it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedFilterRow(
    saved: List<SavedFilter>,
    activeKind: String?,
    activeTags: Set<String>,
    canSave: Boolean,
    onApply: (SavedFilter) -> Unit,
    onRequestDelete: (SavedFilter) -> Unit,
    onSaveClick: () -> Unit
) {
    val c = BoldTheme.colors
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        saved.forEach { filter ->
            // Highlight the pinned chip when the live selection exactly matches it.
            val isActive = filter.kind == activeKind && filter.tags.toSet() == activeTags
            val fg = if (isActive) BoldOnAccent else c.ink2
            Row(
                Modifier.clip(ShapeChip).background(if (isActive) c.accent else c.surface2)
                    .combinedClickable(
                        onClick = { onApply(filter) },
                        onLongClick = { onRequestDelete(filter) },
                        onLongClickLabel = "Remove filter"
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .semantics { role = Role.Button },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(filter.name, style = BoldType.filterChip, color = fg)
            }
        }
        if (canSave) {
            Row(
                Modifier.clip(ShapeChip).border(1.dp, c.line2, ShapeChip)
                    .clickable(onClickLabel = "Save current filter", role = Role.Button, onClick = onSaveClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = c.ink2, modifier = Modifier.size(14.dp))
                Text("Save", style = BoldType.filterChip, color = c.ink2)
            }
        }
    }
}

/** Names and pins the current kind+tags selection as a smart filter. */
@Composable
private fun SaveFilterDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val c = BoldTheme.colors
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name) }, enabled = name.isNotBlank()) {
                Text("Save", color = if (name.isNotBlank()) c.accent else c.ink3)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.muted) } },
        title = { Text("Save filter", style = BoldType.emptyTitle.copy(fontSize = 18.sp), color = c.ink) },
        text = {
            Column {
                Text("Pin the current filter as a chip.", style = BoldType.body.copy(fontSize = 13.sp), color = c.muted)
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().height(44.dp).clip(ShapeField).background(c.surface)
                        .border(1.dp, c.line, ShapeField).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (name.isEmpty()) Text("Filter name", style = BoldType.searchInput.copy(fontSize = 14.5.sp), color = c.ink3)
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it.take(24) },
                            singleLine = true,
                            textStyle = BoldType.searchInput.copy(fontSize = 14.5.sp, color = c.ink),
                            cursorBrush = SolidColor(c.accent),
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Filter name" }
                        )
                    }
                }
            }
        },
        containerColor = c.surface
    )
}

/** Row banner atop the Fading / Archived shelves (#125): a one-line status + a single tinted action. */
@Composable
private fun LifecycleBanner(text: String, action: String, accent: Color, onAction: () -> Unit) {
    val c = BoldTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(ShapeCard).background(c.surface).border(1.dp, c.line, ShapeCard)
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text, style = BoldType.detailMeta.copy(fontSize = 12.sp), color = c.muted, modifier = Modifier.weight(1f))
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.15f))
                .clickable(onClick = onAction, onClickLabel = action, role = Role.Button)
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(action, style = BoldType.detailMeta.copy(fontSize = 11.5.sp), color = accent)
        }
    }
}

/** Confirms removing a pinned saved filter. */
@Composable
private fun ConfirmDeleteFilterDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = BoldTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onConfirm) { Text("Remove", color = c.skip) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.muted) } },
        title = { Text("Remove filter", style = BoldType.emptyTitle.copy(fontSize = 18.sp), color = c.ink) },
        text = { Text("Remove the saved filter “$name”?", style = BoldType.body.copy(fontSize = 14.sp), color = c.muted) },
        containerColor = c.surface
    )
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
