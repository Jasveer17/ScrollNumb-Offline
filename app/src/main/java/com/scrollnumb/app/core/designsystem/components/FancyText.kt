package com.scrollnumb.app.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors

/**
 * 3D embossed text: a dark extrusion layer offset behind the face layer.
 * Gives headlines a chunky, tactile, claymation feel.
 */
@Composable
fun EmbossedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    faceColor: Color = ScrollNumbColors.Cream,
    depthColor: Color = Color(0x66000000),
    depth: Int = 3
) {
    Box(modifier = modifier) {
        Text(
            text = text,
            style = style,
            color = depthColor,
            modifier = Modifier.offset(x = depth.dp, y = depth.dp)
        )
        Text(
            text = text,
            style = style,
            color = faceColor
        )
    }
}

/**
 * Neon text: saturated core with a soft colored glow. Donghua-poster energy.
 */
@Composable
fun NeonText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = ScrollNumbColors.Saffron
) {
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(
            color = color,
            shadow = Shadow(
                color = color.copy(alpha = 0.55f),
                offset = Offset(0f, 0f),
                blurRadius = 24f
            )
        )
    )
}
