package com.rajasudhan.taskmind.data.source.email

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.rajasudhan.taskmind.data.source.EgressLogger
import com.rajasudhan.taskmind.data.source.SettingsManager
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
    private val egressLogger: EgressLogger,
    private val okHttpClient: OkHttpClient
) {
    private val scopes = listOf(Scope(GMAIL_READONLY_SCOPE))

    private fun request(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(scopes).build()

    val connectedAccount: String?
        get() = settingsManager.gmailAccount.ifBlank { null }

    /**
     * Attempts authorization. Returns [GmailAuthState.Authorized] with a token if the grant already
     * exists (works silently, even in the background), [GmailAuthState.NeedsConsent] with an intent
     * to launch the one-time consent UI, or [GmailAuthState.Error].
     */
    suspend fun authorize(): GmailAuthState = try {
        val result = Identity.getAuthorizationClient(appContext).authorize(request()).await()
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
        GmailAuthState.Error(e.message ?: e::class.java.simpleName)
    }

    /** Silent token for background scans; null if not currently authorized (caller skips the scan). */
    suspend fun silentAccessToken(): String? =
        (authorize() as? GmailAuthState.Authorized)?.accessToken

    /** Extracts the access token from the consent activity result intent. */
    fun tokenFromConsent(data: Intent?): String? =
        runCatching {
            Identity.getAuthorizationClient(appContext)
                .getAuthorizationResultFromIntent(data)
                .accessToken
        }.getOrNull()

    fun storeAccount(email: String) {
        settingsManager.gmailAccount = email
    }

    /** Clears local connection state and best-effort revokes the token with Google. */
    suspend fun disconnect() {
        val token = runCatching { silentAccessToken() }.getOrNull()
        settingsManager.gmailAccount = ""
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

    companion object {
        const val GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
    }
}

/** Suspends on a Play-services [Task] without pulling in the coroutines-play-services artifact. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}
