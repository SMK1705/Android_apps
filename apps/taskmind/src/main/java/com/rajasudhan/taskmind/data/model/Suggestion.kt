package com.rajasudhan.taskmind.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "suggestions")
data class Suggestion(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val source: String,
    val rawSnippet: String,
    val extractedTitle: String,
    val dueDate: String?, // YYYY-MM-DD
    val dueTime: String?, // HH:MM
    val type: String, // "note" | "todo" | "reminder"
    val confidence: Double,
    val status: String // "pending" | "approved" | "rejected"
)
