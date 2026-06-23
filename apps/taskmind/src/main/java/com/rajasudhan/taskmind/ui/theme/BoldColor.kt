package com.rajasudhan.taskmind.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── "Bold direction" token system ────────────────────────────────────────────
// A self-contained editorial palette (lime accent, near-black neutrals) provided alongside the
// Material3 scheme via [LocalBoldColors]. Values are 1:1 with the design's THEME map.

/** Ink that sits on the lime accent — hardcoded dark in BOTH themes (matches the design markup). */
val BoldOnAccent = Color(0xFF0A0A0C)

/** Toggle knob color when off, both themes. */
val BoldKnobOff = Color(0xFFFFFFFF)

@Immutable
data class BoldColors(
    val isDark: Boolean,
    val appBg: Color,
    val screen: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val accent: Color,
    val accentGlow: Color,
    val keepBg: Color,
    val skip: Color,
    val skipBg: Color,
    val skipLine: Color,
    val amber: Color,
    val amberSoft: Color,
    val task: Color, val taskSoft: Color,
    val event: Color, val eventSoft: Color,
    val reminder: Color, val reminderSoft: Color,
    val idea: Color, val ideaSoft: Color,
    val navBg: Color,
    val snackBg: Color,
)

val BoldDarkColors = BoldColors(
    isDark = true,
    appBg = Color(0xFF0A0A0C),
    screen = Color(0xFF0E0E11),
    surface = Color(0xFF161619),
    surface2 = Color(0xFF1F1F24),
    ink = Color(0xFFF4F3EF),
    ink2 = Color(0xFFA2A09A),
    ink3 = Color(0xFF6A6862),
    line = Color(0xFF26262B),
    accent = Color(0xFFCDFF4E),
    accentGlow = Color(0x29CDFF4E),
    keepBg = Color(0xFF1B2A09),
    skip = Color(0xFFFF5C5C),
    skipBg = Color(0xFF2A1212),
    skipLine = Color(0xFF4A2228),
    amber = Color(0xFFFFB454),
    amberSoft = Color(0xFF2C2110),
    task = Color(0xFF8AB4FF), taskSoft = Color(0xFF16213A),
    event = Color(0xFF5EE6C0), eventSoft = Color(0xFF0F2B27),
    reminder = Color(0xFFFFB454), reminderSoft = Color(0xFF2C2110),
    idea = Color(0xFFFF8FB3), ideaSoft = Color(0xFF2C1722),
    navBg = Color(0xD10E0E11),
    snackBg = Color(0xFF1F1F24),
)

val BoldLightColors = BoldColors(
    isDark = false,
    appBg = Color(0xFFE6E4DC),
    screen = Color(0xFFF4F2EA),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEFEDE4),
    ink = Color(0xFF16160F),
    ink2 = Color(0xFF5A584E),
    ink3 = Color(0xFF9A988C),
    line = Color(0xFFE2DFD3),
    accent = Color(0xFF5B8C00),
    accentGlow = Color(0x1F5B8C00),
    keepBg = Color(0xFFE6F0CF),
    skip = Color(0xFFD63A33),
    skipBg = Color(0xFFF7E2E0),
    skipLine = Color(0xFFECCAC7),
    amber = Color(0xFFB26A00),
    amberSoft = Color(0xFFF5E6CF),
    task = Color(0xFF3258C8), taskSoft = Color(0xFFE4E9F9),
    event = Color(0xFF0E8F76), eventSoft = Color(0xFFD9F0EA),
    reminder = Color(0xFFB26A00), reminderSoft = Color(0xFFF5E6CF),
    idea = Color(0xFFC13C73), ideaSoft = Color(0xFFF9E2EC),
    navBg = Color(0xD9F4F2EA),
    snackBg = Color(0xFF1C1C16),
)

val LocalBoldColors = staticCompositionLocalOf { BoldDarkColors }

/** Access the active Bold palette: `BoldTheme.colors.accent`, etc. */
object BoldTheme {
    val colors: BoldColors
        @Composable @ReadOnlyComposable
        get() = LocalBoldColors.current
}
