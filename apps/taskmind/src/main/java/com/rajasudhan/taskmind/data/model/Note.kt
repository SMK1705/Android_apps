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
    val type: String, // "note" | "todo" | "reminder" | "waiting_on"
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
    @ColumnInfo(defaultValue = "0") val nag: Boolean = false,
    // v9 (MIGRATION_8_9): the other party — who you're waiting on (type "waiting_on") or who a
    // commitment is to. Used to prompt a "did they deliver?" check when that person next gets in touch.
    val counterparty: String? = null,
    // v11 (MIGRATION_10_11): set to the moment the counterparty got in touch on an open "waiting_on"
    // item — the note is then awaiting the user's one-tap confirmation of whether they actually
    // delivered. Nullable (null = not awaiting confirmation), so no @ColumnInfo default.
    val pendingConfirmSince: Long? = null,
    // v12 (MIGRATION_11_12): the intended day-of-month (1–31) for a MONTHLY reminder, captured when the
    // recurrence is set. Each occurrence is stepped from this anchor (clamped to the month's length)
    // rather than from the previous — possibly already-clamped — date, so a "31st" reminder doesn't
    // permanently drift down to the 28th after February. Null for non-monthly / undated notes.
    val recurrenceAnchorDay: Int? = null
)
