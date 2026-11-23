package com.scrollnumb.app.core.designsystem.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import kotlin.math.sin

@Composable
fun StreakFlame(
    streakCount: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "flame")
    val flicker by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flicker"
    )

    val sway by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sway"
    )

    val sizeDp = when {
        streakCount >= 30 -> 48
        streakCount >= 14 -> 40
        streakCount >= 7 -> 36
        streakCount >= 3 -> 32
        else -> 28
    }

    val intensityAlpha = (0.6f + (streakCount.coerceAtMost(30) / 30f) * 0.4f)

    Canvas(modifier = modifier.size(sizeDp.dp)) {
        val w = size.width
        val h = size.height
        val swayOffset = sway * w * 0.05f
        val flickerScale = 0.92f + flicker * 0.08f

        val gradient = Brush.verticalGradient(
            colors = listOf(
                ScrollNumbColors.StreakFlameEnd.copy(alpha = intensityAlpha),
                ScrollNumbColors.StreakFlameStart.copy(alpha = intensityAlpha),
                ScrollNumbColors.XpGold.copy(alpha = intensityAlpha * 0.7f)
            )
        )

        val flamePath = Path().apply {
            moveTo(w * 0.5f + swayOffset, h * (1f - flickerScale))
            cubicTo(
                w * 0.2f + swayOffset, h * 0.3f,
                w * 0.0f, h * 0.65f,
                w * 0.3f, h * 0.95f
            )
            cubicTo(
                w * 0.35f, h * 1.0f,
                w * 0.65f, h * 1.0f,
                w * 0.7f, h * 0.95f
            )
            cubicTo(
                w * 1.0f, h * 0.65f,
                w * 0.8f + swayOffset, h * 0.3f,
                w * 0.5f + swayOffset, h * (1f - flickerScale)
            )
            close()
        }

        drawPath(flamePath, gradient)

        // Inner bright core
        val innerPath = Path().apply {
            moveTo(w * 0.5f + swayOffset * 0.5f, h * 0.35f)
            cubicTo(
                w * 0.35f, h * 0.55f,
                w * 0.3f, h * 0.75f,
                w * 0.42f, h * 0.9f
            )
            cubicTo(
                w * 0.45f, h * 0.95f,
                w * 0.55f, h * 0.95f,
                w * 0.58f, h * 0.9f
            )
            cubicTo(
                w * 0.7f, h * 0.75f,
                w * 0.65f, h * 0.55f,
                w * 0.5f + swayOffset * 0.5f, h * 0.35f
            )
            close()
        }

        drawPath(
            innerPath,
            Brush.verticalGradient(
                colors = listOf(
                    ScrollNumbColors.XpGold.copy(alpha = 0.9f),
                    ScrollNumbColors.StreakFlameStart.copy(alpha = 0.5f)
                )
            )
        )
    }
}
