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
    val status: String // "pending" | "approved" | "rejected"
)
