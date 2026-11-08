package com.v2ray.ang.ui.compose

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R

/**
 * FrostMost typography. The app ships its own face instead of the platform default, because the
 * whole point of the design language is a visible typographic hierarchy, and a system font cannot
 * guarantee one across devices.
 *
 * Manrope (OFL) carries Cyrillic, which the app needs: ru is one of the two shipped locales.
 */
val FrostFontFamily = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

/**
 * Optical trimming: Manrope has generous vertical metrics, so default line boxes leave a visible
 * gap above caps. Trimming to the cap and baseline makes a headline sit where it looks like it sits,
 * which is most of the difference between "typed text" and "typeset text".
 */
private val TrimmedLines = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Float = 0f,
) = TextStyle(
    fontFamily = FrostFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = TrimmedLines,
)

/**
 * A deliberately small scale of eight steps. Fewer sizes, used consistently, read as designed;
 * a dozen ad-hoc sizes read as accidental. Display and Headline are for the connection state, which
 * is the one thing the screen exists for; Title and Body carry everything else.
 */
val FrostTypography = Typography(
    // Numbers the user stares at: balance, traffic, status.
    displaySmall = style(40, 46, FontWeight.Bold, tracking = (-0.5f)),
    headlineMedium = style(30, 36, FontWeight.Bold, tracking = (-0.3f)),
    headlineSmall = style(24, 30, FontWeight.SemiBold, tracking = (-0.2f)),
    titleLarge = style(20, 26, FontWeight.SemiBold),
    titleMedium = style(17, 23, FontWeight.SemiBold),
    titleSmall = style(15, 20, FontWeight.Medium),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 21, FontWeight.Normal),
    bodySmall = style(13, 18, FontWeight.Normal),
    labelLarge = style(14, 18, FontWeight.SemiBold),
    labelMedium = style(12, 16, FontWeight.SemiBold, tracking = 0.3f),
    labelSmall = style(11, 14, FontWeight.Medium, tracking = 0.5f),
)
