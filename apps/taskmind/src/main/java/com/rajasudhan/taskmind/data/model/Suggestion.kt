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
    val type: String, // "note" | "todo" | "reminder"
    val confidence: Double,
    val status: String, // "pending" | "approved" | "rejected"
    // v3 (MIGRATION_2_3): when set, the item is hidden from the Inbox until this time (snooze).
    val snoozedUntil: Long? = null,
    // v4 (MIGRATION_3_4): a place named in the source text (geocoded into the note on approval).
    val location: String? = null,
    // v5 (MIGRATION_4_5): "daily" | "weekly" | "monthly" for a repeating reminder, else null.
    val recurrence: String? = null,
    // v7 (MIGRATION_6_7): the model's suggested priority — "normal" | "high" (extraction never emits
    // "low"). Copied onto the created Note.priority on approval. Default mirrors MIGRATION_6_7.
    @ColumnInfo(defaultValue = "'normal'") val priority: String = "normal"
)
