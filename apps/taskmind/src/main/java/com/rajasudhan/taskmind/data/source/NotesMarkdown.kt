package com.rajasudhan.taskmind.data.source

import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.ui.notes.Checklist
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Renders notes as plain-text Markdown — the open-format "exit ramp" (#121): a portable, human-readable
 * copy of everything, so notes are never trapped in the app even without TaskMind installed.
 *
 * Pure and deterministic (a function of the notes alone — dates are formatted in UTC so the output
 * doesn't depend on the device timezone), which keeps it fully unit-testable without any Android deps.
 */
object NotesMarkdown {

    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)

    fun render(notes: List<Note>): String {
        if (notes.isEmpty()) return "# TaskMind Notes\n\n_No notes._\n"

        val sb = StringBuilder("# TaskMind Notes\n\n")
        for (n in notes) {
            // A heading is a single line, so flatten any newline in the title or it splits the document.
            val heading = n.title.ifBlank { "(untitled)" }.replace('\n', ' ').replace('\r', ' ').trim()
            sb.append("## ").append(heading).append("\n\n")

            val meta = buildList {
                add("**Type:** ${n.type}")
                if (n.completed) add("**Status:** completed")
                if (n.priority != "normal") add("**Priority:** ${n.priority}")
                n.dueDate?.let { d -> add("**Due:** $d" + (n.dueTime?.let { " $it" } ?: "")) }
                n.recurrence?.takeIf { it.isNotBlank() }?.let { add("**Repeats:** $it") }
                n.counterparty?.takeIf { it.isNotBlank() }?.let { add("**Waiting on:** $it") }
                n.locationLabel?.takeIf { it.isNotBlank() }?.let { add("**Location:** $it") }
                add("**Created:** ${DATE.format(Instant.ofEpochMilli(n.createdDate))}")
                add("**Source:** ${n.source}")
            }
            sb.append(meta.joinToString(" · ")).append("\n\n")

            // Blockquote every line of the summary — a single "> " prefix would leave a multi-line
            // summary's later lines as an ordinary (mis-rendered) paragraph.
            if (n.summary.isNotBlank()) {
                n.summary.trimEnd().lineSequence().forEach { sb.append("> ").append(it).append("\n") }
                sb.append("\n")
            }
            if (n.body.isNotBlank()) sb.append(n.body.trimEnd()).append("\n\n")

            n.checklist?.takeIf { it.isNotBlank() }?.let { encoded ->
                for (item in Checklist.decode(encoded)) {
                    sb.append(if (item.checked) "- [x] " else "- [ ] ").append(item.text).append("\n")
                }
                sb.append("\n")
            }

            sb.append("---\n\n")
        }
        return sb.toString()
    }
}
