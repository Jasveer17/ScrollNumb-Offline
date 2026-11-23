package com.scrollnumb.app.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scrollnumb.app.R
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbType

@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    accent: Color = ScrollNumbColors.Saffron,
    surface: Color = ScrollNumbColors.InkRaised
) {
    Image(
        painter = painterResource(id = R.drawable.br_ic_brain),
        contentDescription = null,
        colorFilter = ColorFilter.tint(accent),
        modifier = modifier.then(Modifier.size(size))
    )
}

@Composable
fun AppBarBrand(
    modifier: Modifier = Modifier,
    iconSize: Dp = 30.dp,
    color: Color = ScrollNumbColors.Cream,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Actual app logo (not a tinted glyph).
        Image(
            painter = painterResource(id = R.mipmap.scrollnumbdark),
            contentDescription = null,
            modifier = Modifier
                .size(iconSize)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(10.dp))
        // FitWidth: on narrow phones / large system fonts the brand column gets
        // squeezed by the streak-chip cluster — the word used to ellipsize to
        // "ScrollN…" ("half showing"). Now it scales down to fit instead.
        FitWidth {
            Text(
                text = "ScrollNumb",
                style = ScrollNumbType.BrandDisplay,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
