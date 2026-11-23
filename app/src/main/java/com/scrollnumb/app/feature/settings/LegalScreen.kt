package com.scrollnumb.app.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.scrollnumb.app.R
import com.scrollnumb.app.core.designsystem.components.WarmGrainBackground
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbCorners
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbType

@Composable
fun LegalScreen(
    onBack: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToTerms: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScrollNumbColors.Ink)
    ) {
        WarmGrainBackground(modifier = Modifier.fillMaxSize())

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.login_close),
                        tint = ScrollNumbColors.Cream
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.settings_legal),
                    style = ScrollNumbType.BrandDisplay,
                    fontWeight = FontWeight.Bold,
                    color = ScrollNumbColors.Cream
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_legal_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ScrollNumbColors.CreamDim
                )
                Spacer(modifier = Modifier.height(20.dp))

                LegalHubRow(
                    iconRes = R.drawable.br_ic_lock_gold,
                    title = stringResource(R.string.settings_privacy_policy),
                    onClick = onNavigateToPrivacy
                )
                Spacer(modifier = Modifier.height(10.dp))
                LegalHubRow(
                    iconRes = R.drawable.br_ic_copy,
                    title = stringResource(R.string.settings_terms),
                    onClick = onNavigateToTerms
                )
                Spacer(modifier = Modifier.height(20.dp))
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun LegalHubRow(
    iconRes: Int,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(ScrollNumbCorners.lg))
            .background(ScrollNumbColors.InkRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(ScrollNumbCorners.sm))
                .background(ScrollNumbColors.Saffron.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ScrollNumbColors.Saffron),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = ScrollNumbColors.Cream,
            modifier = Modifier.weight(1f)
        )
        Image(
            painter = painterResource(id = R.drawable.br_ic_caret_right),
            contentDescription = null,
            colorFilter = ColorFilter.tint(ScrollNumbColors.CreamMuted),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun LegalToggleRow(
    iconRes: Int,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ScrollNumbCorners.lg))
            .background(ScrollNumbColors.InkRaised)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(ScrollNumbCorners.sm))
                .background(ScrollNumbColors.Saffron.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ScrollNumbColors.Saffron),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = ScrollNumbColors.Cream
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = ScrollNumbType.Microcopy,
                    color = ScrollNumbColors.CreamMuted
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ScrollNumbColors.Ink,
                checkedTrackColor = ScrollNumbColors.Saffron,
                uncheckedThumbColor = ScrollNumbColors.CreamMuted,
                uncheckedTrackColor = ScrollNumbColors.InkWhisper
            )
        )
    }
}
