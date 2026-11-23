package com.scrollnumb.app.core.designsystem.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors

@Composable
fun StrikeIndicator(
    strikesCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(3) { index ->
            val isActive = index < strikesCount
            StrikeDot(isActive = isActive, index = index, strikesCount = strikesCount)
        }
    }
}

@Composable
private fun StrikeDot(isActive: Boolean, index: Int, strikesCount: Int) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    val activeColor = ScrollNumbColors.StrikeRed
    val inactiveColor = ScrollNumbColors.TextSecondary.copy(alpha = 0.3f)

    Canvas(modifier = Modifier.size((14 * scale).dp)) {
        val radius = size.minDimension / 2f
        drawCircle(
            color = if (isActive) activeColor else inactiveColor,
            radius = radius,
            center = Offset(size.width / 2, size.height / 2)
        )
        if (isActive) {
            drawCircle(
                color = activeColor.copy(alpha = 0.3f),
                radius = radius * 1.4f,
                center = Offset(size.width / 2, size.height / 2)
            )
        }
    }
}
