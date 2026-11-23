package com.scrollnumb.app.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.TextStyle
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors

/**
 * Text with a slow iridescent gradient sweep — cream → saffron → coral → sage.
 * Use sparingly: hero headlines and key numbers only.
 *
 * The animated shift is read inside [ShaderBrush.createShader] (draw phase),
 * NOT in composition — the old composition-time brush recomposed the Text on
 * every frame, forever, which was a measurable jank source on the dashboard.
 */
@Composable
fun HolographicText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current
) {
    val transition = rememberInfiniteTransition(label = "holo")
    val shift = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "holoShift"
    )

    val cream = ScrollNumbColors.Cream
    val saffron = ScrollNumbColors.Saffron
    val coral = ScrollNumbColors.Coral
    val sage = ScrollNumbColors.Sage
    val brush = remember(cream, saffron, coral, sage) {
        object : ShaderBrush() {
            override fun createShader(size: Size): Shader {
                val s = shift.value
                return LinearGradientShader(
                    from = Offset(s, 0f),
                    to = Offset(s + 600f, 80f),
                    colors = listOf(cream, saffron, coral, sage, cream),
                    tileMode = TileMode.Mirror
                )
            }
        }
    }

    Text(
        text = text,
        modifier = modifier,
        style = style.copy(brush = brush)
    )
}
