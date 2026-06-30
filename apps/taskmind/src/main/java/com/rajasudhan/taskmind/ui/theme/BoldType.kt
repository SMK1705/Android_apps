package com.rajasudhan.taskmind.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── "Bold direction" type roles ──────────────────────────────────────────────
// Instrument Serif = editorial headlines/titles, Plus Jakarta Sans = UI/body, JetBrains Mono =
// eyebrows/meta/labels. [BoldType] holds the exact named styles used by the Bold components;
// [BoldTypography] feeds Material3 so built-in widgets adopt the same families.

object BoldType {
    val eyebrow = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 10.sp, letterSpacing = 1.5.sp)
    val screenTitle = TextStyle(fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 40.sp, letterSpacing = 0.3.sp)
    val deckCount = TextStyle(fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 34.sp)
    val deckCountLabel = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 9.sp, letterSpacing = 0.5.sp)
    // ONLY the card/item titles use Times New Roman (Tinos); everything else keeps Instrument Serif.
    val cardTitle = TextStyle(fontFamily = TimesSerif, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 25.sp)
    val noteTitle = TextStyle(fontFamily = TimesSerif, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 23.sp)
    /** Suggestion-card title in the refined design — Hanken Grotesk semibold (not serif). */
    val sugTitle = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 16.5.sp, lineHeight = 21.sp, letterSpacing = (-0.1).sp)
    val emptyTitle = TextStyle(fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 34.sp)
    val heroTitle = TextStyle(fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 24.sp)
    val privacyBig = TextStyle(fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal, fontSize = 64.sp, lineHeight = 58.sp, letterSpacing = 0.5.sp)

    val srcLabel = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, letterSpacing = 0.2.sp)
    val noteSrcMeta = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 9.5.sp, letterSpacing = 0.2.sp)
    val confBadge = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 9.5.sp)
    val detailMeta = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 10.sp)
    val hint = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 10.sp, letterSpacing = 0.3.sp)
    val tabLabel = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 9.5.sp, letterSpacing = 0.3.sp)
    val sectionMono = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 0.5.sp)
    val loadedLabel = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.3.sp)

    val kindChip = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold, fontSize = 10.5.sp)
    val kindTag = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.2.sp)
    val filterChip = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
    val body = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp)
    val button = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    val sourceName = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
    val sourceMeta = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.Normal, fontSize = 11.5.sp)
    val stayChip = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.Medium, fontSize = 12.sp)
    val searchInput = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.Normal, fontSize = 13.5.sp)
}

// Material3 typography so built-in widgets inherit the Bold families. Serif for display/headline,
// Jakarta for titles/body/labels, mono for the smallest label.
val BoldTypography = Typography(
    displayLarge = TextStyle(fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal, fontSize = 52.sp, lineHeight = 56.sp),
    displayMedium = TextStyle(fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal, fontSize = 42.sp, lineHeight = 46.sp),
    displaySmall = TextStyle(fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    bodyMedium = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodySmall = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, letterSpacing = 0.4.sp),
)
