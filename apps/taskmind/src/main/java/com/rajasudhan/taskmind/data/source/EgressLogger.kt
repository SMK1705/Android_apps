package com.rajasudhan.taskmind.data.source

import android.content.SharedPreferences
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = true)
data class EgressEvent(
    val timestamp: Long,
    val host: String,
    val purpose: String
)

/**
 * Records every time data leaves the device (currently only optional cloud LLM calls).
 * Stores metadata ONLY — host, purpose, time — never the content that was sent.
 * Persisted in EncryptedSharedPreferences so the audit log itself is encrypted at rest.
 */
@Singleton
class EgressLogger @Inject constructor(
    private val prefs: SharedPreferences,
    moshi: Moshi
) {
    private val adapter = moshi.adapter<List<EgressEvent>>(
        Types.newParameterizedType(List::class.java, EgressEvent::class.java)
    )

    private val _events = MutableStateFlow(load())
    val events: StateFlow<List<EgressEvent>> = _events

    @Synchronized
    fun record(host: String, purpose: String) {
        val updated = (listOf(EgressEvent(System.currentTimeMillis(), host, purpose)) + _events.value)
            .take(MAX_EVENTS)
        _events.value = updated
        prefs.edit().putString(KEY, adapter.toJson(updated)).apply()
    }

    fun clear() {
        _events.value = emptyList()
        prefs.edit().remove(KEY).apply()
    }

    private fun load(): List<EgressEvent> = try {
        prefs.getString(KEY, null)?.let { adapter.fromJson(it) } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    companion object {
        private const val KEY = "egress_log"
        private const val MAX_EVENTS = 100
    }
}
