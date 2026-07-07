package com.rajasudhan.taskmind.data.source.appfunctions

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.service.AppFunction
import javax.inject.Inject

/**
 * The Android AppFunctions surface (#209): the thin `@AppFunction`-annotated binding the system agent
 * (Gemini) discovers and invokes. Each function just adapts the platform signature — a leading
 * [AppFunctionContext] plus a `@AppFunctionSerializable` request — to the already-tested behaviour in
 * [TaskMindAppFunctions], which runs over the real data layer. Keeping the binding thin means the logic
 * stays fully unit-tested and the alpha AppFunctions API is confined to this one file + the models.
 *
 * Hilt provides this class; it's handed to the framework via TaskMindApp's
 * `AppFunctionConfiguration.Provider` (addEnclosingClassFactory). `isDescribedByKDoc = true` makes the
 * compiler use each function's KDoc as the agent-facing description.
 */
class AgentFunctions @Inject constructor(
    private val taskFunctions: TaskMindAppFunctions,
) {
    /**
     * Add a task, reminder or note to the user's TaskMind. It lands in their Inbox as a suggestion for
     * them to approve — TaskMind proposes, the user still decides.
     *
     * @param request What to add: the title and optional date/time/type/notes.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun createTask(
        appFunctionContext: AppFunctionContext,
        request: CreateTaskRequest,
    ): AppFunctionResult = taskFunctions.createTask(request)

    /**
     * List the user's TaskMind items due today, soonest-timed first. Use the returned item ids to snooze.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getItemsDueToday(
        appFunctionContext: AppFunctionContext,
    ): DueTodayResult = taskFunctions.getItemsDueToday()

    /**
     * Reschedule ("snooze") an existing TaskMind item to a new date/time, keeping its alarm and any
     * mirrored calendar event in step.
     *
     * @param request The id of the item to move and its new date (and optional time).
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun snoozeItem(
        appFunctionContext: AppFunctionContext,
        request: SnoozeRequest,
    ): AppFunctionResult = taskFunctions.snoozeItem(request)
}
