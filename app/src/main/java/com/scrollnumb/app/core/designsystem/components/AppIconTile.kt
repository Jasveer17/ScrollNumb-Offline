package com.scrollnumb.app.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbCorners
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbType

@Composable
fun AppIconTile(
    name: String,
    count: Int,
    iconRes: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(104.dp)
            .clip(RoundedCornerShape(ScrollNumbCorners.lg))
            .background(ScrollNumbColors.InkRaised)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(ScrollNumbCorners.sm)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = count.toString(),
            style = ScrollNumbType.NumeralMedium,
            color = ScrollNumbColors.Cream
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = name.uppercase(),
            style = ScrollNumbType.Microcopy,
            color = ScrollNumbColors.CreamMuted
        )
    }
}
