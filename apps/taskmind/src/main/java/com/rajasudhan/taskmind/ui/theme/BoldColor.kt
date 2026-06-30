package com.rajasudhan.taskmind.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── "Bold direction" token system ────────────────────────────────────────────
// A self-contained editorial palette (lime accent, near-black neutrals) provided alongside the
// Material3 scheme via [LocalBoldColors]. Values are 1:1 with the design handoff's THEME map
// (apps/taskmind/design/handoff/TaskMind.dc.html).

/** Ink that sits on the lime accent — near-black in BOTH themes (matches the design's accent-ink). */
val BoldOnAccent = Color(0xFF14160B)

/** Toggle knob color when off, both themes. */
val BoldKnobOff = Color(0xFFFFFFFF)

@Immutable
data class BoldColors(
    val isDark: Boolean,
    val appBg: Color,
    val screen: Color,
    val bg2: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    /** Solid desaturated grey for secondary text/metadata (the design's --tm-muted), distinct from
     *  the alpha-over-ink [ink2]/[ink3]. */
    val muted: Color,
    val line: Color,
    val line2: Color,
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
    appBg = Color(0xFF0C0C0E),
    screen = Color(0xFF0C0C0E),
    bg2 = Color(0xFF141417),
    surface = Color(0xFF17171A),
    surface2 = Color(0xFF1F1F23),
    ink = Color(0xFFF4F3EE),
    ink2 = Color(0x8FF4F3EE),
    ink3 = Color(0x57F4F3EE),
    muted = Color(0xFF9A988F),
    line = Color(0x17F4F3EE),
    line2 = Color(0x26F4F3EE),
    accent = Color(0xFFCFF54A),
    accentGlow = Color(0x29CFF54A),
    keepBg = Color(0x29CFF54A),
    skip = Color(0xFFFF6F6F),
    skipBg = Color(0xFF2A1212),
    skipLine = Color(0xFF4A2228),
    amber = Color(0xFFFFC24B),
    amberSoft = Color(0x26FFC24B),
    task = Color(0xFF6AA6FF), taskSoft = Color(0x266AA6FF),
    event = Color(0xFF2FD8C3), eventSoft = Color(0x262FD8C3),
    reminder = Color(0xFFFFC24B), reminderSoft = Color(0x26FFC24B),
    idea = Color(0xFFF58FC2), ideaSoft = Color(0x26F58FC2),
    navBg = Color(0xEB121215),
    snackBg = Color(0xFF232327),
)

val BoldLightColors = BoldColors(
    isDark = false,
    appBg = Color(0xFFF3F1EA),
    screen = Color(0xFFF3F1EA),
    bg2 = Color(0xFFEAE7DD),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF6F4EE),
    ink = Color(0xFF15150F),
    ink2 = Color(0x9415150F),
    ink3 = Color(0x6615150F),
    muted = Color(0xFF6E6C63),
    line = Color(0x1A15150F),
    line2 = Color(0x2915150F),
    accent = Color(0xFFAAD400),
    accentGlow = Color(0x2EAAD400),
    keepBg = Color(0x2EAAD400),
    skip = Color(0xFFDD463F),
    skipBg = Color(0xFFF7E2E0),
    skipLine = Color(0xFFECCAC7),
    amber = Color(0xFFBD7D00),
    amberSoft = Color(0x24BD7D00),
    task = Color(0xFF2E6BE6), taskSoft = Color(0x1F2E6BE6),
    event = Color(0xFF0A9B8C), eventSoft = Color(0x210A9B8C),
    reminder = Color(0xFFBD7D00), reminderSoft = Color(0x24BD7D00),
    idea = Color(0xFFD6519A), ideaSoft = Color(0x1FD6519A),
    navBg = Color(0xE6F3F1EA),
    snackBg = Color(0xFFFFFFFF),
)

val LocalBoldColors = staticCompositionLocalOf { BoldDarkColors }

/** Access the active Bold palette: `BoldTheme.colors.accent`, etc. */
object BoldTheme {
    val colors: BoldColors
        @Composable @ReadOnlyComposable
        get() = LocalBoldColors.current
}
