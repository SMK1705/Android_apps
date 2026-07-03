package com.rajasudhan.taskmind.data.source.email

// Google API status codes the Gmail consent flow keys on. Literal (mirroring
// com.google.android.gms.common.api.CommonStatusCodes) so this stays free of Play-services types and
// is unit-testable on the plain JVM.
internal const val GMAIL_STATUS_CANCELLED = 16       // CANCELED — a genuine user cancel
private const val GMAIL_STATUS_DEVELOPER_ERROR = 10  // DEVELOPER_ERROR — SHA-1 / package not registered
private const val GMAIL_STATUS_INTERNAL_ERROR = 8    // INTERNAL_ERROR — usually OAuth-project / propagation
internal const val GMAIL_STATUS_NO_TOKEN = -1        // result returned but carried no access token

/**
 * A short, user-facing reason for a failed Gmail consent, keyed by the Google API status code. The two
 * we actually hit after a signing / OAuth-client change get a specific, actionable hint; anything else
 * shows the raw code (the full exception is in logcat). Replaces a blanket "Gmail connection cancelled"
 * that hid real configuration errors.
 */
internal fun gmailConsentErrorMessage(statusCode: Int): String = when (statusCode) {
    GMAIL_STATUS_DEVELOPER_ERROR ->
        "Gmail sign-in failed (error 10): this build's signing SHA-1 isn't registered for the Android OAuth client."
    GMAIL_STATUS_INTERNAL_ERROR ->
        "Gmail sign-in failed (error 8): the Google Cloud OAuth setup may still be propagating, or is missing the Gmail API, scope, or test user. Wait a few minutes and retry."
    GMAIL_STATUS_NO_TOKEN ->
        "Gmail sign-in didn't return access. Please try again."
    else ->
        "Gmail sign-in failed (error $statusCode). If it keeps failing, check the Google Cloud OAuth setup."
}
