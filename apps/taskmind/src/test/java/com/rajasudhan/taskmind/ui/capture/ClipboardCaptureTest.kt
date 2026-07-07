package com.rajasudhan.taskmind.ui.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure decisions behind the "Use copied text" capture chip (#122). */
class ClipboardCaptureTest {

    @Test
    fun hasPasteableText_trueForPlainText() {
        assertTrue(ClipboardCapture.hasPasteableText(arrayOf("text/plain")))
    }

    @Test
    fun hasPasteableText_trueForAnyTextSubtype() {
        // The chip should also offer a paste for e.g. rich text on the clipboard.
        assertTrue(ClipboardCapture.hasPasteableText(arrayOf("text/html")))
    }

    @Test
    fun hasPasteableText_falseWhenOnlyNonText() {
        assertFalse(ClipboardCapture.hasPasteableText(arrayOf("image/png")))
    }

    @Test
    fun hasPasteableText_falseForNullOrEmpty() {
        assertFalse(ClipboardCapture.hasPasteableText(null))
        assertFalse(ClipboardCapture.hasPasteableText(emptyArray()))
    }

    @Test
    fun captureText_trimsAndReturnsContent() {
        assertEquals("buy milk on the way home", ClipboardCapture.captureText("  buy milk on the way home\n"))
    }

    @Test
    fun captureText_nullWhenNothingUsable() {
        assertNull(ClipboardCapture.captureText(null))
        assertNull(ClipboardCapture.captureText(""))
        assertNull(ClipboardCapture.captureText("   \n\t "))
    }
}
