package com.rajasudhan.taskmind.data.local

import java.io.File

/**
 * Non-destructive recovery for an on-disk database that can no longer be opened (a lost/rotated
 * encryption key, a torn file). The cardinal rule after a real incident that lost a user's notes:
 * **never silently delete the database.** Instead we move it — and its WAL/SHM siblings — aside to a
 * timestamped `.corrupt-*` name, so the (still-encrypted) bytes are preserved for possible recovery
 * or debugging rather than destroyed, while the app is free to create a fresh, working DB.
 */
object DatabaseRecovery {

    // The DB's sidecar files that must travel with it (main file = ""). Covers both journal modes:
    // "-wal"/"-shm" (WAL) and "-journal" (the rollback journal Room falls back to on low-RAM devices),
    // matching the full set context.deleteDatabase() used to remove.
    private val SIDE_SUFFIXES = listOf("", "-wal", "-shm", "-journal")

    /**
     * Quarantines [dbFile] (and its `-wal`/`-shm` siblings) by renaming them to `<name>.corrupt-<now>`.
     * Clears any prior quarantine first so repeated incidents can't accumulate on disk (a prior
     * quarantine is already unopenable, so keeping only the latest is safe). If a rename fails, that
     * single file is deleted as a last resort so the app can still recover to a working state rather
     * than crash-loop. [now] is passed in (not read from the clock) so the behaviour is testable.
     */
    fun quarantine(dbFile: File, now: Long) {
        val dir = dbFile.parentFile ?: return
        val quarantinePrefix = dbFile.name + ".corrupt-"
        dir.listFiles { f -> f.name.startsWith(quarantinePrefix) }?.forEach { runCatching { it.delete() } }

        for (suffix in SIDE_SUFFIXES) {
            val src = File(dbFile.path + suffix)
            if (!src.exists()) continue
            val dest = File("${dbFile.path}.corrupt-$now$suffix")
            val moved = runCatching { src.renameTo(dest) }.getOrDefault(false)
            if (!moved) runCatching { src.delete() }
        }
    }

    /**
     * True if a prior [quarantine] left `.corrupt-*` files aside for [dbFile] — i.e. the database was
     * reset under the user at least once. Drives the "your data was reset — restore from a snapshot?"
     * affordance; the marker persists until the next incident or an explicit [clearQuarantine].
     */
    fun hasQuarantine(dbFile: File): Boolean {
        val dir = dbFile.parentFile ?: return false
        val prefix = dbFile.name + ".corrupt-"
        return dir.listFiles { f -> f.name.startsWith(prefix) }?.isNotEmpty() == true
    }

    /**
     * Deletes the quarantined `.corrupt-*` files for [dbFile]. Called once the user has recovered
     * another way (e.g. restored from a JSON snapshot) so the reset nudge clears and the (unopenable)
     * bytes stop taking up space.
     */
    fun clearQuarantine(dbFile: File) {
        val dir = dbFile.parentFile ?: return
        val prefix = dbFile.name + ".corrupt-"
        dir.listFiles { f -> f.name.startsWith(prefix) }?.forEach { runCatching { it.delete() } }
    }
}
