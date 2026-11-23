package com.scrollnumb.app.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch

/**
 * A highly polished, physics-based bounce click modifier.
 * Used for providing a premium, tactile feel to buttons across the app.
 * 
 * @param scaleDown The target scale to shrink to when pressed.
 * @param onClick The callback to trigger on release.
 */
fun Modifier.bounceClick(
    scaleDown: Float = 0.92f,
    onClick: (() -> Unit)? = null
) = composed {
    val coroutineScope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val haptic = LocalHapticFeedback.current

    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(Unit) {
            while (true) {
                awaitPointerEventScope {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Trigger haptic and scale down with spring physics
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    coroutineScope.launch {
                        scale.animateTo(
                            targetValue = scaleDown,
                            animationSpec = spring(
                                dampingRatio = 0.7f,
                                stiffness = 600f
                            )
                        )
                    }

                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        // Click was successful
                        onClick?.invoke()
                    }
                    
                    // Scale back up to 1.0 with a nice bounce
                    coroutineScope.launch {
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = 0.5f,
                                stiffness = 400f
                            )
                        )
                    }
                }
            }
        }
}
