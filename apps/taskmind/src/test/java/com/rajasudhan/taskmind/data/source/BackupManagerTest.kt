package com.rajasudhan.taskmind.data.source

import com.rajasudhan.taskmind.data.local.TaskMindDatabase
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The restore-time schema guard (#B1): a backup made by a NEWER app build must be refused up front —
 * Room can't downgrade, so committing it would swap in a DB that fails to open on the mandatory restart
 * and gets quarantined into an empty database, after we'd already reported "restore complete".
 */
class BackupManagerTest {

    @Test
    fun newerSchema_isRejected_withAClearMessage() {
        val msg = BackupManager.newerSchemaRejectionMessage(TaskMindDatabase.SCHEMA_VERSION + 1)
        assertTrue("a newer-schema backup must be refused", msg != null && msg.contains("newer version"))
    }

    @Test
    fun sameSchema_isAccepted() {
        assertNull(BackupManager.newerSchemaRejectionMessage(TaskMindDatabase.SCHEMA_VERSION))
    }

    @Test
    fun olderSchema_isAccepted_soRoomCanMigrateItForward() {
        assertNull(BackupManager.newerSchemaRejectionMessage(TaskMindDatabase.SCHEMA_VERSION - 1))
    }
}
