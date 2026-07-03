package com.rajasudhan.taskmind.data.source

import com.rajasudhan.taskmind.testutil.FakeTaskMindDao
import com.rajasudhan.taskmind.testutil.aNote
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Auto-resolving a waiting-on item when its counterparty gets in touch — with conservative matching. */
class WaitingOnResolverTest {

    private val dao = FakeTaskMindDao()
    private val alarms = mockk<AlarmScheduler>(relaxed = true)
    private val resolver = WaitingOnResolver(dao, alarms)

    private suspend fun seedWaiting(id: Int, counterparty: String, title: String = "Waiting on $counterparty") {
        dao.insertNote(aNote(id = id, title = title, type = "waiting_on", counterparty = counterparty))
    }

    @Test
    fun resolvesTheWaitingItem_whenTheCounterpartyMessages() = runTest {
        seedWaiting(1, "John")

        val n = resolver.resolveFrom("John Doe") // a WhatsApp message titled by sender

        assertEquals(1, n)
        assertTrue(dao.getNoteByIdNow(1)!!.completed)
        verify { alarms.cancel(1) }
    }

    @Test
    fun matchesTheLandlord_afterTheThePrefixIsStripped() = runTest {
        seedWaiting(1, "the landlord")

        assertEquals(1, resolver.resolveFrom("Landlord"))
        assertTrue(dao.getNoteByIdNow(1)!!.completed)
    }

    @Test
    fun doesNotResolve_onALooseSubstringNameCollision() = runTest {
        // "Dave" must NOT be closed by a "David" notification — whole-word tokens only.
        seedWaiting(1, "Dave")

        assertEquals(0, resolver.resolveFrom("David"))
        assertFalse(dao.getNoteByIdNow(1)!!.completed)
        verify(exactly = 0) { alarms.cancel(any()) }
    }

    @Test
    fun leavesUnrelatedSenders_alone() = runTest {
        seedWaiting(1, "Priya")

        assertEquals(0, resolver.resolveFrom("Amazon"))
        assertFalse(dao.getNoteByIdNow(1)!!.completed)
    }

    @Test
    fun ignoresTooShortSenders() = runTest {
        seedWaiting(1, "Bo")

        // Both the sender guard and the token guard reject a 2-char name — no accidental resolve.
        assertEquals(0, resolver.resolveFrom("Bo"))
        assertFalse(dao.getNoteByIdNow(1)!!.completed)
    }

    @Test
    fun resolvesEveryOpenItemForThatPerson() = runTest {
        seedWaiting(1, "Sarah", title = "Waiting on Sarah: the file")
        seedWaiting(2, "Sarah", title = "Waiting on Sarah: the sign-off")
        seedWaiting(3, "Mike")

        assertEquals(2, resolver.resolveFrom("Sarah Connor"))
        assertTrue(dao.getNoteByIdNow(1)!!.completed)
        assertTrue(dao.getNoteByIdNow(2)!!.completed)
        assertFalse(dao.getNoteByIdNow(3)!!.completed) // Mike is untouched
    }
}
