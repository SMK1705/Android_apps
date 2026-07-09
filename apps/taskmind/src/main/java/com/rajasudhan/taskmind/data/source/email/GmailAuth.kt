package com.rajasudhan.taskmind.data.source.email

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
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
                // A HARD, non-recoverable auth failure — distinct from the consent path above (which is a
                // UserRecoverableAuthException, a subclass). e.message is GoogleAuthUtil's status string
                // ("NeedPermission" / "BadAuthentication" / "ServiceDisabled" / "NetworkError" / "Unknown"
                // / "ERROR"), the real discriminator — log the full detail plus whether the account is even
                // still on the device (a removed/re-added account loses this app's token grant and hard-
                // fails here even when consent looks fine). Account is masked in logs (PII).
                val onDevice = googleAccountsOnDevice()
                // Classify the hard failure: does a BASIC scope work for this same account? OK / RECOVERABLE
                // (the account signs in, consent just isn't granted yet) both mean ONLY the restricted
                // gmail.readonly scope is blocked (test-user / app-verification / Advanced Protection /
                // supervised account); BROKEN means the account's own Play Services state is broken;
                // UNREACHABLE means Google couldn't be reached. Splits the very different fixes.
                val probe = probeBasicScope(accountEmail)
                android.util.Log.e(
                    TAG,
                    "getToken failed for ${mask(accountEmail)}: ${e.javaClass.simpleName}: ${e.message} " +
                        "(device Google accounts: ${onDevice.size}; basic-scope probe: ${probe.category}(${probe.detail}))",
                    e
                )
                GmailAuthState.Error(authGuidance(e.message, accountEmail, onDevice, probe.category))
            } catch (e: IOException) {
                GmailAuthState.Error("Network error reaching Google — try again.")
            }
        }
    }

    /** Silent token for background scans; null if not currently authorized (caller skips the scan). */
    suspend fun silentAccessToken(accountEmail: String? = null): String? =
        (authorize(accountEmail) as? GmailAuthState.Authorized)?.accessToken

    /**
     * Invalidates a cached access [token] in Google Play Services so the next [authorize] re-fetches a
     * fresh one. This is the app-visible way to clear a stale/poisoned GMS token — an app reinstall does
     * NOT clear it (the cache is keyed by package + account + scope INSIDE Play Services). Call it when
     * the server rejects a token mid-use (a Gmail 401) so the following scan re-authorizes cleanly.
     */
    suspend fun invalidate(token: String) {
        if (token.isBlank()) return
        egressLogger.record("oauth2.googleapis.com", "Gmail token invalidate")
        runCatching { withContext(Dispatchers.IO) { GoogleAuthUtil.clearToken(appContext, token) } }
    }

    /** Names of the Google accounts currently visible on the device; empty if unreadable. */
    private fun googleAccountsOnDevice(): List<String> =
        runCatching { AccountManager.get(appContext).getAccountsByType(GOOGLE_TYPE).map { it.name } }
            .getOrDefault(emptyList())

    /**
     * Diagnostic run only when the Gmail token HARD-fails: try a BASIC (non-restricted) scope for the same
     * account, then [classifyProbe] the outcome. [BasicScopeProbe.OK]/[BasicScopeProbe.RECOVERABLE] mean the
     * account signs in fine and only the restricted Gmail scope is blocked; [BasicScopeProbe.BROKEN] means the
     * account's own Play Services sign-in state is broken; [BasicScopeProbe.UNREACHABLE] means Google was
     * unreachable. The [ProbeResult.detail] is logged and the [ProbeResult.category] folds into
     * [authGuidance]. Never throws.
     */
    private suspend fun probeBasicScope(accountEmail: String): ProbeResult =
        runCatching {
            GoogleAuthUtil.getToken(appContext, Account(accountEmail, GOOGLE_TYPE), OAUTH2_EMAIL_SCOPE)
            ProbeResult(BasicScopeProbe.OK, "OK")
        }.getOrElse { ProbeResult(classifyProbe(it), "${it.javaClass.simpleName}: ${it.message}") }

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

    /** How the basic-scope diagnostic probe came back — its category folds into [authGuidance]. */
    internal enum class BasicScopeProbe {
        /** A basic token issued — the account signs in fully. */
        OK,

        /** Consent merely isn't granted yet (a [UserRecoverableAuthException] — NeedRemoteConsent /
         *  NeedPermission). The account is healthy; like [OK], this means the *restricted* Gmail scope is
         *  the thing that's blocked, NOT the account's device sign-in. */
        RECOVERABLE,

        /** A hard, non-recoverable auth failure — the account's own device sign-in state is broken. */
        BROKEN,

        /** Google couldn't be reached — inconclusive. */
        UNREACHABLE,
    }

    /** [category] drives the guidance; [detail] keeps the raw reason for logcat. */
    internal data class ProbeResult(val category: BasicScopeProbe, val detail: String)

    companion object {
        const val GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
        private const val OAUTH2_SCOPE = "oauth2:$GMAIL_READONLY_SCOPE"
        // A basic, NON-restricted scope for the diagnostic probe (identity only — never gated the way the
        // restricted Gmail scope is).
        private const val OAUTH2_EMAIL_SCOPE = "oauth2:https://www.googleapis.com/auth/userinfo.email"
        private const val GOOGLE_TYPE = "com.google"
        private const val TAG = "GmailAuth"

        /**
         * Classifies a basic-scope probe failure. A [UserRecoverableAuthException] (NeedRemoteConsent /
         * NeedPermission) means the account is healthy and merely needs consent → [BasicScopeProbe.RECOVERABLE],
         * NOT a broken sign-in. IO / NetworkError / Timeout failures are [BasicScopeProbe.UNREACHABLE]; every
         * other hard [GoogleAuthException] is [BasicScopeProbe.BROKEN]. Pure + unit-testable.
         */
        @VisibleForTesting
        internal fun classifyProbe(t: Throwable): BasicScopeProbe = when {
            t is UserRecoverableAuthException -> BasicScopeProbe.RECOVERABLE
            t is IOException -> BasicScopeProbe.UNREACHABLE
            t is GoogleAuthException && isNetworkStatus(t.message) -> BasicScopeProbe.UNREACHABLE
            else -> BasicScopeProbe.BROKEN
        }

        /** GoogleAuthUtil's transient-connectivity status strings. */
        private fun isNetworkStatus(status: String?): Boolean =
            status?.trim() == "NetworkError" || status?.trim() == "Timeout"

        /**
         * Turns a GoogleAuthUtil failure into actionable, user-facing guidance instead of an opaque "ERROR".
         *  - account no longer on the device → re-add it;
         *  - Google unreachable (the main status or the probe) → check the connection;
         *  - [probe] OK or RECOVERABLE → the account signs in but the *restricted Gmail scope* is blocked for
         *    it (Advanced Protection / supervised account / not an OAuth test user / app not yet verified);
         *  - else the account's own sign-in on the device is broken → re-add / clear Play Services.
         * Pure, so it's unit-testable without Play Services.
         */
        @VisibleForTesting
        internal fun authGuidance(
            status: String?,
            accountEmail: String,
            onDeviceEmails: List<String>,
            probe: BasicScopeProbe,
        ): String {
            if (onDeviceEmails.isNotEmpty() && onDeviceEmails.none { it.equals(accountEmail, ignoreCase = true) }) {
                return "This Google account isn't signed in on the device anymore. Add it under Settings → " +
                    "Accounts, open Gmail once, then reconnect it here."
            }
            if (isNetworkStatus(status) || probe == BasicScopeProbe.UNREACHABLE) {
                return "Couldn't reach Google — check your connection and try again."
            }
            if (probe == BasicScopeProbe.OK || probe == BasicScopeProbe.RECOVERABLE) {
                return "This account signs in, but Gmail access is blocked for it specifically. That points to " +
                    "the account (Advanced Protection, or a supervised/Family Link account, blocks third-party " +
                    "Gmail access) or to app authorization (it must be an OAuth test user; a public connection " +
                    "needs Google to verify the app for the Gmail scope). Your other accounts work because they " +
                    "don't have this restriction."
            }
            return "This account's sign-in on the device is stuck. Remove it under Settings → Accounts and " +
                "re-add it — or clear Google Play Services storage — then reconnect."
        }

        /**
         * Masks an email for logs (first char + domain) so account PII is never written to logcat. Shared:
         * also used by RecentDataScanner's Gmail scan logs.
         */
        internal fun mask(email: String?): String {
            if (email.isNullOrBlank()) return "(none)"
            val at = email.indexOf('@')
            return if (at <= 0) "***" else "${email.first()}***${email.substring(at)}"
        }
    }
}
