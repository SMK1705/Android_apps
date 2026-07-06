package com.rajasudhan.taskmind.data.source.appfunctions

/**
 * Request/response shapes for TaskMind's Android AppFunctions surface (#127). Kept as plain data
 * classes so the follow-up that adds the `androidx.appfunctions` dependency can annotate them
 * `@AppFunctionSerializable` without reshaping — the on-device agent (Gemini) fills the requests and
 * reads the responses back to the user.
 */

data class CreateTaskRequest(
    val title: String,
    val dueDate: String? = null, // YYYY-MM-DD
    val dueTime: String? = null, // HH:MM
    val type: String = "todo",   // "todo" | "reminder" | "note" | "waiting_on"
    val notes: String = "",
)

data class SnoozeRequest(
    val id: Int,
    val dueDate: String,         // YYYY-MM-DD
    val dueTime: String? = null, // HH:MM
)

/** A generic ok/failed result with a short message the agent can read back to the user. */
data class AppFunctionResult(val success: Boolean, val message: String)

data class DueItem(val id: Int, val title: String, val dueTime: String?, val type: String)

data class DueTodayResult(val items: List<DueItem>, val count: Int)
