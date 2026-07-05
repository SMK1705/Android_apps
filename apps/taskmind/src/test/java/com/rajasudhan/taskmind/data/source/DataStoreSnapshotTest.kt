package com.rajasudhan.taskmind.data.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The DataStore snapshot/restore path (#121). A real Preferences DataStore round-trip — proving the
 * API-based restore reproduces every key and is a true replace — coverage the raw-file approach couldn't
 * give and that BackupManager itself can't (it needs a device Keystore). Robolectric because the snapshot
 * codec ([PrefsSnapshot]) uses org.json, which is only functional under the Android test shadow.
 */
@RunWith(RobolectricTestRunner::class)
class DataStoreSnapshotTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { File(tmp.root, "$name.preferences_pb") }

    @Test
    fun encodeThenRestore_reproducesEveryKey_intoAFreshStore() = runTest {
        val source = store("source")
        source.edit {
            it[booleanPreferencesKey("sms_enabled")] = true
            it[stringPreferencesKey("call_recording_path")] = "/rec"
            it[longPreferencesKey("last_scan")] = 42L
            it[stringSetPreferencesKey("processed_sms_ids")] = setOf("1", "2")
        }

        val snapshot = DataStoreSnapshot.encode(source)
        val target = store("target")
        DataStoreSnapshot.restore(target, snapshot)

        val restored = target.data.first()
        assertEquals(true, restored[booleanPreferencesKey("sms_enabled")])
        assertEquals("/rec", restored[stringPreferencesKey("call_recording_path")])
        assertEquals(42L, restored[longPreferencesKey("last_scan")])
        assertEquals(setOf("1", "2"), restored[stringSetPreferencesKey("processed_sms_ids")])
    }

    @Test
    fun restore_isATrueReplace_clearingKeysAbsentFromTheSnapshot() = runTest {
        val source = store("source")
        source.edit { it[booleanPreferencesKey("sms_enabled")] = true }
        val snapshot = DataStoreSnapshot.encode(source)

        val target = store("target")
        target.edit { it[stringPreferencesKey("stale_key")] = "leftover" } // present on target, not in backup
        DataStoreSnapshot.restore(target, snapshot)

        val restored = target.data.first()
        assertEquals(true, restored[booleanPreferencesKey("sms_enabled")])
        assertNull(restored[stringPreferencesKey("stale_key")]) // cleared — a true replace, not a merge
    }
}
