package com.rajasudhan.taskmind.data.source

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rajasudhan.taskmind.testutil.FakeTaskMindDao
import com.rajasudhan.taskmind.testutil.aNote
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** The #161 auto-snapshot net: write, rotate, the empty-DB and backward-clock guards, and restore. */
@RunWith(RobolectricTestRunner::class)
class SnapshotManagerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val alarms = mockk<AlarmScheduler>(relaxed = true)
    private val geofence = mockk<GeofenceManager>(relaxed = true)

    // Must match SnapshotManager's dir (noBackupFilesDir — kept out of cloud backup).
    private val dir = File(context.noBackupFilesDir, SnapshotManager.SNAPSHOT_DIR)

    private fun manager(dao: FakeTaskMindDao) = SnapshotManager(context, dao, moshi, alarms, geofence)
    private fun jsonFiles() = dir.listFiles()?.filter { it.name.endsWith(".json") } ?: emptyList()

    @Before
    @After
    fun clean() {
        dir.deleteRecursively()
    }

    @Test
    fun snapshot_writesAJsonFileNamedByTimestamp_capturingEveryNote() = runTest {
        val dao = FakeTaskMindDao()
        dao.insertNote(aNote(title = "Pay rent", type = "reminder"))
        dao.insertNote(aNote(title = "Buy milk", type = "todo"))

        val count = manager(dao).snapshot(now = 1000L)

        assertEquals(2, count)
        assertEquals(listOf("notes-1000.json"), jsonFiles().map { it.name })
        assertTrue(jsonFiles().single().readText().contains("Pay rent"))
    }

    @Test
    fun snapshot_withNoNotes_writesNothing_soItCantEvictGoodSnapshots() = runTest {
        val seeded = FakeTaskMindDao().apply { insertNote(aNote(title = "Keep me")) }
        manager(seeded).snapshot(now = 1000L) // one good snapshot on disk

        // The DB is then wiped (a quarantine leaves an empty store): the daily run must NOT overwrite
        // the net with an empty snapshot, or seven such days would rotate every good one away.
        val count = manager(FakeTaskMindDao()).snapshot(now = 2000L)

        assertEquals(0, count) // skipped, not a failure
        assertFalse(File(dir, "notes-2000.json").exists())
        assertTrue(File(dir, "notes-1000.json").exists()) // the good snapshot survives
    }

    @Test
    fun snapshot_rotatesDownToTheNewestSeven() = runTest {
        val dao = FakeTaskMindDao().apply { insertNote(aNote(title = "n")) }
        val m = manager(dao)
        for (t in 1L..9L) m.snapshot(now = t)

        val kept = jsonFiles().map { it.name }
        assertEquals(SnapshotManager.MAX_SNAPSHOTS, kept.size)
        assertTrue(kept.contains("notes-9.json"))  // newest kept
        assertFalse(kept.contains("notes-1.json")) // two oldest rotated out
        assertFalse(kept.contains("notes-2.json"))
    }

    @Test
    fun snapshot_underABackwardClock_keepsTheFreshCapture_ratherThanRotatingItAway() = runTest {
        // A backward wall-clock jump (NTP correction / manual date change) would give a fresh capture a
        // smaller stamp than existing snapshots and rotation would delete it. The monotonic stamp
        // prevents that: the newest write always sorts newest and latest() returns the fresh data.
        val dao = FakeTaskMindDao().apply { insertNote(aNote(title = "a")) }
        val m = manager(dao)
        m.snapshot(now = 5000L)
        dao.insertNote(aNote(title = "b")) // DB grows

        val count = m.snapshot(now = 1000L) // clock jumped back

        assertEquals(2, count)
        assertTrue(m.latest()!!.readText().contains("b")) // the fresh capture is the latest, not the stale one
    }

    @Test
    fun restoreLatest_reinsertsEveryNote_fromTheNewestSnapshot() = runTest {
        val source = FakeTaskMindDao()
        source.insertNote(aNote(title = "old", type = "todo"))
        manager(source).snapshot(now = 1000L)
        source.insertNote(aNote(title = "new", type = "reminder", dueDate = "2026-07-01", dueTime = "09:00"))
        manager(source).snapshot(now = 2000L) // the newest snapshot has both notes

        val target = FakeTaskMindDao() // a freshly-wiped DB
        val restored = manager(target).restoreLatest()

        assertEquals(2, restored)
        assertEquals(setOf("old", "new"), target.getNotesList().map { it.title }.toSet())
    }

    @Test
    fun restoreLatest_reArmsAlarmsAndGeofences_forRestoredNotes() = runTest {
        val source = FakeTaskMindDao()
        source.insertNote(aNote(title = "Pills", type = "reminder", dueDate = "2026-07-01", dueTime = "09:00"))
        source.insertNote(aNote(title = "Home", type = "note", locationLat = 1.0, locationLng = 2.0, locationRadius = 150.0))
        manager(source).snapshot(now = 1000L)

        manager(FakeTaskMindDao()).restoreLatest()

        verify { alarms.schedule(any(), "Pills", "2026-07-01", "09:00", null) } // dated reminder re-armed
        verify { geofence.add(any(), 1.0, 2.0, 150.0f) }                        // place reminder re-registered
    }

    @Test
    fun restoreLatest_withNoSnapshot_returnsNull() = runTest {
        assertNull(manager(FakeTaskMindDao()).restoreLatest())
    }

    @Test
    fun restoreLatest_withACorruptSnapshot_throws_andInsertsNothing() = runTest {
        dir.mkdirs()
        File(dir, "notes-1000.json").writeText("{ this is not valid json")
        val target = FakeTaskMindDao()

        var threw = false
        try {
            manager(target).restoreLatest()
        } catch (_: Exception) {
            threw = true
        }

        assertTrue(threw) // a torn snapshot fails loudly (distinct from "no snapshot" == null)
        assertTrue(target.getNotesList().isEmpty()) // and leaves nothing half-restored
    }
}
