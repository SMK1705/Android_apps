package com.rajasudhan.taskmind.ui.notes

import com.rajasudhan.taskmind.data.model.SavedFilter
import com.rajasudhan.taskmind.data.source.AlarmScheduler
import com.rajasudhan.taskmind.data.source.SavedFilterStore
import com.rajasudhan.taskmind.testutil.FakeTaskMindDao
import com.rajasudhan.taskmind.testutil.MainDispatcherRule
import com.rajasudhan.taskmind.testutil.aNote
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private val savedFilterStore = mockk<SavedFilterStore>(relaxed = true).also {
        every { it.filters } returns flowOf(emptyList())
    }

    @Before
    fun setUp() {
        vm = NotesViewModel(
            dao, mockk<AlarmScheduler>(relaxed = true),
            com.rajasudhan.taskmind.data.source.embedding.SemanticIndex(
                com.rajasudhan.taskmind.data.source.embedding.HashingEmbedder(), dao
            ),
            savedFilterStore
        )
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
    fun reschedule_reAnchorsAMonthlyReminderToTheNewDate() = runTest {
        val id = dao.insertNote(
            aNote(title = "Rent", type = "reminder", dueDate = "2026-01-31", dueTime = "09:00", recurrence = "monthly")
        ).toInt()

        vm.reschedule(dao.getNoteByIdNow(id)!!, "2026-02-05")

        assertEquals(5, dao.getNoteByIdNow(id)!!.recurrenceAnchorDay) // anchor follows the new day
    }

    @Test
    fun reschedule_clearsAStaleNagFiringFlag_soADeferredNagCantResurrectOnReboot() = runTest {
        // Bumping a mid-chain nag reminder re-arms via schedule() (cancels the live re-fire); the persisted
        // nagFiring flag is now stale and must clear, or a reboot would resurrect the dead nag. #180.
        val id = dao.insertNote(
            aNote(title = "Pills", type = "reminder", dueDate = "2026-07-01", dueTime = "09:00", nag = true, nagFiring = true)
        ).toInt()

        vm.reschedule(dao.getNoteByIdNow(id)!!, "2026-07-05")

        assertFalse(dao.getNoteByIdNow(id)!!.nagFiring)
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

    private suspend fun seedTagged() {
        dao.insertNote(aNote(title = "Pay rent", type = "todo", tags = "Money", createdDate = 10))
        dao.insertNote(aNote(title = "Buy milk", type = "todo", tags = "Shopping", createdDate = 20))
        dao.insertNote(aNote(title = "Ship invoice", type = "todo", tags = "Money,Work", createdDate = 30))
        dao.insertNote(aNote(title = "Journal", type = "note", createdDate = 40)) // untagged
    }

    @Test
    fun tagFilter_keepsOnlyNotesCarryingAnySelectedTag() = runTest {
        seedTagged()
        vm.toggleTag("Money")
        assertEquals(setOf("Pay rent", "Ship invoice"), vm.notes.filterNotNull().first().map { it.title }.toSet())
    }

    @Test
    fun tagFilter_isAndedWithKind_andOredWithinTheSelection() = runTest {
        seedTagged()
        // "Work" OR "Shopping" among todos → milk (Shopping) + invoice (Work); rent (Money only) drops.
        vm.toggleTag("Work")
        vm.toggleTag("Shopping")
        assertEquals(setOf("Buy milk", "Ship invoice"), vm.notes.filterNotNull().first().map { it.title }.toSet())
    }

    @Test
    fun toggleTag_isReversible() = runTest {
        seedTagged()
        vm.toggleTag("Money")
        vm.toggleTag("Money") // toggled back off → no tag constraint
        assertEquals(4, vm.notes.filterNotNull().first().size)
    }

    @Test
    fun tagCounts_reflectActiveNotesByTag() = runTest {
        seedTagged()
        val counts = vm.tagCounts.first { it.isNotEmpty() }
        assertEquals(2, counts["Money"]) // rent + invoice
        assertEquals(1, counts["Shopping"])
        assertEquals(1, counts["Work"])
    }

    @Test
    fun applySavedFilter_restoresKindAndTags() = runTest {
        seedTagged()
        vm.applySavedFilter(SavedFilter(name = "Money todos", kind = "todo", tags = listOf("Money")))
        assertEquals("todo", vm.kindFilter.first())
        assertEquals(setOf("Money"), vm.tagFilter.first())
        assertEquals(setOf("Pay rent", "Ship invoice"), vm.notes.filterNotNull().first().map { it.title }.toSet())
    }

    @Test
    fun canSaveCurrentFilter_trueOnlyWhenSomethingIsSelected() = runTest {
        assertFalse(vm.canSaveCurrentFilter())
        vm.toggleTag("Money")
        assertTrue(vm.canSaveCurrentFilter())
    }

    // ---- Task Fade + bankruptcy (#125) ----

    private val dayMs = 24L * 60 * 60 * 1000
    private fun old() = System.currentTimeMillis() - (TaskFade.FADE_AFTER_DAYS + 1) * dayMs

    private suspend fun seedFading() {
        dao.insertNote(aNote(title = "Old chore", type = "todo", createdDate = old()))          // fading
        dao.insertNote(aNote(title = "Older errand", type = "todo", createdDate = old()))        // fading
        dao.insertNote(aNote(title = "Fresh todo", type = "todo", createdDate = System.currentTimeMillis())) // too new
        dao.insertNote(aNote(title = "Old reminder", type = "reminder", dueDate = "2026-08-01", createdDate = old())) // dated
    }

    @Test
    fun fadingCount_countsOldUndatedTodosOnly() = runTest {
        seedFading()
        assertEquals(2, vm.kindCounts.first { it.isNotEmpty() }["fading"])
    }

    @Test
    fun fadingFilter_showsOnlyFadingItems() = runTest {
        seedFading()
        vm.setKindFilter("fading")
        assertEquals(setOf("Old chore", "Older errand"), vm.notes.filterNotNull().first().map { it.title }.toSet())
    }

    @Test
    fun declareBankruptcy_archivesFadingItems_offTheActiveList_intoArchived() = runTest {
        seedFading()
        vm.declareBankruptcy()
        assertEquals(2, vm.archivedCount.first { it > 0 }) // waits for the archive to land
        vm.setKindFilter(null)
        assertEquals(setOf("Fresh todo", "Old reminder"), vm.notes.filterNotNull().first().map { it.title }.toSet())
    }

    @Test
    fun restoreAllArchived_bringsThemBackToActive() = runTest {
        seedFading()
        vm.declareBankruptcy()
        assertEquals(2, vm.archivedCount.first { it > 0 })
        vm.restoreAllArchived()
        assertEquals(0, vm.archivedCount.first { it == 0 })
    }
}
