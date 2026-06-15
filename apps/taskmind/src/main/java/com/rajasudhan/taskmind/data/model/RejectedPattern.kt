package com.rajasudhan.taskmind.data.model

import androidx.room.Entity

/**
 * On-device learning memory: a sender or keyword the user has repeatedly rejected. The pipeline
 * consults this to down-rank or skip future suggestions from the same origin. Kept entirely local.
 */
@Entity(tableName = "rejected_patterns", primaryKeys = ["kind", "value"])
data class RejectedPattern(
    val kind: String,     // "sender" | "keyword"
    val value: String,    // normalised (lowercased) sender/keyword
    val count: Int,       // how many times the user rejected something matching this
    val updatedAt: Long
)
