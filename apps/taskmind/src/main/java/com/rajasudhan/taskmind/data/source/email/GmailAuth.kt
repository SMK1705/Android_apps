package com.rajasudhan.taskmind.data.source.email

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.rajasudhan.taskmind.data.source.EgressLogger
import com.rajasudhan.taskmind.data.source.SettingsManager
import com.rajasudhan.taskmind.data.source.SourceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of attempting to authorize read-only Gmail access. */
sealed interface GmailAuthState {
    data class Authorized(val accessToken: String) : GmailAuthState
    /** Consent needed — launch [consentIntent] for a result, then re-authorize the same account. */
    data class NeedsConsent(val consentIntent: Intent) : GmailAuthState
    data class Error(val message: String) : GmailAuthState
}

/**
 * Read-only Gmail access via the legacy **GoogleAuthUtil** token API. Like the Identity Authorization
 * API it matches the app by package name + signing SHA-1 against the Android OAuth client in Google
 * Cloud (no client-id embedded) — but it uses a *different* token/consent path. We moved off the newer
 * `Identity.getAuthorizationClient().authorize()` because its consent step returned a persistent
 * `INTERNAL_ERROR` (status 8) for the restricted `gmail.readonly` scope on this project even with every
 * console setting verified correct; GoogleAuthUtil is the older, battle-tested surface.
 *
 * GoogleAuthUtil is per-account (the account is picked from the system chooser), which maps directly
 * onto the app's multi-mailbox model. The access token is fetched on demand and never persisted; only
 * the connected account email is stored (in [SettingsManager] = EncryptedSharedPreferences).
 *
 * SETUP — the Google Cloud project (whose OAuth consent screen covers this app) must have an
 * **Android OAuth client** registered for:
 *   - package: `com.rajasudhan.taskmind`
 *   - SHA-1:   `CB:5D:68:D2:EA:B0:E1:DD:10:35:F6:F9:1C:35:54:8F:09:81:A3:F4`
 * That is the fingerprint of the committed debug keystore (`apps/taskmind/debug.keystore`) that every
 * local, CI, and OTA "debug-latest" build now signs with — one stable SHA-1 to register once. If the
 * token call fails with DEVELOPER_ERROR, this Android OAuth client is missing or its SHA-1/package
 * doesn't match the installed build.
 */
@Singleton
class GmailAuth @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsManager: SettingsManager,
    private val sourceManager: SourceManager,
    private val egressLogger: EgressLogger,
    private val okHttpClient: OkHttpClient
) {
    /** All connected Gmail accounts (empty = none connected). */
    val connectedAccounts: Set<String>
        get() = settingsManager.gmailAccounts

    /**
     * Fetches a read-only Gmail access token for [accountEmail]. Returns [GmailAuthState.Authorized]
     * when the grant already exists (works silently, even in the background); [GmailAuthState.NeedsConsent]
     * with an intent to launch the one-time consent UI — re-call this for the same account after it
     * returns; or [GmailAuthState.Error]. Requires a specific account (GoogleAuthUtil is per-account).
     */
    suspend fun authorize(accountEmail: String? = null): GmailAuthState {
        if (accountEmail.isNullOrBlank()) return GmailAuthState.Error("No account selected.")
        return withContext(Dispatchers.IO) {
            try {
                egressLogger.record("oauth2.googleapis.com", "Gmail access token")
                val token = GoogleAuthUtil.getToken(appContext, Account(accountEmail, GOOGLE_TYPE), OAUTH2_SCOPE)
                GmailAuthState.Authorized(token)
            } catch (e: UserRecoverableAuthException) {
                // First use (or a revoked grant): Google needs the user to grant the scope; e.intent
                // launches that consent. After it returns OK, re-calling getToken succeeds.
                e.intent?.let { GmailAuthState.NeedsConsent(it) }
                    ?: GmailAuthState.Error(e.message ?: "Gmail consent required.")
            } catch (e: GoogleAuthException) {
                android.util.Log.e(TAG, "getToken failed", e)
                GmailAuthState.Error(e.message ?: e::class.java.simpleName)
            } catch (e: IOException) {
                GmailAuthState.Error("Network error reaching Google — try again.")
            }
        }
    }

    /** Silent token for background scans; null if not currently authorized (caller skips the scan). */
    suspend fun silentAccessToken(accountEmail: String? = null): String? =
        (authorize(accountEmail) as? GmailAuthState.Authorized)?.accessToken

    /**
     * Intent that shows the system Google-account chooser (all Google accounts plus "Add account").
     * Launch it for a result; the chosen email comes back via [accountFromChooser]. Picking the account
     * up front is what lets a *distinct* second mailbox be added and grants this app visibility of that
     * account for [GoogleAuthUtil]. Needs no GET_ACCOUNTS permission on API 23+.
     */
    fun accountChooserIntent(): Intent =
        AccountManager.newChooseAccountIntent(
            null,                   // no pre-selected account
            null as List<Account>?, // null = offer all Google accounts
            arrayOf(GOOGLE_TYPE),
            null, null, null, null
        )

    /** The email the user picked in the account chooser, or null if they cancelled. */
    fun accountFromChooser(data: Intent?): String? =
        data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)

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
            // Invalidate the locally-cached token so a future connect re-fetches a fresh one, then
            // revoke it server-side.
            runCatching { withContext(Dispatchers.IO) { GoogleAuthUtil.clearToken(appContext, token) } }
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
        private const val OAUTH2_SCOPE = "oauth2:$GMAIL_READONLY_SCOPE"
        private const val GOOGLE_TYPE = "com.google"
        private const val TAG = "GmailAuth"
    }
}
