package com.scrollnumb.app.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.core.util.SoundFx

data class RingSpec(
    val progress: Float,      // 0..1
    val color: Color,
    val trackAlpha: Float = 0.18f
)

/**
 * Apple-style concentric activity rings, Canvas-drawn with spring fills.
 * Outer → inner as passed in [rings]. Plays a chime the moment any ring
 * reaches completion (once per completion).
 */
@Composable
fun ActivityRings(
    rings: List<RingSpec>,
    modifier: Modifier = Modifier,
    diameter: Dp = 180.dp,
    strokeWidth: Dp = 16.dp,
    gap: Dp = 6.dp,
    soundOnComplete: Boolean = true
) {
    val animated = rings.map { spec ->
        val anim = remember { Animatable(0f) }
        LaunchedEffect(spec.progress) {
            anim.animateTo(
                spec.progress.coerceIn(0f, 1f),
                spring(dampingRatio = 0.8f, stiffness = 80f)
            )
        }
        anim
    }

    // Completion chime — once per ring per completion edge.
    if (soundOnComplete) {
        rings.forEachIndexed { i, spec ->
            var celebrated by remember(i) { mutableStateOf(false) }
            LaunchedEffect(spec.progress) {
                if (spec.progress >= 1f && !celebrated) {
                    celebrated = true
                    SoundFx.playChime()
                } else if (spec.progress < 1f) {
                    celebrated = false
                }
            }
        }
    }

    Box(modifier = modifier.size(diameter)) {
        Canvas(modifier = Modifier.size(diameter)) {
            val strokePx = strokeWidth.toPx()
            val gapPx = gap.toPx()
            rings.forEachIndexed { index, spec ->
                val inset = index * (strokePx + gapPx) + strokePx / 2
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)

                // Track
                drawArc(
                    color = spec.color.copy(alpha = spec.trackAlpha),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
                // Fill
                val sweep = 360f * animated[index].value
                if (sweep > 0.5f) {
                    drawArc(
                        color = spec.color,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}
