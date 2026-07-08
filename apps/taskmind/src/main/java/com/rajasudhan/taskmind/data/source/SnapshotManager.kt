package com.rajasudhan.taskmind.data.source

import android.content.Context
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.model.Note
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The last-resort data safety net (issue #161). Independently of the encrypted DB and the on-demand
 * "Export Notes" / encrypted-backup features, this keeps a small rolling set of PLAIN-JSON snapshots
 * of every note, written daily by [AutoSnapshotWorker].
 *
 * Plaintext-on-device is acceptable — and necessary — here: the whole point is to survive the one
 * failure the encrypted DB can't (a Keystore reset that destroys the DB key and leaves the ciphertext
 * permanently unopenable — a snapshot encrypted under that same Keystore would die with it). To keep
 * the "never leaves the phone" promise true, snapshots live in [Context.getNoBackupFilesDir] — which
 * Android Auto Backup and device-to-device transfer NEVER include — so the cleartext can't be swept
 * up to the cloud the way [Context.getFilesDir] contents would be.
 */
@Singleton
class SnapshotManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TaskMindDao,
    private val moshi: Moshi,
    private val alarmScheduler: AlarmScheduler,
    private val geofenceManager: GeofenceManager,
) {
    // noBackupFilesDir (NOT filesDir): keeps the plaintext out of Google cloud backup / device transfer.
    private val dir: File get() = File(context.noBackupFilesDir, SNAPSHOT_DIR)
    private val adapter by lazy {
        moshi.adapter<List<Note>>(Types.newParameterizedType(List::class.java, Note::class.java))
    }

    /**
     * Writes a fresh snapshot of all notes, then rotates the directory down to [MAX_SNAPSHOTS] (newest
     * kept). Returns the number of notes captured, or null if the write failed.
     *
     * Skips writing when there are no notes: an empty snapshot is worthless AND actively dangerous —
     * daily empty writes would, within [MAX_SNAPSHOTS] days, rotate away every good snapshot, so the
     * net would erase itself right after a wipe left the DB empty. [now] is injected for tests.
     */
    suspend fun snapshot(now: Long = System.currentTimeMillis()): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val notes = dao.getNotesList()
            if (notes.isEmpty()) return@runCatching 0 // never let an empty snapshot evict good ones
            dir.mkdirs()
            // Monotonic filename stamp: never <= the newest existing snapshot. The name encodes the sort
            // key, so a backward wall clock (NTP correction, manual date change, bad RTC) must not be
            // allowed to make a fresh capture sort as "oldest" — which would rotate it straight away and
            // leave latest() pointing at stale data. Clamp up so ordering always follows write order.
            val stamp = maxOf(now, (snapshots().firstOrNull()?.let { timestampOf(it) } ?: 0L) + 1)
            val json = adapter.indent("  ").toJson(notes)
            // Atomic write: fill a temp file then rename, so a crash mid-write can't leave a truncated
            // (unparseable) snapshot that a later restore would choke on.
            val dest = File(dir, "$PREFIX$stamp$SUFFIX")
            val tmp = File(dir, dest.name + PART_SUFFIX)
            tmp.writeText(json)
            if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
            rotate()
            notes.size
        }.getOrNull()
    }

    /** The most recent snapshot file, or null if none exist. Ordered by the timestamp in the name. */
    fun latest(): File? = snapshots().firstOrNull()

    /** All snapshot files, newest first. */
    fun snapshots(): List<File> =
        dir.listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(SUFFIX) }
            ?.sortedByDescending { timestampOf(it) } ?: emptyList()

    /**
     * Restores notes from the latest snapshot by INSERTING them as new rows (ids reassigned), then
     * re-arming their reminders/geofences under the new ids — a recovery action after the DB was wiped,
     * so the store is expected empty and inserting (not replacing) never clobbers survivors.
     *
     * All-or-nothing: the inserts run in one transaction ([TaskMindDao.insertNotes]), so a mid-way
     * failure rolls back to zero rather than leaving a partial, duplicate-prone restore. Returns the
     * count restored, or null if there is simply no snapshot to restore. THROWS on a corrupt/unreadable
     * snapshot or a DB failure, so the caller can tell "nothing to restore" apart from "restore failed"
     * (and not falsely clear the reset nudge).
     */
    suspend fun restoreLatest(): Int? = withContext(Dispatchers.IO) {
        val file = latest() ?: return@withContext null
        val notes = adapter.fromJson(file.readText()).orEmpty()
        val newIds = dao.insertNotes(notes.map { it.copy(id = 0) }) // atomic; throws roll back the whole batch
        // Best-effort re-arm with the new ids so restored reminders actually alert and places actually
        // fire — a net that silently drops your alarms/geofences isn't a net. Guarded per note so a
        // single failure (revoked permission, Play Services) can't undo the committed content restore.
        notes.forEachIndexed { i, note ->
            val id = newIds.getOrNull(i)?.toInt() ?: return@forEachIndexed
            runCatching { rearm(id, note) }
        }
        notes.size
    }

    private fun rearm(id: Int, note: Note) {
        if (!note.completed && note.type == "reminder" && note.dueDate != null && note.dueTime != null) {
            // Pass the monthly anchor so a restored recurring reminder with a stale slot advances on its
            // intended day-of-month instead of drifting to the 28th.
            alarmScheduler.schedule(id, note.title, note.dueDate, note.dueTime, note.recurrence, note.recurrenceAnchorDay)
        }
        if (note.locationLat != null && note.locationLng != null && note.locationRadius != null) {
            geofenceManager.add(id, note.locationLat, note.locationLng, note.locationRadius.toFloat())
        }
    }

    private fun rotate() {
        snapshots().drop(MAX_SNAPSHOTS).forEach { runCatching { it.delete() } }
    }

    /** Sort key from the filename's embedded stamp; falls back to mtime if it's ever unparseable. */
    private fun timestampOf(f: File): Long =
        f.name.removePrefix(PREFIX).removeSuffix(SUFFIX).toLongOrNull() ?: f.lastModified()

    companion object {
        const val SNAPSHOT_DIR = "snapshots"
        const val MAX_SNAPSHOTS = 7
        private const val PREFIX = "notes-"
        private const val SUFFIX = ".json"
        private const val PART_SUFFIX = ".part"
    }
}
