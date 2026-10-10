package com.auroro.wallpapers.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.auroro.wallpapers.R
import com.auroro.wallpapers.core.data.FontChoice

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

/** App scale layers on top of Android's own system font scale rather than replacing accessibility. */
fun appTypography(choice: FontChoice, requestedScale: Float): Typography {
    val base = if (choice == FontChoice.OPEN_SANS) AeroTypography else Typography()
    val family = if (choice == FontChoice.OPEN_SANS) AeroFont else FontFamily.Default
    val scale = requestedScale.coerceIn(0.85f, 1.30f)
    fun style(value: TextStyle) = value.copy(
        fontFamily = family,
        fontSize = value.fontSize.scaled(scale),
        lineHeight = value.lineHeight.scaled(scale),
    )
    return base.copy(
        displayLarge = style(base.displayLarge),
        displayMedium = style(base.displayMedium),
        displaySmall = style(base.displaySmall),
        headlineLarge = style(base.headlineLarge),
        headlineMedium = style(base.headlineMedium),
        headlineSmall = style(base.headlineSmall),
        titleLarge = style(base.titleLarge),
        titleMedium = style(base.titleMedium),
        titleSmall = style(base.titleSmall),
        bodyLarge = style(base.bodyLarge),
        bodyMedium = style(base.bodyMedium),
        bodySmall = style(base.bodySmall),
        labelLarge = style(base.labelLarge),
        labelMedium = style(base.labelMedium),
        labelSmall = style(base.labelSmall),
    )
}

private fun TextUnit.scaled(factor: Float): TextUnit =
    if (this == TextUnit.Unspecified) this else this * factor
