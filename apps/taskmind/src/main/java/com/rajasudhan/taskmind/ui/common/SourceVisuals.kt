package com.rajasudhan.taskmind.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** A source channel's brand icon + tint, for the leading badge on suggestion/note cards. */
data class SourceVisual(val icon: ImageVector, val tint: Color, val label: String)

/**
 * Maps a free-form `source` string (e.g. "SMS from +1…", "Notification from Gmail", "Voice note")
 * to a recognizable channel icon. The capture channel wins — a Gmail *notification* shows the
 * notification icon, not the envelope. Tints are medium tones that read on both light pastel and
 * deep dark category containers.
 */
fun sourceVisual(source: String): SourceVisual {
    val s = source.lowercase()
    return when {
        "notification" in s -> SourceVisual(Icons.Default.Notifications, Color(0xFF00897B), "Notification")
        "gmail" in s || "email" in s || "mail" in s -> SourceVisual(Icons.Default.Email, Color(0xFFD93025), "Email")
        "sms" in s || "message" in s || "text" in s -> SourceVisual(Icons.Default.Sms, Color(0xFF1E88E5), "SMS")
        "voice" in s || "call" in s || "recording" in s || "audio" in s -> SourceVisual(Icons.Default.Mic, Color(0xFF7E57C2), "Voice")
        "screenshot" in s || "ocr" in s || "image" in s -> SourceVisual(Icons.Default.Image, Color(0xFF3949AB), "Screenshot")
        "usage" in s || "digest" in s || "screen time" in s -> SourceVisual(Icons.Default.BarChart, Color(0xFF00897B), "Usage")
        "calendar" in s -> SourceVisual(Icons.Default.Event, Color(0xFF2E7D32), "Calendar")
        "manual" in s -> SourceVisual(Icons.Default.Edit, Color(0xFF757575), "Manual")
        else -> SourceVisual(Icons.Default.Inbox, Color(0xFF757575), "Source")
    }
}

/**
 * A subtle outlined pill showing the model's extraction confidence ("94%"), colored green/amber/red
 * by band. Outline + tinted text so it reads on any card container. Hidden below a tiny floor where
 * the value is meaningless.
 */
@Composable
fun ConfidencePill(confidence: Double) {
    val pct = (confidence.coerceIn(0.0, 1.0) * 100).roundToInt()
    if (pct <= 0) return
    val color = when {
        pct >= 80 -> Color(0xFF2E9E4F)
        pct >= 50 -> Color(0xFFE0A100)
        else -> Color(0xFFE53935)
    }
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, color, RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 1.dp)
    ) {
        Text(
            text = "$pct%",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}
