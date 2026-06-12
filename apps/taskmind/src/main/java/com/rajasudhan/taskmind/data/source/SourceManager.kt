package com.rajasudhan.taskmind.data.source

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore by preferencesDataStore(name = "source_settings")

@Singleton
class SourceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val KEY_SMS_ENABLED = booleanPreferencesKey("sms_enabled")
        val KEY_CALL_LOG_ENABLED = booleanPreferencesKey("call_log_enabled")
        val KEY_AUDIO_ENABLED = booleanPreferencesKey("audio_enabled")
        val KEY_IMAGES_ENABLED = booleanPreferencesKey("images_enabled")
        val KEY_CALENDAR_ENABLED = booleanPreferencesKey("calendar_enabled")
        val KEY_APP_USAGE_ENABLED = booleanPreferencesKey("app_usage_enabled")
        val KEY_EMAIL_ENABLED = booleanPreferencesKey("email_enabled")
        
        val KEY_CALL_RECORDING_PATH = stringPreferencesKey("call_recording_path")
        val KEY_VOICE_RECORDING_PATH = stringPreferencesKey("voice_recording_path")

        // Set of package names whose notifications we process. Empty = monitor all apps.
        val KEY_NOTIFICATION_ALLOWLIST = stringSetPreferencesKey("notification_allowlist")

        // Gmail message ids already processed, so a still-unread email isn't re-run every scan.
        val KEY_PROCESSED_EMAIL_IDS = stringSetPreferencesKey("processed_email_ids")
        const val MAX_PROCESSED_EMAIL_IDS = 200

        // ISO date (yyyy-MM-dd) the app-usage digest was last generated — gates it to once per day.
        val KEY_LAST_APP_USAGE_DIGEST_DATE = stringPreferencesKey("last_app_usage_digest_date")

        // MediaStore audio ids already transcribed, so a recording isn't re-transcribed every scan.
        val KEY_PROCESSED_AUDIO_IDS = stringSetPreferencesKey("processed_audio_ids")

        const val DEFAULT_CALL_RECORDING_PATH = "/storage/emulated/0/Recordings/Call/"
        const val DEFAULT_VOICE_RECORDING_PATH = "/storage/emulated/0/Recordings/Voice Recorder/"
    }

    val isNotificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIFICATIONS_ENABLED] ?: false }
    val isSmsEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_SMS_ENABLED] ?: false }
    val isCallLogEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_CALL_LOG_ENABLED] ?: false }
    val isAudioEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUDIO_ENABLED] ?: false }
    val isImagesEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_IMAGES_ENABLED] ?: false }
    val isCalendarEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_CALENDAR_ENABLED] ?: false }
    val isAppUsageEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_APP_USAGE_ENABLED] ?: false }
    val isEmailEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_EMAIL_ENABLED] ?: false }

    val callRecordingPath: Flow<String> = context.dataStore.data.map { it[KEY_CALL_RECORDING_PATH] ?: DEFAULT_CALL_RECORDING_PATH }
    val voiceRecordingPath: Flow<String> = context.dataStore.data.map { it[KEY_VOICE_RECORDING_PATH] ?: DEFAULT_VOICE_RECORDING_PATH }

    suspend fun setSourceEnabled(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[key] = enabled
        }
    }
    
    suspend fun setRecordingPath(key: androidx.datastore.preferences.core.Preferences.Key<String>, path: String) {
        context.dataStore.edit { preferences ->
            preferences[key] = path
        }
    }

    /** Package names whose notifications are processed. Empty set = all apps are monitored. */
    val notificationAllowlist: Flow<Set<String>> =
        context.dataStore.data.map { it[KEY_NOTIFICATION_ALLOWLIST] ?: emptySet() }

    suspend fun setNotificationAppEnabled(packageName: String, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            val current = preferences[KEY_NOTIFICATION_ALLOWLIST]?.toMutableSet() ?: mutableSetOf()
            if (enabled) current.add(packageName) else current.remove(packageName)
            preferences[KEY_NOTIFICATION_ALLOWLIST] = current
        }
    }

    /** Gmail message ids already turned into suggestions (capped, to avoid unbounded growth). */
    val processedEmailIds: Flow<Set<String>> =
        context.dataStore.data.map { it[KEY_PROCESSED_EMAIL_IDS] ?: emptySet() }

    suspend fun addProcessedEmailId(id: String) {
        context.dataStore.edit { preferences ->
            val updated = (preferences[KEY_PROCESSED_EMAIL_IDS] ?: emptySet()) + id
            preferences[KEY_PROCESSED_EMAIL_IDS] =
                if (updated.size > MAX_PROCESSED_EMAIL_IDS)
                    updated.toList().takeLast(MAX_PROCESSED_EMAIL_IDS).toSet()
                else updated
        }
    }

    /** ISO date (yyyy-MM-dd) of the last app-usage digest; empty if never generated. */
    val lastAppUsageDigestDate: Flow<String> =
        context.dataStore.data.map { it[KEY_LAST_APP_USAGE_DIGEST_DATE] ?: "" }

    suspend fun setLastAppUsageDigestDate(date: String) {
        context.dataStore.edit { it[KEY_LAST_APP_USAGE_DIGEST_DATE] = date }
    }

    /** MediaStore audio ids already transcribed (capped, to avoid unbounded growth). */
    val processedAudioIds: Flow<Set<String>> =
        context.dataStore.data.map { it[KEY_PROCESSED_AUDIO_IDS] ?: emptySet() }

    suspend fun addProcessedAudioId(id: String) {
        context.dataStore.edit { preferences ->
            val updated = (preferences[KEY_PROCESSED_AUDIO_IDS] ?: emptySet()) + id
            preferences[KEY_PROCESSED_AUDIO_IDS] =
                if (updated.size > MAX_PROCESSED_EMAIL_IDS)
                    updated.toList().takeLast(MAX_PROCESSED_EMAIL_IDS).toSet()
                else updated
        }
    }
}
