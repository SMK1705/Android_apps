package com.rajasudhan.taskmind.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.rajasudhan.taskmind.R

// The "Bold direction" type system — three bundled, OFL-licensed families (no network fetch, in
// keeping with TaskMind's on-device promise). Instrument Serif carries the editorial headlines,
// Plus Jakarta Sans the body/UI, JetBrains Mono the meta labels, eyebrows, and counts.

/** Editorial serif for display + headline + card-title roles. Ships Regular and Italic. */
val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif, FontWeight.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

/**
 * Times New Roman look-alike — Tinos is OFL-licensed and metrically identical to Times New Roman
 * (the real one is proprietary and can't be bundled). Used for the serif/headline roles.
 */
val TimesSerif = FontFamily(
    Font(R.font.tinos_regular, FontWeight.Normal),
    Font(R.font.tinos_bold, FontWeight.Bold),
    Font(R.font.tinos_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.tinos_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

/** Body / UI sans, weights 400–700. */
val PlusJakarta = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
)

/**
 * Body / UI sans for the refined Bold direction (the design handoff). One OFL-licensed variable
 * font; each weight is rendered from its `wght` axis, so no extra files. Weights 400–800.
 */
val HankenGrotesk = FontFamily(
    Font(R.font.hanken_grotesk, FontWeight.Normal),
    Font(R.font.hanken_grotesk, FontWeight.Medium),
    Font(R.font.hanken_grotesk, FontWeight.SemiBold),
    Font(R.font.hanken_grotesk, FontWeight.Bold),
    Font(R.font.hanken_grotesk, FontWeight.ExtraBold),
)

/** Monospace for eyebrows, meta, source labels, counts. Weights 400/500/700. */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)
