package com.scrollnumb.app.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sin
import kotlin.random.Random

/**
 * Zero-allocation looping particle background, themed:
 *  KAWAII  — pink petals drifting down with sway
 *  DONGHUA — slow rising lantern motes with a gentle candle flicker
 *  LIGHT   — warm dust floating gently
 *  DARK    — disabled (keeps Ink clean)
 *
 * One infinite float drives everything; particles are precomputed seeds,
 * positions derived per frame — no per-frame allocations, no lag.
 */
private data class Seed(
    val x: Float,
    val phase: Float,
    val speed: Float,
    val size: Float,
    val alpha: Float
)

@Composable
fun ThemedParticles(
    themeName: String,
    modifier: Modifier = Modifier
) {
    if (themeName != "KAWAII" && themeName != "DONGHUA" && themeName != "LIGHT") return

    val seeds = remember(themeName) {
        val rng = Random(42)
        // Denser than before but still elegant; low alpha keeps it ambient.
        List(if (themeName == "DONGHUA") 14 else 22) {
            Seed(
                x = rng.nextFloat(),
                phase = rng.nextFloat() * 6.283f,
                speed = 0.35f + rng.nextFloat() * 0.5f,
                size = if (themeName == "DONGHUA") 6f + rng.nextFloat() * 10f
                else 4f + rng.nextFloat() * 5f,
                alpha = 0.06f + rng.nextFloat() * 0.12f
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "particles")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particleT"
    )

    val color = when (themeName) {
        "KAWAII" -> Color(0xFFFF8FAB)
        "DONGHUA" -> Color(0xFFE8C872)
        else -> Color(0xFFC9A86A)
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        for (seed in seeds) {
            // Vertical travel: petals/dust fall, lantern motes rise.
            val progress = ((t * seed.speed + seed.phase / 6.283f) % 1f)
            val y = if (themeName == "DONGHUA") h * (1f - progress) else h * progress
            // Horizontal sway
            val sway = sin((progress * 6.283f * 2) + seed.phase) *
                (if (themeName == "KAWAII") 40f else 14f)
            val x = seed.x * w + sway

            when (themeName) {
                "KAWAII" -> {
                    // Cherry blossom petal: a rotated, tumbling oval.
                    val tumble = progress * 360f * seed.speed + seed.phase * 57f
                    rotate(degrees = tumble, pivot = Offset(x, y)) {
                        drawOval(
                            color = color.copy(alpha = seed.alpha + 0.06f),
                            topLeft = Offset(x - seed.size, y - seed.size * 0.55f),
                            size = androidx.compose.ui.geometry.Size(seed.size * 2f, seed.size * 1.1f)
                        )
                    }
                }
                "DONGHUA" -> {
                    // Glowing lantern orb: warm radial halo + bright core, with
                    // a slow candle-like flicker so the night feels alive. The
                    // old horizontal "mist band" rects read as stray translucent
                    // bars across the screen and were removed.
                    val flicker = 0.75f + 0.25f * sin(t * 6.283f * 3f + seed.phase)
                    drawCircle(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                color.copy(alpha = (seed.alpha + 0.10f) * flicker),
                                color.copy(alpha = 0f)
                            ),
                            center = Offset(x, y),
                            radius = seed.size * 2.4f
                        ),
                        radius = seed.size * 2.4f,
                        center = Offset(x, y)
                    )
                    drawCircle(color.copy(alpha = (seed.alpha + 0.18f) * flicker), seed.size * 0.5f, Offset(x, y))
                }
                else -> drawCircle(color.copy(alpha = seed.alpha), seed.size * 0.7f, Offset(x, y))
            }
        }
    }
}
