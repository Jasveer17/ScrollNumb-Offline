package com.scrollnumb.app.feature.dashboard.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.domain.model.BrainState
import kotlin.math.*
import kotlin.random.Random

@Composable
fun NumbMeter(
    brainState: BrainState,
    scrollCount: Int,
    modifier: Modifier = Modifier
) {
    val stateColor: Color by animateColorAsState(
        targetValue = when (brainState) {
            BrainState.FRESH -> ScrollNumbColors.StateFresh
            BrainState.HEALTHY -> ScrollNumbColors.StateHealthy
            BrainState.MILD -> ScrollNumbColors.StateMild
            BrainState.WARNING -> ScrollNumbColors.StateWarning
            BrainState.CRITICAL -> ScrollNumbColors.StateCritical
            BrainState.NUMB -> ScrollNumbColors.StateNumb
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "stateColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "tree")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (brainState) {
                    BrainState.FRESH -> 3000
                    BrainState.HEALTHY -> 2500
                    BrainState.MILD -> 2000
                    BrainState.WARNING -> 1500
                    BrainState.CRITICAL -> 900
                    BrainState.NUMB -> 500
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val shake by infiniteTransition.animateFloat(
        initialValue = -1.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (brainState == BrainState.CRITICAL) 80 else 4000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shake"
    )

    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing)
        ),
        label = "time"
    )

    // Pre-calculated leaf offsets for consistency
    val leafOffsets = remember {
        List(25) { index ->
            val angle = (index * 15f + 45f) * (PI / 180f)
            val distance = Random.nextFloat() * 45f + 25f
            val leafSize = Random.nextFloat() * 6f + 4f
            val speed = Random.nextFloat() * 0.4f + 0.6f
            val phase = Random.nextFloat() * 6.28f
            Triple(angle, distance, Triple(leafSize, speed, phase))
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(280.dp)
    ) {
        Canvas(modifier = Modifier.size(280.dp)) {
            val cx = size.width / 2
            val cy = size.height * 0.55f
            val radius = size.minDimension * 0.35f * pulseScale
            val shakeOffset = if (brainState.ordinal >= BrainState.CRITICAL.ordinal) shake * 3.5f else 0f

            // 1. Ambient Background Glow
            val glowColor1 = Color(stateColor.red, stateColor.green, stateColor.blue, 0.12f)
            val glowColor2 = Color(stateColor.red, stateColor.green, stateColor.blue, 0.02f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor1, glowColor2, Color.Transparent),
                    center = Offset(cx + shakeOffset, cy - 20.dp.toPx()),
                    radius = radius * 2.2f
                )
            )

            // 2. Draw Tree Trunk and Main Branches
            val trunkPath = Path().apply {
                // Base of trunk
                moveTo(cx - 14.dp.toPx() + shakeOffset, cy + 70.dp.toPx())
                lineTo(cx + 14.dp.toPx() + shakeOffset, cy + 70.dp.toPx())
                // Taper up to main split
                quadraticTo(
                    cx + 8.dp.toPx() + shakeOffset, cy + 20.dp.toPx(),
                    cx + 6.dp.toPx() + shakeOffset, cy - 10.dp.toPx()
                )
                // Left main branch branch
                cubicTo(
                    cx + 6.dp.toPx() + shakeOffset, cy - 25.dp.toPx(),
                    cx - 30.dp.toPx() + shakeOffset, cy - 40.dp.toPx(),
                    cx - 50.dp.toPx() + shakeOffset, cy - 65.dp.toPx()
                )
                // Curve back down branch
                quadraticTo(
                    cx - 25.dp.toPx() + shakeOffset, cy - 30.dp.toPx(),
                    cx - 4.dp.toPx() + shakeOffset, cy - 15.dp.toPx()
                )
                // Taper down to base
                quadraticTo(
                    cx - 8.dp.toPx() + shakeOffset, cy + 20.dp.toPx(),
                    cx - 14.dp.toPx() + shakeOffset, cy + 70.dp.toPx()
                )
                close()
            }

            val trunkColor = if (brainState == BrainState.NUMB) ScrollNumbColors.StateNumb else Color(0xFF3F2B1B)
            drawPath(
                path = trunkPath,
                color = trunkColor.copy(alpha = if (brainState == BrainState.NUMB) 0.5f else 0.9f)
            )

            // Secondary branches
            val branchPath = Path().apply {
                // Right branch
                moveTo(cx + 4.dp.toPx() + shakeOffset, cy - 15.dp.toPx())
                cubicTo(
                    cx + 12.dp.toPx() + shakeOffset, cy - 35.dp.toPx(),
                    cx + 35.dp.toPx() + shakeOffset, cy - 45.dp.toPx(),
                    cx + 55.dp.toPx() + shakeOffset, cy - 60.dp.toPx()
                )
                quadraticTo(
                    cx + 25.dp.toPx() + shakeOffset, cy - 32.dp.toPx(),
                    cx + 6.dp.toPx() + shakeOffset, cy - 10.dp.toPx()
                )

                // Top middle branch
                moveTo(cx - 3.dp.toPx() + shakeOffset, cy - 15.dp.toPx())
                cubicTo(
                    cx - 5.dp.toPx() + shakeOffset, cy - 40.dp.toPx(),
                    cx + 10.dp.toPx() + shakeOffset, cy - 60.dp.toPx(),
                    cx + 5.dp.toPx() + shakeOffset, cy - 80.dp.toPx()
                )
                quadraticTo(
                    cx + 2.dp.toPx() + shakeOffset, cy - 50.dp.toPx(),
                    cx + 3.dp.toPx() + shakeOffset, cy - 12.dp.toPx()
                )
            }
            drawPath(
                path = branchPath,
                color = trunkColor.copy(alpha = if (brainState == BrainState.NUMB) 0.4f else 0.8f)
            )

            // 3. Draw Leaves (Reactive to BrainState)
            val maxLeaves = when (brainState) {
                BrainState.FRESH -> 25
                BrainState.HEALTHY -> 20
                BrainState.MILD -> 12
                BrainState.WARNING -> 6
                BrainState.CRITICAL -> 2
                BrainState.NUMB -> 0
            }

            leafOffsets.take(maxLeaves).forEach { (angle, dist, spec) ->
                val sizeVal = spec.first
                val speed = spec.second
                val phase = spec.third

                // Calculate leaf floating/pulsing positions
                val factor = 1f + sin(time * speed + phase) * 0.08f
                val radDist = dist.dp.toPx() * factor
                val lx = cx + cos(angle).toFloat() * radDist * 1.3f + shakeOffset
                // Center the leaves above the split
                val ly = cy - 25.dp.toPx() - sin(angle).toFloat() * radDist * 0.9f

                // Draw glowing leaves
                drawCircle(
                    color = stateColor.copy(alpha = 0.8f),
                    radius = sizeVal.dp.toPx(),
                    center = Offset(lx, ly)
                )
                drawCircle(
                    color = stateColor.copy(alpha = 0.25f),
                    radius = sizeVal.dp.toPx() * 1.8f,
                    center = Offset(lx, ly)
                )
            }

            // 4. Falling leaves for MILD & WARNING (Autumn decay visual)
            if (brainState == BrainState.MILD || brainState == BrainState.WARNING) {
                val fallCount = if (brainState == BrainState.MILD) 4 else 8
                repeat(fallCount) { idx ->
                    val progress = ((time + idx * (6.28f / fallCount)) % 6.28f) / 6.28f
                    val fx = cx + sin(progress * 10f + idx) * 60.dp.toPx() + shakeOffset
                    val fy = cy - 40.dp.toPx() + progress * 100.dp.toPx()

                    drawCircle(
                        color = stateColor.copy(alpha = (1f - progress) * 0.7f),
                        radius = 4.dp.toPx(),
                        center = Offset(fx, fy)
                    )
                }
            }

            // 5. Crack Lines on Trunk (MILD+)
            if (brainState.ordinal >= BrainState.MILD.ordinal) {
                val crackIntensity = (brainState.ordinal - 2).coerceIn(1, 4)
                val crackColor = Color(stateColor.red, stateColor.green, stateColor.blue, 0.7f)
                val strokeWidth = (1.2f + crackIntensity * 0.4f).dp.toPx()

                // Draw cracks crawling up trunk
                val crackPath = Path().apply {
                    moveTo(cx + shakeOffset, cy + 50.dp.toPx())
                    lineTo(cx - 3.dp.toPx() + shakeOffset, cy + 30.dp.toPx())
                    lineTo(cx + 4.dp.toPx() + shakeOffset, cy + 10.dp.toPx())
                    if (crackIntensity >= 2) {
                        moveTo(cx - 4.dp.toPx() + shakeOffset, cy + 25.dp.toPx())
                        lineTo(cx - 12.dp.toPx() + shakeOffset, cy + 5.dp.toPx())
                    }
                }
                drawPath(
                    path = crackPath,
                    color = crackColor,
                    style = Stroke(width = strokeWidth)
                )
            }

            // 6. NUMB Withered static particles
            if (brainState == BrainState.NUMB) {
                repeat(60) {
                    val x = cx + (Random.nextFloat() - 0.5f) * 160.dp.toPx()
                    val y = cy - 20.dp.toPx() + (Random.nextFloat() - 0.5f) * 160.dp.toPx()
                    drawCircle(
                        color = Color.White.copy(alpha = Random.nextFloat() * 0.22f),
                        radius = Random.nextFloat() * 1.5f + 0.5f,
                        center = Offset(x, y)
                    )
                }
            }
        }

        // State label overlay at the center-bottom of the widget
        if (brainState == BrainState.NUMB) {
            Text(
                text = "WITHERED",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = ScrollNumbColors.StateNumb.copy(alpha = 0.8f),
                letterSpacing = 6.sp
            )
        }
    }
}

