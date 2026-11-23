package com.scrollnumb.app.core.designsystem.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ──────────────────────────────────────────────────
//  🌙  Dark Color Scheme
// ──────────────────────────────────────────────────
private val ScrollNumbDarkColorScheme = darkColorScheme(
    primary = ScrollNumbColors.PrimaryDark,
    onPrimary = ScrollNumbColors.OnPrimaryDark,
    primaryContainer = ScrollNumbColors.PrimaryContainerDark,
    onPrimaryContainer = ScrollNumbColors.OnPrimaryContainerDark,
    secondary = ScrollNumbColors.SecondaryDark,
    onSecondary = ScrollNumbColors.OnSecondaryDark,
    secondaryContainer = ScrollNumbColors.SecondaryContainerDark,
    onSecondaryContainer = ScrollNumbColors.OnSecondaryContainerDark,
    tertiary = ScrollNumbColors.TertiaryDark,
    onTertiary = ScrollNumbColors.OnTertiaryDark,
    tertiaryContainer = ScrollNumbColors.TertiaryContainerDark,
    onTertiaryContainer = ScrollNumbColors.OnTertiaryContainerDark,
    background = ScrollNumbColors.BackgroundDark,
    onBackground = ScrollNumbColors.OnBackgroundDark,
    surface = ScrollNumbColors.SurfaceDark,
    onSurface = ScrollNumbColors.OnSurfaceDark,
    surfaceVariant = ScrollNumbColors.SurfaceVariantDark,
    onSurfaceVariant = ScrollNumbColors.OnSurfaceVariantDark,
    error = ScrollNumbColors.ErrorDark,
    onError = ScrollNumbColors.OnErrorDark,
    errorContainer = ScrollNumbColors.ErrorContainerDark,
    onErrorContainer = ScrollNumbColors.OnErrorContainerDark,
    outline = ScrollNumbColors.OutlineDark,
    outlineVariant = ScrollNumbColors.OutlineVariantDark
)

// ──────────────────────────────────────────────────
//  ☀️  Light Color Scheme
// ──────────────────────────────────────────────────
private val ScrollNumbLightColorScheme = lightColorScheme(
    primary = ScrollNumbColors.PrimaryLight,
    onPrimary = ScrollNumbColors.OnPrimaryLight,
    primaryContainer = ScrollNumbColors.PrimaryContainerLight,
    onPrimaryContainer = ScrollNumbColors.OnPrimaryContainerLight,
    secondary = ScrollNumbColors.SecondaryLight,
    onSecondary = ScrollNumbColors.OnSecondaryLight,
    secondaryContainer = ScrollNumbColors.SecondaryContainerLight,
    onSecondaryContainer = ScrollNumbColors.OnSecondaryContainerLight,
    tertiary = ScrollNumbColors.TertiaryLight,
    onTertiary = ScrollNumbColors.OnTertiaryLight,
    tertiaryContainer = ScrollNumbColors.TertiaryContainerLight,
    onTertiaryContainer = ScrollNumbColors.OnTertiaryContainerLight,
    background = ScrollNumbColors.BackgroundLight,
    onBackground = ScrollNumbColors.OnBackgroundLight,
    surface = ScrollNumbColors.SurfaceLight,
    onSurface = ScrollNumbColors.OnSurfaceLight,
    surfaceVariant = ScrollNumbColors.SurfaceVariantLight,
    onSurfaceVariant = ScrollNumbColors.OnSurfaceVariantLight,
    error = ScrollNumbColors.ErrorLight,
    onError = ScrollNumbColors.OnErrorLight,
    errorContainer = ScrollNumbColors.ErrorContainerLight,
    onErrorContainer = ScrollNumbColors.OnErrorContainerLight,
    outline = ScrollNumbColors.OutlineLight,
    outlineVariant = ScrollNumbColors.OutlineVariantLight
)

