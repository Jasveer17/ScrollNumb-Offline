package com.scrollnumb.app.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors

/**
 * Warm, paper-feel background for the whole app.
 * Two static warm radial glows + subtle vignette — no animated gradient.
 * Reads as designed, not as a default theme wash.
 */
@Composable
fun WarmGrainBackground(
    modifier: Modifier = Modifier,
    base: Color = ScrollNumbColors.BackgroundDark,
    glowA: Color = ScrollNumbColors.SaffronMuted,
    glowB: Color = ScrollNumbColors.SaffronSoft
) {
    val brush = remember(base, glowA, glowB) {
        Brush.radialGradient(
            colors = listOf(glowA, Color.Transparent),
            center = Offset(120f, 80f),
            radius = 720f
        )
    }
    val brush2 = remember(base, glowB) {
        Brush.radialGradient(
            colors = listOf(glowB, Color.Transparent),
            center = Offset(900f, 1600f),
            radius = 900f
        )
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(base)
            .background(brush)
            .background(brush2)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Subtle inner border vignette
            val vignette = Brush.radialGradient(
                colors = listOf(Color.Transparent, base.copy(alpha = 0.55f)),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = size.maxDimension * 0.85f
            )
            drawRect(brush = vignette)
        }
    }
}
