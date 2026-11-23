package com.scrollnumb.app.core.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors

@Composable
fun AnimatedCounter(
    targetValue: Int,
    modifier: Modifier = Modifier,
    fontSize: Int = 64
) {
    AnimatedContent(
        targetState = targetValue,
        transitionSpec = {
            if (targetState > initialState) {
                slideInVertically { -it } togetherWith slideOutVertically { it }
            } else {
                slideInVertically { it } togetherWith slideOutVertically { -it }
            }
        },
        modifier = modifier,
        label = "counter"
    ) { count ->
        Text(
            text = "$count",
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            color = ScrollNumbColors.TextPrimary,
            letterSpacing = (-1).sp
        )
    }
}
