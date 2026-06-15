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
    val type: String // "note" | "todo" | "reminder"
)
