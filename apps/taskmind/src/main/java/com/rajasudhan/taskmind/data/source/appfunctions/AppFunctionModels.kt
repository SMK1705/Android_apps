package com.rajasudhan.taskmind.data.source.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/**
 * Request/response shapes for TaskMind's Android AppFunctions surface (#127, bound in #209). Annotated
 * `@AppFunctionSerializable` so the compiler encodes their schema (field KDoc included) for the on-device
 * agent (Gemini) to fill the requests and read the responses back to the user.
 */

@AppFunctionSerializable(isDescribedByKDoc = true)
data class CreateTaskRequest(
    /** What to do — the task/reminder title, e.g. "Call the dentist". */
    val title: String,
    /** Optional due date as YYYY-MM-DD, or null if none. */
    val dueDate: String? = null,
    /** Optional due time as 24-hour HH:MM, or null if none. */
    val dueTime: String? = null,
    /** The kind of item: "todo", "reminder", "note", or "waiting_on". Null defaults to "todo". */
    val type: String? = null,
    /** Optional extra details/notes for the item. */
    val notes: String? = null,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
data class SnoozeRequest(
    /** The id of the existing item to reschedule (from getItemsDueToday). */
    val id: Int,
    /** The new date to move the item to, as YYYY-MM-DD. */
    val dueDate: String,
    /** Optional new time as 24-hour HH:MM, or null to leave it all-day. */
    val dueTime: String? = null,
)

/** A generic ok/failed result with a short message the agent can read back to the user. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AppFunctionResult(
    /** True if the action succeeded. */
    val success: Boolean,
    /** A short human-readable message describing the outcome. */
    val message: String,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
data class DueItem(
    /** The item's id (use it to snooze). */
    val id: Int,
    /** The item's title. */
    val title: String,
    /** The item's time as HH:MM, or null if it has no specific time. */
    val dueTime: String?,
    /** The kind of item: "todo", "reminder", "note", or "waiting_on". */
    val type: String,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
data class DueTodayResult(
    /** The items due today, soonest-timed first. */
    val items: List<DueItem>,
    /** How many items are due today. */
    val count: Int,
)
