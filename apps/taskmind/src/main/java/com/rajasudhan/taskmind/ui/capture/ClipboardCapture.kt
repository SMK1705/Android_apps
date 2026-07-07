package com.rajasudhan.taskmind.ui.capture

/**
 * Pure clipboard-capture helpers for the "Use copied text" chip in the quick-capture sheet.
 *
 * Split out from the Composable so the two decisions — *should we offer a paste?* and *what text do we
 * insert?* — are plain functions with no Android dependency, unit-testable without Robolectric.
 *
 * Privacy note: whether to SHOW the chip is decided from the clipboard's MIME **description**
 * ([hasPasteableText]), which the platform lets us read without surfacing the "app pasted from your
 * clipboard" toast. The actual content ([captureText]) is only read when the user taps the chip — the
 * moment a paste is expected — so opening the capture sheet never silently peeks at the clipboard.
 */
object ClipboardCapture {

    /**
     * True when the clipboard advertises pasteable text, judged from its declared MIME types alone
     * (`text/plain`, or any other `text` subtype). Reading the description does not trigger the system
     * paste toast.
     */
    fun hasPasteableText(mimeTypes: Array<String>?): Boolean =
        mimeTypes?.any { it == "text/plain" || it.startsWith("text/") } == true

    /** The trimmed clipboard text to insert, or null when there is nothing usable (empty/blank). */
    fun captureText(raw: CharSequence?): String? =
        raw?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}
