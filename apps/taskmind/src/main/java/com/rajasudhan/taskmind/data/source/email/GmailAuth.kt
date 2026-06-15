package com.rajasudhan.taskmind.data.source.email

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.rajasudhan.taskmind.data.source.EgressLogger
import com.rajasudhan.taskmind.data.source.SettingsManager
import com.rajasudhan.taskmind.data.source.SourceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Outcome of attempting to authorize read-only Gmail access. */
sealed interface GmailAuthState {
    data class Authorized(val accessToken: String) : GmailAuthState
    data class NeedsConsent(val intentSender: IntentSender) : GmailAuthState
    data class Error(val message: String) : GmailAuthState
}

/**
 * Wraps the Google Identity Services **Authorization API** to get a short-lived OAuth access token
 * for the read-only Gmail scope. No client secret and no client-id are embedded — Google Play
 * services matches the app by package name + signing SHA-1 against the Android OAuth client you
 * register in Google Cloud.
 *
 * The access token is used immediately for Gmail REST calls and never persisted; only the connected
 * account email is stored (in [SettingsManager] = EncryptedSharedPreferences) so the UI can show it.
 */
@Singleton
class GmailAuth @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsManager: SettingsManager,
    private val sourceManager: SourceManager,
    private val egressLogger: EgressLogger,
    private val okHttpClient: OkHttpClient
) {
    private val scopes = listOf(Scope(GMAIL_READONLY_SCOPE))

    private fun request(accountEmail: String?): AuthorizationRequest {
        val builder = AuthorizationRequest.builder().setRequestedScopes(scopes)
        // Pin the request to a specific Google account so each connected mailbox gets its own token.
        // Null lets Play services show its account chooser/consent (used when adding a new account).
        if (!accountEmail.isNullOrBlank()) builder.setAccount(Account(accountEmail, "com.google"))
        return builder.build()
    }

    /** All connected Gmail accounts (empty = none connected). */
    val connectedAccounts: Set<String>
        get() = settingsManager.gmailAccounts

    /**
     * Attempts authorization for [accountEmail] (or the chooser-selected account when null). Returns
     * [GmailAuthState.Authorized] with a token if the grant already exists (works silently, even in
     * the background), [GmailAuthState.NeedsConsent] with an intent to launch the one-time consent
     * UI, or [GmailAuthState.Error].
     */
    suspend fun authorize(accountEmail: String? = null): GmailAuthState = try {
        val result = Identity.getAuthorizationClient(appContext).authorize(request(accountEmail)).await()
        android.util.Log.i(TAG, "authorize: hasResolution=${result.hasResolution()} hasToken=${result.accessToken != null}")
        when {
            result.hasResolution() -> {
                val sender = result.pendingIntent?.intentSender
                if (sender != null) GmailAuthState.NeedsConsent(sender)
                else GmailAuthState.Error("Consent required but no intent was provided")
            }
            result.accessToken != null -> GmailAuthState.Authorized(result.accessToken!!)
            else -> GmailAuthState.Error("No access token returned")
        }
    } catch (e: Exception) {
        android.util.Log.e(TAG, "authorize failed", e)
        GmailAuthState.Error(e.message ?: e::class.java.simpleName)
    }

    /** Silent token for background scans; null if not currently authorized (caller skips the scan). */
    suspend fun silentAccessToken(accountEmail: String? = null): String? =
        (authorize(accountEmail) as? GmailAuthState.Authorized)?.accessToken

    /** Extracts the access token from the consent activity result intent. */
    fun tokenFromConsent(data: Intent?): String? =
        runCatching {
            Identity.getAuthorizationClient(appContext)
                .getAuthorizationResultFromIntent(data)
                .accessToken
        }.onFailure { android.util.Log.e(TAG, "tokenFromConsent failed", it) }.getOrNull()

    fun addAccount(email: String) {
        settingsManager.addGmailAccount(email)
    }

    /** Removes [email] from the connected set and best-effort revokes its token with Google. */
    suspend fun disconnect(email: String) {
        val token = runCatching { silentAccessToken(email) }.getOrNull()
        settingsManager.removeGmailAccount(email)
        // Drop this account's dedup set so reconnecting later doesn't silently skip new mail.
        runCatching { sourceManager.clearProcessedEmailIds(email) }
        if (!token.isNullOrBlank()) {
            egressLogger.record("oauth2.googleapis.com", "Gmail token revoke")
            runCatching {
                withContext(Dispatchers.IO) {
                    val req = Request.Builder()
                        .url("https://oauth2.googleapis.com/revoke?token=$token")
                        .post(ByteArray(0).toRequestBody(null))
                        .build()
                    okHttpClient.newCall(req).execute().close()
                }
            }
        }
    }

    /** Disconnects every connected account (used when the Email source is turned off). */
    suspend fun disconnectAll() {
        connectedAccounts.toList().forEach { disconnect(it) }
    }

    companion object {
        const val GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
        private const val TAG = "GmailAuth"
    }
}

/** Suspends on a Play-services [Task] without pulling in the coroutines-play-services artifact. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}
