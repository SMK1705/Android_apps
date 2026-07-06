package com.rajasudhan.taskmind.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "suggestions")
data class Suggestion(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val source: String,
    val rawSnippet: String,
    val extractedTitle: String,
    // Default must mirror MIGRATION_1_2 so Room's schema validation passes on upgraded installs.
    @ColumnInfo(defaultValue = "''") val summary: String = "", // one-line model summary; UI falls back to a snippet preview
    val dueDate: String?, // YYYY-MM-DD
    val dueTime: String?, // HH:MM
    val type: String, // "note" | "todo" | "reminder" | "waiting_on"
    val confidence: Double,
    val status: String, // "pending" | "approved" | "rejected"
    // v9 (MIGRATION_8_9): the other party — who you're waiting on / who a commitment is to. Nullable.
    val counterparty: String? = null,
    // v3 (MIGRATION_2_3): when set, the item is hidden from the Inbox until this time (snooze).
    val snoozedUntil: Long? = null,
    // v4 (MIGRATION_3_4): a place named in the source text (geocoded into the note on approval).
    val location: String? = null,
    // v5 (MIGRATION_4_5): "daily" | "weekly" | "monthly" for a repeating reminder, else null.
    val recurrence: String? = null,
    // v7 (MIGRATION_6_7): the model's suggested priority — "normal" | "high" (extraction never emits
    // "low"). Copied onto the created Note.priority on approval. Default mirrors MIGRATION_6_7.
    @ColumnInfo(defaultValue = "'normal'") val priority: String = "normal",
    // v14 (MIGRATION_13_14): auto-tags (#123) — 0–2 from the closed taxonomy, comma-separated (see
    // [Tags]). Sanitised from the model's `tags` array; copied onto the note on approval. Nullable.
    val tags: String? = null,
    // v15 (MIGRATION_14_15): safe semantic dedup (#145) — the title of an existing note/suggestion this
    // capture is likely a re-capture of, surfaced as a dismissable "possible duplicate" flag in the
    // Inbox. Never used to DROP a capture (similarity is unreliable); nullable (null = not flagged).
    val possibleDuplicateOf: String? = null,
    // v17 (MIGRATION_16_17): completion-based recurrence (#124) — carried from a typed "every!" marker or
    // an auto-detected pattern, so approving creates a Note that reschedules from completion. Copied onto
    // Note.repeatFromCompletion on approval. Off by default (NOT NULL DEFAULT 0, mirroring the migration).
    @ColumnInfo(defaultValue = "0") val repeatFromCompletion: Boolean = false
)
