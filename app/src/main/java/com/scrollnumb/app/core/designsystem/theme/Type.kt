package com.scrollnumb.app.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.scrollnumb.app.R

// ──────────────────────────────────────────────────
//  Font families
// ──────────────────────────────────────────────────
val CabinetGrotesk = FontFamily.SansSerif

val Nunito = FontFamily.SansSerif

// ──────────────────────────────────────────────────
//  Type scale
//  Display & headlines: Cabinet Grotesk (geometric grotesque)
//  Body & labels: Nunito (warm humanist sans)
// ──────────────────────────────────────────────────
val ScrollNumbTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = CabinetGrotesk,
        fontWeight = FontWeight.Black,
        fontSize = 64.sp,
        lineHeight = 64.sp,
        letterSpacing = (-1.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = CabinetGrotesk,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 48.sp,
        lineHeight = 52.sp,
        letterSpacing = (-1).sp
    ),
    displaySmall = TextStyle(
        fontFamily = CabinetGrotesk,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = CabinetGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = CabinetGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = CabinetGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = CabinetGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = CabinetGrotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = CabinetGrotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

// ──────────────────────────────────────────────────
//  Display & hero styles used outside M3 typography
//  (e.g. oversized brand moments, counter)
// ──────────────────────────────────────────────────
object ScrollNumbType {
    val NumeralHero = TextStyle(
        fontFamily = CabinetGrotesk,
        fontWeight = FontWeight.Black,
        fontSize = 120.sp,
        lineHeight = 112.sp,
        letterSpacing = (-4).sp
    )
    val NumeralLarge = TextStyle(
        fontFamily = CabinetGrotesk,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 72.sp,
        lineHeight = 72.sp,
        letterSpacing = (-2).sp
    )
    val NumeralMedium = TextStyle(
        fontFamily = CabinetGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1).sp
    )
    val BrandHero = TextStyle(
        fontFamily = CabinetGrotesk,
        fontWeight = FontWeight.Black,
        fontSize = 56.sp,
        lineHeight = 56.sp,
        letterSpacing = (-2).sp
    )
    val BrandDisplay = TextStyle(
        fontFamily = CabinetGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.4).sp
    )
    val Tagline = TextStyle(
        fontFamily = CabinetGrotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 1.5.sp
    )
    val Microcopy = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    )
}
