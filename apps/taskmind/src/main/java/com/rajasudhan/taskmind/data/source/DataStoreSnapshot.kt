package com.rajasudhan.taskmind.data.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Snapshots and restores a Preferences [DataStore] for backup (#121), THROUGH the DataStore API rather
 * than by copying its backing file. This is deliberate: DataStore is a single-writer, in-memory-cached
 * singleton, so a raw-file swap would be silently clobbered the next time a concurrent writer (a scan,
 * the notification listener) flushes its stale cache. Going through `edit {}` keeps the live instance
 * authoritative and takes effect immediately, no restart needed.
 *
 * Reuses [PrefsSnapshot]'s typed JSON so dynamic per-account keys ride along without enumeration.
 */
object DataStoreSnapshot {

    /** Typed-JSON snapshot of every key currently in [dataStore]. */
    suspend fun encode(dataStore: DataStore<Preferences>): ByteArray {
        val map = dataStore.data.first().asMap().entries.associate { (key, value) -> key.name to value }
        return PrefsSnapshot.encode(map)
    }

    /** Replaces [dataStore]'s contents with [bytes] (a true clear-then-put, so stale keys don't linger). */
    suspend fun restore(dataStore: DataStore<Preferences>, bytes: ByteArray) {
        val values = PrefsSnapshot.decode(bytes)
        dataStore.edit { prefs ->
            prefs.clear()
            for ((name, value) in values) prefs.put(name, value)
        }
    }

    /** Sets [name]=[value] with the correctly-typed Preferences key for the value's runtime type. */
    private fun MutablePreferences.put(name: String, value: Any?) {
        when (value) {
            is String -> set(stringPreferencesKey(name), value)
            is Boolean -> set(booleanPreferencesKey(name), value)
            is Int -> set(intPreferencesKey(name), value)
            is Long -> set(longPreferencesKey(name), value)
            is Float -> set(floatPreferencesKey(name), value)
            is Set<*> -> set(stringSetPreferencesKey(name), value.map { it.toString() }.toSet())
        }
    }
}
