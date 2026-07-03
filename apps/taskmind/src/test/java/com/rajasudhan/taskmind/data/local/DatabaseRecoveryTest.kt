package com.rajasudhan.taskmind.data.local

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The non-destructive DB quarantine: an unopenable database must be moved aside, never deleted. */
class DatabaseRecoveryTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("dbrec").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun db() = File(dir, "taskmind_db")

    @Test
    fun quarantine_movesDbAndAllSiblingsAside_preservingBytes() {
        db().writeText("real-notes")
        File(dir, "taskmind_db-wal").writeText("wal")
        File(dir, "taskmind_db-shm").writeText("shm")
        File(dir, "taskmind_db-journal").writeText("journal") // rollback-journal mode (low-RAM devices)

        DatabaseRecovery.quarantine(db(), now = 1000L)

        // The live paths are cleared so Room can create a fresh DB…
        assertFalse(db().exists())
        assertFalse(File(dir, "taskmind_db-wal").exists())
        assertFalse(File(dir, "taskmind_db-shm").exists())
        assertFalse(File(dir, "taskmind_db-journal").exists())
        // …but the bytes are preserved under the quarantine name, NOT destroyed.
        val quarantined = File(dir, "taskmind_db.corrupt-1000")
        assertTrue(quarantined.exists())
        assertEquals("real-notes", quarantined.readText())
        assertTrue(File(dir, "taskmind_db.corrupt-1000-wal").exists())
        assertTrue(File(dir, "taskmind_db.corrupt-1000-shm").exists())
        assertTrue(File(dir, "taskmind_db.corrupt-1000-journal").exists())
    }

    @Test
    fun quarantine_clearsAPriorQuarantine_soTheyDoNotAccumulate() {
        File(dir, "taskmind_db.corrupt-1").writeText("older-incident")
        File(dir, "taskmind_db.corrupt-1-wal").writeText("older-wal")
        db().writeText("newer")

        DatabaseRecovery.quarantine(db(), now = 2000L)

        assertFalse(File(dir, "taskmind_db.corrupt-1").exists())     // prior quarantine cleared
        assertFalse(File(dir, "taskmind_db.corrupt-1-wal").exists())
        assertTrue(File(dir, "taskmind_db.corrupt-2000").exists())   // only the latest kept
    }

    @Test
    fun quarantine_withNoWalOrShm_movesJustTheMainFile() {
        db().writeText("data")

        DatabaseRecovery.quarantine(db(), now = 3000L)

        assertFalse(db().exists())
        assertTrue(File(dir, "taskmind_db.corrupt-3000").exists())
        assertFalse(File(dir, "taskmind_db.corrupt-3000-wal").exists()) // none created
    }

    @Test
    fun quarantine_whenNothingExists_isANoOp() {
        DatabaseRecovery.quarantine(db(), now = 4000L) // must not throw
        assertFalse(File(dir, "taskmind_db.corrupt-4000").exists())
    }
}
