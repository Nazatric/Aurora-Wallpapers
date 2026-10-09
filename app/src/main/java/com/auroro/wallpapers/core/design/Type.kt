package com.auroro.wallpapers.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.auroro.wallpapers.R

/**
 * Open Sans (SIL OFL 1.1) is used as an openly licensed humanist sans in the spirit of Frutiger and
 * Segoe UI, the faces behind the original Aero look. See ASSET_LICENSES.md.
 */
val AeroFont = FontFamily(
    Font(R.font.opensans_regular, FontWeight.Normal),
    Font(R.font.opensans_semibold, FontWeight.SemiBold),
    Font(R.font.opensans_bold, FontWeight.Bold),
)

private fun t(size: Int, line: Int, weight: FontWeight, spacing: Double = 0.0) =
    TextStyle(fontFamily = AeroFont, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = spacing.sp)

val AeroTypography = Typography(
    displaySmall = t(32, 40, FontWeight.Bold, -0.4),
    headlineLarge = t(28, 36, FontWeight.Bold, -0.3),
    headlineMedium = t(24, 32, FontWeight.SemiBold, -0.2),
    headlineSmall = t(20, 28, FontWeight.SemiBold),
    titleLarge = t(20, 28, FontWeight.SemiBold),
    titleMedium = t(16, 24, FontWeight.SemiBold, 0.1),
    titleSmall = t(14, 20, FontWeight.SemiBold, 0.1),
    bodyLarge = t(16, 24, FontWeight.Normal),
    bodyMedium = t(14, 20, FontWeight.Normal),
    bodySmall = t(12, 16, FontWeight.Normal, 0.1),
    labelLarge = t(14, 20, FontWeight.SemiBold, 0.1),
    labelMedium = t(12, 16, FontWeight.SemiBold, 0.2),
    labelSmall = t(11, 16, FontWeight.SemiBold, 0.3),
)
