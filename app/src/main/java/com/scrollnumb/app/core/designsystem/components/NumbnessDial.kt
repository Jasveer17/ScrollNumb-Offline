package com.scrollnumb.app.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * The ScrollNumb hero indicator.
 * A stylized thumb that warms up (saffron) at 0 scrolls and cools
 * down to a numb blue-purple as you exceed your daily cap.
 * Replaces the old emoji-based StatusEmoji.
 */
@Composable
fun NumbnessDial(
    count: Int,
    limit: Int,
    modifier: Modifier = Modifier,
    sizeDp: androidx.compose.ui.unit.Dp = 220.dp
) {
    val ratio = if (limit > 0) (count.toFloat() / limit) else 0f
    val intensity = ratio.coerceIn(0f, 1.6f) / 1.6f

    val pulse = rememberInfiniteTransition(label = "numbPulse")
    val breath by pulse.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    val ringSweep = remember { Animatable(0f) }
    LaunchedEffect(ratio) {
        ringSweep.animateTo(
            targetValue = ratio.coerceAtMost(1f),
            animationSpec = tween(700)
        )
    }

    val warm = ScrollNumbColors.Saffron
    val mid = ScrollNumbColors.Coral
    val cold = Color(0xFF6B7BFF)
    val dead = Color(0xFF464066)

    val handTop = lerpColor(warm, mid, intensity.coerceAtMost(0.5f) * 2f)
    val handBottom = lerpColor(mid, cold, ((intensity - 0.5f).coerceAtLeast(0f) * 2f).coerceAtMost(1f))
    val ringColor = lerpColor(warm, dead, intensity)

    Canvas(modifier = modifier.then(Modifier.size(sizeDp))) {
        val canvasSize = this.size.minDimension
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val outerR = canvasSize * 0.48f
        val stroke = 10f

        // Outer ring (track)
        drawCircle(
            color = ScrollNumbColors.InkWhisper,
            radius = outerR,
            center = center,
            style = Stroke(width = stroke)
        )

        // Progress ring (swept)
        val sweepDeg = 360f * ringSweep.value
        drawArc(
            color = ringColor,
            startAngle = -90f,
            sweepAngle = sweepDeg,
            useCenter = false,
            topLeft = Offset(center.x - outerR, center.y - outerR),
            size = Size(outerR * 2f, outerR * 2f),
            style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )

        // Thumb body
        val bodyR = outerR * 0.62f * breath
        val bodyCenter = Offset(center.x, center.y + outerR * 0.05f)

        val gradient = Brush.verticalGradient(
            colors = listOf(handTop, handBottom),
            startY = bodyCenter.y - bodyR,
            endY = bodyCenter.y + bodyR
        )
        drawCircle(
            brush = gradient,
            radius = bodyR,
            center = bodyCenter
        )

        // Thumbnail hint
        val nailW = bodyR * 1.05f
        val nailH = bodyR * 0.7f
        val nailTopLeft = Offset(bodyCenter.x - nailW / 2f, bodyCenter.y - bodyR * 1.02f)
        val nailPath = Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    offset = nailTopLeft,
                    size = Size(nailW, nailH)
                )
            )
        }
        drawPath(
            path = nailPath,
            color = lerpColor(handTop, dead, 0.35f)
        )

        // Tick marks around the dial
        val tickCount = 24
        for (i in 0 until tickCount) {
            val angle = (Math.toRadians((i * (360.0 / tickCount) - 90.0)))
            val r1 = outerR + stroke + 8f
            val r2 = r1 + if (i % 6 == 0) 16f else 8f
            val p1 = Offset(
                (center.x + cos(angle) * r1).toFloat(),
                (center.y + sin(angle) * r1).toFloat()
            )
            val p2 = Offset(
                (center.x + cos(angle) * r2).toFloat(),
                (center.y + sin(angle) * r2).toFloat()
            )
            drawLine(
                color = ScrollNumbColors.InkWhisper,
                start = p1,
                end = p2,
                strokeWidth = if (i % 6 == 0) 3f else 1.5f
            )
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val tt = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * tt,
        green = a.green + (b.green - a.green) * tt,
        blue = a.blue + (b.blue - a.blue) * tt,
        alpha = a.alpha + (b.alpha - a.alpha) * tt
    )
}
