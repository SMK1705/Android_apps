package com.rajasudhan.taskmind.ui.sources

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rajasudhan.taskmind.data.source.SourceManager
import com.rajasudhan.taskmind.data.source.email.GmailAuth
import com.rajasudhan.taskmind.data.source.email.GmailAuthState
import com.rajasudhan.taskmind.data.source.email.GmailCollector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A launchable app the user can choose to monitor for notifications. */
data class MonitorableApp(val packageName: String, val label: String)

@HiltViewModel
class SourcesViewModel @Inject constructor(
    private val sourceManager: SourceManager,
    private val gmailAuth: GmailAuth,
    private val gmailCollector: GmailCollector,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val notificationAllowlist =
        sourceManager.notificationAllowlist.stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    private val _installedApps = MutableStateFlow<List<MonitorableApp>>(emptyList())
    val installedApps: StateFlow<List<MonitorableApp>> = _installedApps

    /** Loads launchable (user-facing) apps for the monitored-apps picker. Cached after first load. */
    fun loadInstalledApps() {
        if (_installedApps.value.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(0)
                .asSequence()
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .filter { it.packageName != context.packageName }
                .map { MonitorableApp(it.packageName, pm.getApplicationLabel(it).toString()) }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
                .toList()
            _installedApps.value = apps
        }
    }

    fun setAppMonitored(packageName: String, enabled: Boolean) {
        viewModelScope.launch { sourceManager.setNotificationAppEnabled(packageName, enabled) }
    }

    val isNotificationsEnabled = sourceManager.isNotificationsEnabled.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val isSmsEnabled = sourceManager.isSmsEnabled.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val isCallLogEnabled = sourceManager.isCallLogEnabled.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val isAudioEnabled = sourceManager.isAudioEnabled.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val isImagesEnabled = sourceManager.isImagesEnabled.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val isCalendarEnabled = sourceManager.isCalendarEnabled.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val isAppUsageEnabled = sourceManager.isAppUsageEnabled.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val isEmailEnabled = sourceManager.isEmailEnabled.stateIn(viewModelScope, SharingStarted.Lazily, false)
    
    val callRecordingPath = sourceManager.callRecordingPath.stateIn(viewModelScope, SharingStarted.Lazily, SourceManager.DEFAULT_CALL_RECORDING_PATH)
    val voiceRecordingPath = sourceManager.voiceRecordingPath.stateIn(viewModelScope, SharingStarted.Lazily, SourceManager.DEFAULT_VOICE_RECORDING_PATH)

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch { sourceManager.setSourceEnabled(SourceManager.KEY_NOTIFICATIONS_ENABLED, enabled) }
    }

    fun toggleSms(enabled: Boolean) {
        viewModelScope.launch { sourceManager.setSourceEnabled(SourceManager.KEY_SMS_ENABLED, enabled) }
    }

    fun toggleCallLog(enabled: Boolean) {
        viewModelScope.launch { sourceManager.setSourceEnabled(SourceManager.KEY_CALL_LOG_ENABLED, enabled) }
    }

    fun toggleAudio(enabled: Boolean) {
        viewModelScope.launch { sourceManager.setSourceEnabled(SourceManager.KEY_AUDIO_ENABLED, enabled) }
    }

    fun toggleImages(enabled: Boolean) {
        viewModelScope.launch { sourceManager.setSourceEnabled(SourceManager.KEY_IMAGES_ENABLED, enabled) }
    }

    fun toggleCalendar(enabled: Boolean) {
        viewModelScope.launch { sourceManager.setSourceEnabled(SourceManager.KEY_CALENDAR_ENABLED, enabled) }
    }

    fun toggleAppUsage(enabled: Boolean) {
        viewModelScope.launch { sourceManager.setSourceEnabled(SourceManager.KEY_APP_USAGE_ENABLED, enabled) }
    }

    // ---- Gmail (OAuth, multi-account) ----
    private val _gmailAccounts = MutableStateFlow(gmailAuth.connectedAccounts.toList())
    val gmailAccounts: StateFlow<List<String>> = _gmailAccounts

    private val _gmailStatus = MutableStateFlow<String?>(null)
    val gmailStatus: StateFlow<String?> = _gmailStatus

    /** One-shot events carrying the OAuth consent intent for the screen to launch. */
    private val _gmailConsent = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val gmailConsent = _gmailConsent.asSharedFlow()

    /** Email source toggle: connect a first account on enable, disconnect every account on disable. */
    fun onEmailToggle(enabled: Boolean) {
        if (!enabled) {
            viewModelScope.launch {
                gmailAuth.disconnectAll()
                sourceManager.setSourceEnabled(SourceManager.KEY_EMAIL_ENABLED, false)
                _gmailAccounts.value = emptyList()
                _gmailStatus.value = null
            }
            return
        }
        if (gmailAuth.connectedAccounts.isEmpty()) {
            addGmailAccount()
        } else {
            viewModelScope.launch { sourceManager.setSourceEnabled(SourceManager.KEY_EMAIL_ENABLED, true) }
        }
    }

    /** Starts the OAuth flow to connect another Google account. */
    fun addGmailAccount() {
        viewModelScope.launch {
            _gmailStatus.value = "Connecting…"
            when (val state = gmailAuth.authorize()) {
                is GmailAuthState.Authorized -> finishConnect(state.accessToken)
                is GmailAuthState.NeedsConsent -> {
                    _gmailStatus.value = null
                    _gmailConsent.emit(state.intentSender)
                }
                is GmailAuthState.Error -> _gmailStatus.value = "Couldn't connect: ${state.message}"
            }
        }
    }

    /** Called by the screen with the result of the consent activity. */
    fun onConsentResult(data: Intent?) {
        viewModelScope.launch {
            val token = gmailAuth.tokenFromConsent(data)
            if (token == null) {
                _gmailStatus.value = "Gmail connection cancelled."
                return@launch
            }
            finishConnect(token)
        }
    }

    /** Disconnects a single connected account; disables the source if it was the last one. */
    fun disconnectGmailAccount(email: String) {
        viewModelScope.launch {
            gmailAuth.disconnect(email)
            val remaining = gmailAuth.connectedAccounts.toList()
            _gmailAccounts.value = remaining
            if (remaining.isEmpty()) {
                sourceManager.setSourceEnabled(SourceManager.KEY_EMAIL_ENABLED, false)
            }
            _gmailStatus.value = null
        }
    }

    private suspend fun finishConnect(token: String) {
        val email = gmailCollector.profileEmail(token)
        if (email.isNullOrBlank()) {
            _gmailStatus.value = "Couldn't read the account email; please try again."
            return
        }
        gmailAuth.addAccount(email)
        _gmailAccounts.value = gmailAuth.connectedAccounts.toList()
        sourceManager.setSourceEnabled(SourceManager.KEY_EMAIL_ENABLED, true)
        _gmailStatus.value = null
    }

    fun updateCallRecordingPath(path: String) {
        viewModelScope.launch { sourceManager.setRecordingPath(SourceManager.KEY_CALL_RECORDING_PATH, path) }
    }

    fun updateVoiceRecordingPath(path: String) {
        viewModelScope.launch { sourceManager.setRecordingPath(SourceManager.KEY_VOICE_RECORDING_PATH, path) }
    }
}
