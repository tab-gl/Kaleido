package com.tabgl.kaleido.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.tabgl.kaleido.R

private val InstrumentSans = FontFamily(
    Font(R.font.instrument_sans_regular, FontWeight.Normal),
    Font(R.font.instrument_sans_medium, FontWeight.Medium),
    Font(R.font.instrument_sans_semibold, FontWeight.SemiBold),
    Font(R.font.instrument_sans_bold, FontWeight.Bold),
)

private val base = Typography()

val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = InstrumentSans),
    displayMedium = base.displayMedium.copy(fontFamily = InstrumentSans),
    displaySmall = base.displaySmall.copy(fontFamily = InstrumentSans),
    headlineLarge = base.headlineLarge.copy(fontFamily = InstrumentSans),
    headlineMedium = base.headlineMedium.copy(fontFamily = InstrumentSans),
    headlineSmall = base.headlineSmall.copy(fontFamily = InstrumentSans),
    titleLarge = base.titleLarge.copy(fontFamily = InstrumentSans),
    titleMedium = base.titleMedium.copy(fontFamily = InstrumentSans),
    titleSmall = base.titleSmall.copy(fontFamily = InstrumentSans),
    bodyLarge = base.bodyLarge.copy(fontFamily = InstrumentSans),
    bodyMedium = base.bodyMedium.copy(fontFamily = InstrumentSans),
    bodySmall = base.bodySmall.copy(fontFamily = InstrumentSans),
    labelLarge = base.labelLarge.copy(fontFamily = InstrumentSans),
    labelMedium = base.labelMedium.copy(fontFamily = InstrumentSans),
    labelSmall = base.labelSmall.copy(fontFamily = InstrumentSans),
)
