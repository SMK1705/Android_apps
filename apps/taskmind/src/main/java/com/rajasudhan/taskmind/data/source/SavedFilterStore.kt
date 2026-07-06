package com.rajasudhan.taskmind.data.source

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rajasudhan.taskmind.data.model.SavedFilter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Its own DataStore file (apart from source_settings) — saved filters are a distinct, user-facing
// concern with a different lifecycle than the scan-source prefs.
private val Context.savedFilterDataStore by preferencesDataStore(name = "saved_filters")

/**
 * Persists the user's pinned smart filters (#123) as a single JSON blob in DataStore. There are only
 * ever a handful, changed rarely, so one serialized array under one key is plenty — no Room table.
 * Serialized with the app's [Moshi] so an arbitrary filter name round-trips safely (a delimiter
 * scheme would break on a name containing the delimiter).
 */
@Singleton
class SavedFilterStore @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi
) {
    private val adapter = moshi.adapter<List<SavedFilter>>(
        Types.newParameterizedType(List::class.java, SavedFilter::class.java)
    )

    private fun decode(json: String?): List<SavedFilter> =
        json?.let { runCatching { adapter.fromJson(it) }.getOrNull() }.orEmpty()

    /** The saved filters in insertion order (newest last). Empty until the user pins one. */
    val filters: Flow<List<SavedFilter>> =
        context.savedFilterDataStore.data.map { decode(it[KEY]) }

    /** Adds [filter], replacing any existing one with the same name (case-insensitive) so re-saving updates. */
    suspend fun save(filter: SavedFilter) {
        context.savedFilterDataStore.edit { prefs ->
            val kept = decode(prefs[KEY]).filterNot { it.name.equals(filter.name, ignoreCase = true) }
            prefs[KEY] = adapter.toJson(kept + filter)
        }
    }

    /** Removes the filter named [name] (case-insensitive); a no-op if none matches. */
    suspend fun delete(name: String) {
        context.savedFilterDataStore.edit { prefs ->
            prefs[KEY] = adapter.toJson(decode(prefs[KEY]).filterNot { it.name.equals(name, ignoreCase = true) })
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("saved_filters_json")
    }
}
