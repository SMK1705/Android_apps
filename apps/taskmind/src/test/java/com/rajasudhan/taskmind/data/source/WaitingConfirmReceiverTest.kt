package com.rajasudhan.taskmind.data.source

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.local.TaskMindDatabase
import com.rajasudhan.taskmind.testutil.aNote
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Got it / Still waiting answers on a "did they deliver?" check, via the extracted
 * [WaitingConfirmReceiver.handle]. Got it is the only path that completes the note; Still waiting
 * must leave the loop — and its follow-up alarm — untouched.
 */
@RunWith(RobolectricTestRunner::class)
class WaitingConfirmReceiverTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: TaskMindDatabase
    private lateinit var dao: TaskMindDao
    private val alarms = mockk<AlarmScheduler>(relaxed = true)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, TaskMindDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.taskMindDao()
    }

    @After
    fun tearDown() = db.close()

    private fun receiver(): WaitingConfirmReceiver {
        val r = WaitingConfirmReceiver()
        r.dao = dao
        r.alarmScheduler = alarms
        return r
    }

    private suspend fun seedPending(): Int =
        dao.insertNote(
            aNote(
                type = "waiting_on", title = "Waiting on David: sales report",
                counterparty = "David", pendingConfirmSince = 5_000L
            )
        ).toInt()

    @Test
    fun gotIt_completesTheNote_clearsTheFlag_andTearsDownTheAlarm() = runTest {
        val id = seedPending()

        receiver().handle(context, WaitingConfirmReceiver.ACTION_GOT_IT, id, "Waiting on David: sales report")

        val note = dao.getNoteByIdNow(id)!!
        assertTrue(note.completed)
        assertNull(note.pendingConfirmSince)
        verify { alarms.cancel(id) }
    }

    @Test
    fun stillWaiting_keepsTheLoopOpen_andNeverCancelsTheAlarm() = runTest {
        val id = seedPending()

        receiver().handle(context, WaitingConfirmReceiver.ACTION_STILL_WAITING, id, "Waiting on David: sales report")

        val note = dao.getNoteByIdNow(id)!!
        assertFalse(note.completed)            // still an open loop
        assertNull(note.pendingConfirmSince)   // just dropped out of the Ready-to-close filter
        verify(exactly = 0) { alarms.cancel(any()) }
    }
}
