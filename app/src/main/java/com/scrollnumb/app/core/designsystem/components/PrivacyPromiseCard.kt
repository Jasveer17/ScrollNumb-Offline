package com.scrollnumb.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrollnumb.app.R
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors

@Composable
fun PrivacyPromiseCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        ScrollNumbColors.Sage.copy(alpha = 0.16f),
                        ScrollNumbColors.InkRaised
                    )
                )
            )
            .border(1.dp, ScrollNumbColors.Sage.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.privacy_title),
                color = ScrollNumbColors.Cream,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.privacy_on_device),
                color = ScrollNumbColors.Sage,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ScrollNumbColors.Sage.copy(alpha = 0.13f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = stringResource(R.string.privacy_local),
            color = ScrollNumbColors.CreamDim,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.privacy_content),
            color = ScrollNumbColors.CreamDim,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}
