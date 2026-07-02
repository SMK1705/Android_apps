package com.rajasudhan.taskmind.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    // Default must mirror MIGRATION_1_2 so Room's schema validation passes on upgraded installs.
    @ColumnInfo(defaultValue = "''") val summary: String = "", // carried over from the approved suggestion
    val body: String,
    val dueDate: String?, // YYYY-MM-DD
    val dueTime: String?, // HH:MM
    val source: String,
    val createdDate: Long, // timestamp
    val type: String, // "note" | "todo" | "reminder"
    // ---- v3 additions (MIGRATION_2_3). Nullable columns need no @ColumnInfo default; the one
    // NOT NULL column (`completed`) mirrors its migration DEFAULT so schema validation passes. ----
    @ColumnInfo(defaultValue = "0") val completed: Boolean = false,
    val completedDate: Long? = null,
    val recurrence: String? = null, // null | "daily" | "weekly" | "monthly"
    val checklist: String? = null,  // JSON array of {text, checked}, or null if not a checklist
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val locationRadius: Double? = null, // metres
    val locationLabel: String? = null,
    // v6 (MIGRATION_5_6): "low" | "normal" | "high" — the priority sort key after due date.
    @ColumnInfo(defaultValue = "'normal'") val priority: String = "normal",
    // v8 (MIGRATION_7_8): nag until done — after the reminder fires, keep re-firing it at
    // escalating intervals until the note is completed (or deleted). Off by default.
    @ColumnInfo(defaultValue = "0") val nag: Boolean = false
)
