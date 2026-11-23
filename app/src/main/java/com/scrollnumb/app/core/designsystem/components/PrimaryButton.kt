package com.scrollnumb.app.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbCorners

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "primaryButtonScale"
    )
    val content by animateColorAsState(
        targetValue = if (enabled) ScrollNumbColors.Ink else ScrollNumbColors.CreamMuted,
        label = "primaryButtonContent"
    )

    // Slow gradient sweep across the enabled button — saffron body with a
    // warm highlight band drifting through. Subtle, not a loading shimmer.
    val transition = rememberInfiniteTransition(label = "primaryButtonSweep")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "primaryButtonSweepShift"
    )
    // Colors resolved in composition (theme reads); the SWEEP value is read
    // only inside drawBehind, so the per-frame animation invalidates the draw
    // pass alone — previously the brush was built in composition and every
    // visible PrimaryButton RECOMPOSED on every frame, forever, which is a
    // real jank source on screens with several buttons.
    val saffron = ScrollNumbColors.Saffron
    val saffronDim = ScrollNumbColors.SaffronDim
    val inkElevated = ScrollNumbColors.InkElevated

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(ScrollNumbCorners.pill))
            .drawBehind {
                val brush = if (enabled) {
                    Brush.linearGradient(
                        colors = listOf(saffron, saffronDim, Color(0xFFFFE382), saffron),
                        start = Offset(sweep, 0f),
                        end = Offset(sweep + 700f, 56f),
                        tileMode = androidx.compose.ui.graphics.TileMode.Mirror
                    )
                } else {
                    Brush.linearGradient(listOf(inkElevated, inkElevated))
                }
                drawRect(brush = brush)
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = content,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            letterSpacing = 0.2.sp
        )
    }
}