// ──────────────────────────────────────────────────
//  🌸  Kawaii (fun) Color Scheme — candy pastels
// ──────────────────────────────────────────────────
private val ScrollNumbKawaiiColorScheme = lightColorScheme(
    primary = ScrollNumbColors.KawaiiPink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E4),
    onPrimaryContainer = Color(0xFF5A1A33),
    secondary = ScrollNumbColors.KawaiiLavender,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAE3FB),
    onSecondaryContainer = Color(0xFF2E2255),
    tertiary = ScrollNumbColors.KawaiiMint,
    onTertiary = Color(0xFF00382A),
    tertiaryContainer = Color(0xFFD4F5E7),
    onTertiaryContainer = Color(0xFF00382A),
    background = ScrollNumbColors.KawaiiCream,
    onBackground = ScrollNumbColors.KawaiiInk,
    surface = ScrollNumbColors.KawaiiSurface,
    onSurface = ScrollNumbColors.KawaiiInk,
    surfaceVariant = Color(0xFFFBEAF1),
    onSurfaceVariant = Color(0xFF7A6472),
    error = Color(0xFFE5598A),
    onError = Color.White,
    outline = Color(0xFFC9A8B8),
    outlineVariant = ScrollNumbColors.KawaiiLine
)

// ──────────────────────────────────────────────────
//  🏮  Donghua Color Scheme — ink-wash night
// ──────────────────────────────────────────────────
private val ScrollNumbDonghuaColorScheme = darkColorScheme(
    primary = ScrollNumbColors.DonghuaVermilion,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5A1812),
    onPrimaryContainer = Color(0xFFFFD9D2),
    secondary = ScrollNumbColors.DonghuaGold,
    onSecondary = Color(0xFF3A2A00),
    secondaryContainer = Color(0xFF4A3A14),
    onSecondaryContainer = Color(0xFFF5E3B8),
    tertiary = ScrollNumbColors.DonghuaJade,
    onTertiary = Color(0xFF00382E),
    tertiaryContainer = Color(0xFF14524A),
    onTertiaryContainer = Color(0xFFC3F2E8),
    background = ScrollNumbColors.DonghuaNight,
    onBackground = ScrollNumbColors.DonghuaMist,
    surface = ScrollNumbColors.DonghuaInkWash,
    onSurface = ScrollNumbColors.DonghuaMist,
    surfaceVariant = ScrollNumbColors.DonghuaInkDeep,
    onSurfaceVariant = Color(0xCCDCE3F2),
    error = Color(0xFFFF8A7A),
    onError = Color(0xFF3F0805),
    outline = ScrollNumbColors.DonghuaLine,
    outlineVariant = Color(0xFF31407A)
)

@Composable
fun ScrollNumbTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeName: String = if (darkTheme) "DARK" else "LIGHT",
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeName) {
        "KAWAII" -> ScrollNumbKawaiiColorScheme
        "DONGHUA" -> ScrollNumbDonghuaColorScheme
        "LIGHT" -> ScrollNumbLightColorScheme
        "DARK" -> ScrollNumbDarkColorScheme
        else -> if (darkTheme) ScrollNumbDarkColorScheme else ScrollNumbLightColorScheme
    }
    val effectiveDark = when (themeName) {
        "KAWAII", "LIGHT" -> false
        "DONGHUA", "DARK" -> true
        else -> darkTheme
    }

    // Swap the global dynamic palette — this is what re-colors every screen
    // that reads ScrollNumbColors.* directly (which is most of the app).
    val today = androidx.compose.runtime.produceState(java.time.LocalDate.now()) {
        while (true) {
            value = java.time.LocalDate.now()
            kotlinx.coroutines.delay(60_000L)
        }
    }
    val festival = festivalFor(today.value)
    ScrollNumbColors.applyThemePalette(themeName, darkTheme, festival)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            var context = view.context
            while (context is android.content.ContextWrapper) {
                if (context is Activity) break
                context = context.baseContext
            }
            val activity = context as? Activity
            activity?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !effectiveDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !effectiveDark
            }
        }
    }

    MaterialTheme(
        colorScheme = if (festival == null) colorScheme else colorScheme.copy(
            primary = if (effectiveDark) festival.dark else festival.light,
            onPrimary = if (effectiveDark) Color.Black else Color.White
        ),
        typography = ScrollNumbTypography,
        shapes = ScrollNumbShapes,
        content = { androidx.compose.runtime.CompositionLocalProvider(LocalFestival provides festival, content = content) }
    )
}
