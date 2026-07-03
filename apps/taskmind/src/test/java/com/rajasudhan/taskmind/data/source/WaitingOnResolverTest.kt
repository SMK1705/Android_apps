package com.rajasudhan.taskmind.data.source

import com.rajasudhan.taskmind.testutil.FakeTaskMindDao
import com.rajasudhan.taskmind.testutil.aNote
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * When a waiting-on counterparty gets in touch, the resolver raises a one-tap "did they deliver?"
 * check — it must NEVER complete the note or cancel its alarm on its own (that was the false-close
 * bug). Matching stays conservative so an unrelated notification doesn't even prompt.
 */
class WaitingOnResolverTest {

    private val dao = FakeTaskMindDao()
    private val notifier = mockk<WaitingConfirmNotifier>(relaxed = true)
    private val resolver = WaitingOnResolver(dao, notifier)

    private suspend fun seedWaiting(id: Int, counterparty: String, title: String = "Waiting on $counterparty") {
        dao.insertNote(aNote(id = id, title = title, type = "waiting_on", counterparty = counterparty))
    }

    @Test
    fun promptsButNeverCloses_whenTheCounterpartyMessages() = runTest {
        seedWaiting(1, "John")

        val n = resolver.resolveFrom("John Doe", "here's the file you wanted")

        assertEquals(1, n)
        // The note is NOT completed — completion is now the user's explicit tap.
        assertFalse(dao.getNoteByIdNow(1)!!.completed)
        // It is flagged as awaiting confirmation, and the prompt was raised with the message snippet.
        assertNotNull(dao.getNoteByIdNow(1)!!.pendingConfirmSince)
        verify(exactly = 1) { notifier.prompt(match { it.id == 1 }, "here's the file you wanted") }
    }

    @Test
    fun matchesTheLandlord_afterTheThePrefixIsStripped() = runTest {
        seedWaiting(1, "the landlord")

        assertEquals(1, resolver.resolveFrom("Landlord"))
        assertFalse(dao.getNoteByIdNow(1)!!.completed)
        verify(exactly = 1) { notifier.prompt(match { it.id == 1 }, null) }
    }

    @Test
    fun doesNotPrompt_onALooseSubstringNameCollision() = runTest {
        // "Dave" must NOT be prompted by a "David" notification — whole-word tokens only.
        seedWaiting(1, "Dave")

        assertEquals(0, resolver.resolveFrom("David"))
        assertNull(dao.getNoteByIdNow(1)!!.pendingConfirmSince)
        verify(exactly = 0) { notifier.prompt(any(), any()) }
    }

    @Test
    fun leavesUnrelatedSenders_alone() = runTest {
        seedWaiting(1, "Priya")

        assertEquals(0, resolver.resolveFrom("Amazon"))
        assertNull(dao.getNoteByIdNow(1)!!.pendingConfirmSince)
        verify(exactly = 0) { notifier.prompt(any(), any()) }
    }

    @Test
    fun ignoresTooShortSenders() = runTest {
        seedWaiting(1, "Bo")

        // Both the sender guard and the token guard reject a 2-char name — no accidental prompt.
        assertEquals(0, resolver.resolveFrom("Bo"))
        verify(exactly = 0) { notifier.prompt(any(), any()) }
    }

    @Test
    fun promptsEveryOpenItemForThatPerson() = runTest {
        seedWaiting(1, "Sarah", title = "Waiting on Sarah: the file")
        seedWaiting(2, "Sarah", title = "Waiting on Sarah: the sign-off")
        seedWaiting(3, "Mike")

        assertEquals(2, resolver.resolveFrom("Sarah Connor"))
        assertNotNull(dao.getNoteByIdNow(1)!!.pendingConfirmSince)
        assertNotNull(dao.getNoteByIdNow(2)!!.pendingConfirmSince)
        assertNull(dao.getNoteByIdNow(3)!!.pendingConfirmSince) // Mike is untouched
        verify(exactly = 1) { notifier.prompt(match { it.id == 1 }, any()) }
        verify(exactly = 1) { notifier.prompt(match { it.id == 2 }, any()) }
        verify(exactly = 0) { notifier.prompt(match { it.id == 3 }, any()) }
    }

    @Test
    fun doesNotReStampAnAlreadyPendingItem_butStillRefreshesThePrompt() = runTest {
        // Second message from the same person: keep the original "got in touch" timestamp, but still
        // re-raise the prompt (a chatty counterparty just refreshes the one quiet notification).
        val firstContact = 5_000L
        dao.insertNote(
            aNote(id = 1, title = "Waiting on John: report", type = "waiting_on", counterparty = "John", pendingConfirmSince = firstContact)
        )

        assertEquals(1, resolver.resolveFrom("John", "any update?"))

        assertEquals(firstContact, dao.getNoteByIdNow(1)!!.pendingConfirmSince) // not re-stamped
        verify(exactly = 1) { notifier.prompt(match { it.id == 1 }, "any update?") }
    }
}
