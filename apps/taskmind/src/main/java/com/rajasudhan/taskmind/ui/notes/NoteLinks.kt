package com.rajasudhan.taskmind.ui.notes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

// URLs, emails, +intl phone numbers, and bare 10-digit numbers. The 10-digit / +intl shapes avoid
// matching dates like 2026-06-15 (which contain dashes, not 10 consecutive digits).
private val LINK_REGEX = Regex(
    """https?://\S+|www\.\S+|[\w.+-]+@[\w-]+\.[\w.-]+|\+\d[\d\s-]{7,}\d|\b\d{10}\b"""
)

/**
 * Renders [text] with tappable URLs, emails and phone numbers using [LinkAnnotation.Url], so taps go
 * through the platform UriHandler (browser / mail app / dialer). It surfaces actions — it never
 * auto-dials or auto-sends — which fits the approve-before-act design.
 */
fun linkifyNoteBody(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    val styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    var last = 0
    for (m in LINK_REGEX.findAll(text)) {
        if (m.range.first > last) append(text.substring(last, m.range.first))
        val token = m.value
        val uri = when {
            token.contains("@") && !token.contains("://") -> "mailto:$token"
            token.startsWith("http", ignoreCase = true) -> token
            token.startsWith("www", ignoreCase = true) -> "http://$token"
            else -> "tel:" + token.filter { it.isDigit() || it == '+' }
        }
        withLink(LinkAnnotation.Url(uri, styles)) { append(token) }
        last = m.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}
