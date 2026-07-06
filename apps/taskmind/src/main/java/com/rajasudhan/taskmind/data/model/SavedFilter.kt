package com.rajasudhan.taskmind.data.model

import com.squareup.moshi.JsonClass

/**
 * A user-pinned smart filter (#123): a named kind + tags combination shown as a custom chip in the
 * Notes filter row, so a frequently-used view ("Work") is one tap away instead of re-selecting the
 * chips each time. Captured from the live filter state; natural-language-to-filter is a later layer.
 */
@JsonClass(generateAdapter = true)
data class SavedFilter(
    val name: String,
    // The kind dimension, mirroring NotesViewModel: null | "todo" | "reminder" | "note" |
    // "waiting_on" | "overdue" | "ready_to_close".
    val kind: String? = null,
    // Auto-tags to filter by; a note matches if it carries ANY of these (same OR semantics as the
    // live tag chips). Empty = no tag constraint.
    val tags: List<String> = emptyList()
)
