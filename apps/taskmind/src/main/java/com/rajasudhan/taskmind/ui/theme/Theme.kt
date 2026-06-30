package com.rajasudhan.taskmind.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/** The user's theme choice. [SYSTEM] follows the OS day/night setting; the others force it. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

// Builds a Material3 ColorScheme from a Bold palette so built-in widgets (switches, fields, dialogs,
// system bars) match the editorial look; the richer Bold tokens ride alongside via LocalBoldColors.
private fun boldColorScheme(c: BoldColors): ColorScheme {
    val onErr = if (c.isDark) Color(0xFF0A0A0C) else Color.White
    return if (c.isDark) {
        darkColorScheme(
            primary = c.accent, onPrimary = BoldOnAccent,
            primaryContainer = c.surface2, onPrimaryContainer = c.ink,
            secondary = c.ink2, onSecondary = c.screen,
            secondaryContainer = c.surface2, onSecondaryContainer = c.ink,
            tertiary = c.accent, onTertiary = BoldOnAccent,
            tertiaryContainer = c.surface2, onTertiaryContainer = c.ink,
            background = c.screen, onBackground = c.ink,
            surface = c.screen, onSurface = c.ink,
            surfaceVariant = c.surface2, onSurfaceVariant = c.ink2,
            surfaceTint = c.accent,
            inverseSurface = c.ink, inverseOnSurface = c.screen,
            error = c.skip, onError = onErr,
            errorContainer = c.skipBg, onErrorContainer = c.skip,
            outline = c.ink3, outlineVariant = c.line,
            scrim = Color(0xCC000000),
            surfaceBright = c.surface2, surfaceDim = c.screen,
            surfaceContainerLowest = c.appBg, surfaceContainerLow = c.surface,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface2,
            surfaceContainerHighest = c.surface2,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = BoldOnAccent,
            primaryContainer = c.surface2, onPrimaryContainer = c.ink,
            secondary = c.ink2, onSecondary = c.screen,
            secondaryContainer = c.surface2, onSecondaryContainer = c.ink,
            tertiary = c.accent, onTertiary = BoldOnAccent,
            tertiaryContainer = c.surface2, onTertiaryContainer = c.ink,
            background = c.screen, onBackground = c.ink,
            surface = c.screen, onSurface = c.ink,
            surfaceVariant = c.surface2, onSurfaceVariant = c.ink2,
            surfaceTint = c.accent,
            inverseSurface = c.ink, inverseOnSurface = c.screen,
            error = c.skip, onError = onErr,
            errorContainer = c.skipBg, onErrorContainer = c.skip,
            outline = c.ink3, outlineVariant = c.line,
            scrim = Color(0xCC000000),
            surfaceBright = c.surface2, surfaceDim = c.screen,
            surfaceContainerLowest = c.appBg, surfaceContainerLow = c.surface,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface2,
            surfaceContainerHighest = c.surface2,
        )
    }
}

@Composable
fun TaskMindTheme(
    // The Bold direction is a fixed brand identity in both light and dark, so wallpaper-based
    // dynamic color (Material You) is intentionally not applied.
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val bold = if (darkTheme) BoldDarkColors else BoldLightColors
    CompositionLocalProvider(LocalBoldColors provides bold) {
        MaterialTheme(
            colorScheme = boldColorScheme(bold),
            typography = BoldTypography,
            shapes = Shapes,
            content = content
        )
    }
}
