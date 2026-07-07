package com.rajasudhan.taskmind.data.source.appfunctions

import androidx.appfunctions.AppFunctionContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The @AppFunction binding (#209) just adapts the platform signature (a leading AppFunctionContext) to
 * the already-tested TaskMindAppFunctions, so these lock the delegation — the behaviour itself is covered
 * by TaskMindAppFunctionsTest.
 */
class AgentFunctionsTest {

    private val handler = mockk<TaskMindAppFunctions>()
    private val ctx = mockk<AppFunctionContext>(relaxed = true)
    private val functions = AgentFunctions(handler)

    @Test
    fun createTask_delegatesToTheHandler() = runTest {
        val request = CreateTaskRequest(title = "Call the dentist")
        val expected = AppFunctionResult(true, "Added")
        coEvery { handler.createTask(request) } returns expected

        assertSame(expected, functions.createTask(ctx, request))
        coVerify(exactly = 1) { handler.createTask(request) }
    }

    @Test
    fun getItemsDueToday_delegatesToTheHandler() = runTest {
        val expected = DueTodayResult(emptyList(), 0)
        coEvery { handler.getItemsDueToday(any()) } returns expected

        assertSame(expected, functions.getItemsDueToday(ctx))
        coVerify(exactly = 1) { handler.getItemsDueToday(any()) }
    }

    @Test
    fun snoozeItem_delegatesToTheHandler() = runTest {
        val request = SnoozeRequest(id = 5, dueDate = "2026-07-10")
        val expected = AppFunctionResult(true, "Snoozed")
        coEvery { handler.snoozeItem(request) } returns expected

        assertSame(expected, functions.snoozeItem(ctx, request))
        coVerify(exactly = 1) { handler.snoozeItem(request) }
    }
}
