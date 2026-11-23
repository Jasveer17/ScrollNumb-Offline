package com.scrollnumb.app.core.designsystem.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors

/**
 * Physical 3D button: a face plate resting on a darker base. Pressing
 * sinks the face down the Y-axis onto the base — firm haptic on press,
 * light tick on release.
 */
@Composable
fun ScrollNumb3DButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    faceColor: Color = ScrollNumbColors.Saffron,
    baseColor: Color = ScrollNumbColors.SaffronDim,
    contentColor: Color = ScrollNumbColors.Ink,
    depth: Int = 6,
    height: Int = 56
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current

    // Firm click on press, light tick on release.
    LaunchedEffect(pressed) {
        if (enabled) {
            if (pressed) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            else haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val travel by animateDpAsState(
        targetValue = if (pressed && enabled) depth.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 1200f),
        label = "btn3dTravel"
    )

    val shape = RoundedCornerShape(16.dp)
    Box(modifier = modifier.padding(bottom = 0.dp)) {
        // Base (the "side" of the button you press into)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((height + depth).dp)
                .clip(shape)
                .background(if (enabled) baseColor else ScrollNumbColors.InkLine)
        )
        // Face plate, floating `depth` above the base, sinks on press
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .offset(y = travel)
                .clip(shape)
                .background(if (enabled) faceColor else ScrollNumbColors.InkElevated)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = if (enabled) contentColor else ScrollNumbColors.CreamMuted,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp
            )
        }
    }
}
