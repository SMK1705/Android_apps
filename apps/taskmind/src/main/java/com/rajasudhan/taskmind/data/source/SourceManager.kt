package com.rajasudhan.taskmind.data.source

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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
        // Contacts is an enrichment (resolve a name -> number for the Call button), not a scanned
        // source. It defaults ON (opt-out) so upgrades keep working; READ_CONTACTS still gates it.
        val KEY_CONTACTS_ENABLED = booleanPreferencesKey("contacts_enabled")

        // When each capture source was turned ON (epoch ms). The scanner clamps that source's lookback to
        // this, so enabling a source captures only messages that arrive AFTER — not up to 24h of backfilled
        // history (the app-global 15-min first-run guard doesn't cover a source enabled later). 0 = never
        // stamped (pre-fix / never enabled), which leaves the old behaviour unchanged for existing users.
        val KEY_SMS_ENABLED_AT = longPreferencesKey("sms_enabled_at")
        val KEY_CALL_LOG_ENABLED_AT = longPreferencesKey("call_log_enabled_at")
        val KEY_AUDIO_ENABLED_AT = longPreferencesKey("audio_enabled_at")
        val KEY_IMAGES_ENABLED_AT = longPreferencesKey("images_enabled_at")
        val KEY_EMAIL_ENABLED_AT = longPreferencesKey("email_enabled_at")
        
        val KEY_CALL_RECORDING_PATH = stringPreferencesKey("call_recording_path")
        val KEY_VOICE_RECORDING_PATH = stringPreferencesKey("voice_recording_path")

        // Set of package names whose notifications we process. Empty = monitor all apps.
        val KEY_NOTIFICATION_ALLOWLIST = stringSetPreferencesKey("notification_allowlist")

        // Gmail message ids already processed, so a still-unread email isn't re-run every scan.
        // Keyed per account so multiple mailboxes don't share (and evict) each other's ids.
        fun processedEmailKey(account: String) = stringSetPreferencesKey("processed_email_ids_$account")
        const val MAX_PROCESSED_EMAIL_IDS = 200

        // Pre-multi-account installs stored processed ids under a single global key. Honored as a
        // read-only fallback so an upgraded user doesn't re-process (and re-suggest) already-seen
        // mail. Gmail message ids are globally unique, so merging it into any account is safe.
        val LEGACY_PROCESSED_EMAIL_IDS = stringSetPreferencesKey("processed_email_ids")

        // ISO date (yyyy-MM-dd) the app-usage digest was last generated — gates it to once per day.
        val KEY_LAST_APP_USAGE_DIGEST_DATE = stringPreferencesKey("last_app_usage_digest_date")

        // MediaStore audio ids already transcribed, so a recording isn't re-transcribed every scan.
        val KEY_PROCESSED_AUDIO_IDS = stringSetPreferencesKey("processed_audio_ids")

        // MediaStore image ids already OCR'd, so a screenshot isn't re-read every scan.
        val KEY_PROCESSED_IMAGE_IDS = stringSetPreferencesKey("processed_image_ids")

        // SMS provider _IDs already turned into suggestions — by the real-time observer or a prior
        // scan — so the periodic catch-up doesn't re-run the LLM on a message we've already handled.
        // Capped like the others; the cap keeps the HIGHEST (most recent) ids, since the scan only
        // ever looks at recent messages.
        val KEY_PROCESSED_SMS_IDS = stringSetPreferencesKey("processed_sms_ids")
        const val MAX_PROCESSED_SMS_IDS = 500

        // Notification dedup tokens (StatusBarNotification.key + a hash of the handled content)
        // already turned into suggestions, so neither an unchanged re-post nor the reconnect/boot
        // catch-up sweep of getActiveNotifications() re-runs the LLM on content we already processed.
        // Only the content HASH is stored (never the message text). Capped to bound growth.
        val KEY_PROCESSED_NOTIFICATION_KEYS = stringSetPreferencesKey("processed_notification_keys")
        const val MAX_PROCESSED_NOTIFICATION_KEYS = 300

        // Whether the one-time in-app guide has been shown (re-openable from the ? in the top bar).
        val KEY_HAS_SEEN_GUIDE = booleanPreferencesKey("has_seen_guide")

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
    val isContactsEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_CONTACTS_ENABLED] ?: true }

    // Per-source "enabled at" (epoch ms), 0 when never stamped. The scanner clamps each source's lookback
    // to this so turning a source on captures forward-only, never backfilling history.
    val smsEnabledAt: Flow<Long> = context.dataStore.data.map { it[KEY_SMS_ENABLED_AT] ?: 0L }
    val callLogEnabledAt: Flow<Long> = context.dataStore.data.map { it[KEY_CALL_LOG_ENABLED_AT] ?: 0L }
    val audioEnabledAt: Flow<Long> = context.dataStore.data.map { it[KEY_AUDIO_ENABLED_AT] ?: 0L }
    val imagesEnabledAt: Flow<Long> = context.dataStore.data.map { it[KEY_IMAGES_ENABLED_AT] ?: 0L }
    val emailEnabledAt: Flow<Long> = context.dataStore.data.map { it[KEY_EMAIL_ENABLED_AT] ?: 0L }

    // Maps a source toggle to its "enabled at" key, so [setSourceEnabled] can stamp the turn-on moment.
    private val enabledAtKeys = mapOf(
        KEY_SMS_ENABLED to KEY_SMS_ENABLED_AT,
        KEY_CALL_LOG_ENABLED to KEY_CALL_LOG_ENABLED_AT,
        KEY_AUDIO_ENABLED to KEY_AUDIO_ENABLED_AT,
        KEY_IMAGES_ENABLED to KEY_IMAGES_ENABLED_AT,
        KEY_EMAIL_ENABLED to KEY_EMAIL_ENABLED_AT,
    )

    val callRecordingPath: Flow<String> = context.dataStore.data.map { it[KEY_CALL_RECORDING_PATH] ?: DEFAULT_CALL_RECORDING_PATH }
    val voiceRecordingPath: Flow<String> = context.dataStore.data.map { it[KEY_VOICE_RECORDING_PATH] ?: DEFAULT_VOICE_RECORDING_PATH }

    suspend fun setSourceEnabled(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            val wasEnabled = preferences[key] == true
            preferences[key] = enabled
            // Stamp only the OFF -> ON transition (re-saving an already-on toggle mustn't move the mark),
            // so the scanner starts this source's capture window at the moment it was turned on.
            if (enabled && !wasEnabled) enabledAtKeys[key]?.let { preferences[it] = System.currentTimeMillis() }
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

    /**
     * Gmail message ids already turned into suggestions for [account] (capped to avoid growth).
     * Merges the legacy single-mailbox set so upgraded installs don't re-process old mail.
     */
    fun processedEmailIds(account: String): Flow<Set<String>> =
        context.dataStore.data.map {
            (it[processedEmailKey(account)] ?: emptySet()) + (it[LEGACY_PROCESSED_EMAIL_IDS] ?: emptySet())
        }

    suspend fun addProcessedEmailId(account: String, id: String) {
        val key = processedEmailKey(account)
        context.dataStore.edit { preferences ->
            val updated = (preferences[key] ?: emptySet()) + id
            preferences[key] =
                if (updated.size > MAX_PROCESSED_EMAIL_IDS)
                    updated.toList().takeLast(MAX_PROCESSED_EMAIL_IDS).toSet()
                else updated
        }
    }

    /** Drops an account's dedup set on disconnect, so reconnecting doesn't hide mail (and avoids a leak). */
    suspend fun clearProcessedEmailIds(account: String) {
        context.dataStore.edit { it.remove(processedEmailKey(account)) }
    }

    /** ISO date (yyyy-MM-dd) of the last app-usage digest; empty if never generated. */
    val lastAppUsageDigestDate: Flow<String> =
        context.dataStore.data.map { it[KEY_LAST_APP_USAGE_DIGEST_DATE] ?: "" }

    suspend fun setLastAppUsageDigestDate(date: String) {
        context.dataStore.edit { it[KEY_LAST_APP_USAGE_DIGEST_DATE] = date }
    }

    /** True once the user has seen (or skipped) the in-app guide. */
    val hasSeenGuide: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_HAS_SEEN_GUIDE] ?: false }

    suspend fun setHasSeenGuide(seen: Boolean) {
        context.dataStore.edit { it[KEY_HAS_SEEN_GUIDE] = seen }
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

    /** MediaStore image ids already OCR'd (capped, to avoid unbounded growth). */
    val processedImageIds: Flow<Set<String>> =
        context.dataStore.data.map { it[KEY_PROCESSED_IMAGE_IDS] ?: emptySet() }

    suspend fun addProcessedImageId(id: String) {
        context.dataStore.edit { preferences ->
            val updated = (preferences[KEY_PROCESSED_IMAGE_IDS] ?: emptySet()) + id
            preferences[KEY_PROCESSED_IMAGE_IDS] =
                if (updated.size > MAX_PROCESSED_EMAIL_IDS)
                    updated.toList().takeLast(MAX_PROCESSED_EMAIL_IDS).toSet()
                else updated
        }
    }

    /** SMS provider _IDs already turned into suggestions (capped, keeping the most-recent ids). */
    val processedSmsIds: Flow<Set<String>> =
        context.dataStore.data.map { it[KEY_PROCESSED_SMS_IDS] ?: emptySet() }

    /** Records a batch of processed SMS ids in a single write; caps by keeping the highest ids. */
    suspend fun addProcessedSmsIds(ids: Collection<String>) {
        if (ids.isEmpty()) return
        context.dataStore.edit { preferences ->
            val updated = (preferences[KEY_PROCESSED_SMS_IDS] ?: emptySet()) + ids
            preferences[KEY_PROCESSED_SMS_IDS] =
                if (updated.size > MAX_PROCESSED_SMS_IDS)
                    // SMS ids are monotonic, so "most recent" = numerically highest. Fall back to the
                    // raw string for any non-numeric id so a stray value can't silently evict real ones.
                    updated.sortedByDescending { it.toLongOrNull() ?: Long.MIN_VALUE }
                        .take(MAX_PROCESSED_SMS_IDS).toSet()
                else updated
        }
    }

    /** Notification keys already handled by the live listener (capped, to avoid unbounded growth). */
    val processedNotificationKeys: Flow<Set<String>> =
        context.dataStore.data.map { it[KEY_PROCESSED_NOTIFICATION_KEYS] ?: emptySet() }

    /** Records processed notification keys in a single write; caps by dropping the oldest entries. */
    suspend fun addProcessedNotificationKeys(keys: Collection<String>) {
        if (keys.isEmpty()) return
        context.dataStore.edit { preferences ->
            val updated = (preferences[KEY_PROCESSED_NOTIFICATION_KEYS] ?: emptySet()) + keys
            preferences[KEY_PROCESSED_NOTIFICATION_KEYS] =
                if (updated.size > MAX_PROCESSED_NOTIFICATION_KEYS)
                    updated.toList().takeLast(MAX_PROCESSED_NOTIFICATION_KEYS).toSet()
                else updated
        }
    }
}
