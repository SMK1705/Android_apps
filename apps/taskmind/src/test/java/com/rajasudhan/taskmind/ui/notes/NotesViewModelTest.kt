package com.rajasudhan.taskmind.ui.notes

import com.rajasudhan.taskmind.data.source.AlarmScheduler
import com.rajasudhan.taskmind.testutil.FakeTaskMindDao
import com.rajasudhan.taskmind.testutil.MainDispatcherRule
import com.rajasudhan.taskmind.testutil.aNote
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val dao = FakeTaskMindDao()
    private lateinit var vm: NotesViewModel

    @Before
    fun setUp() {
        vm = NotesViewModel(dao, mockk<AlarmScheduler>(relaxed = true))
    }

    private suspend fun seed() {
        dao.insertNote(aNote(title = "Buy milk", type = "todo", createdDate = 10))
        dao.insertNote(aNote(title = "Standup", type = "reminder", dueDate = "2026-07-01", dueTime = "09:00", createdDate = 20))
        dao.insertNote(aNote(title = "Journal", type = "note", createdDate = 30))
        dao.insertNote(aNote(title = "Done thing", type = "todo", completed = true, completedDate = 40, createdDate = 5))
    }

    @Test
    fun activeNotes_excludeCompleted_andArePrioritisedReminderTodoNote() = runTest {
        seed()
        val active = vm.notes.filterNotNull().first()
        assertEquals(listOf("Standup", "Buy milk", "Journal"), active.map { it.title })
    }

    @Test
    fun kindFilter_keepsOnlyThatTypeAmongActive() = runTest {
        seed()
        vm.setKindFilter("todo")
        assertEquals(listOf("Buy milk"), vm.notes.filterNotNull().first().map { it.title })
    }

    @Test
    fun showCompleted_showsOnlyDoneItems() = runTest {
        seed()
        vm.setShowCompleted(true)
        assertEquals(listOf("Done thing"), vm.notes.filterNotNull().first().map { it.title })
    }

    @Test
    fun search_matchesAcrossFields_withinActive() = runTest {
        seed()
        vm.setQuery("milk")
        assertEquals(listOf("Buy milk"), vm.notes.filterNotNull().first().map { it.title })
    }

    @Test
    fun kindCounts_reflectActiveNotesByType() = runTest {
        seed()
        val counts = vm.kindCounts.first { it.isNotEmpty() }
        assertEquals(3, counts["all"])
        assertEquals(1, counts["todo"])
        assertEquals(1, counts["reminder"])
        assertEquals(1, counts["note"])
    }

    @Test
    fun setCompleted_movesNoteIntoTheCompletedSet() = runTest {
        seed()
        val milk = dao.getNotesList().first { it.title == "Buy milk" }
        vm.setCompleted(milk, true)
        assertTrue(dao.getNoteByIdNow(milk.id)!!.completed)
    }
}
