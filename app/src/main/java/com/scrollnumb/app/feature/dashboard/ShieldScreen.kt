package com.scrollnumb.app.feature.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.scrollnumb.app.R
import com.scrollnumb.app.core.designsystem.components.NumbnessPill
import com.scrollnumb.app.core.designsystem.components.NumberStepper
import com.scrollnumb.app.core.designsystem.components.WarmGrainBackground
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbCorners
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldScreen(
    onBack: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val ratio = if (uiState.dailyLimit > 0) {
        (uiState.globalScrollCount.toFloat() / uiState.dailyLimit).coerceIn(0f, 1.6f)
    } else 0f

    val isLocking = uiState.globalScrollCount >= uiState.dailyLimit
    val statusText = when {
        !uiState.shieldEnabled -> stringResource(R.string.block_status_paused)
        isLocking -> stringResource(R.string.block_status_locking)
        else -> stringResource(R.string.block_status_blocking)
    }
    val statusAccent = when {
        !uiState.shieldEnabled -> ScrollNumbColors.CreamMuted
        isLocking -> ScrollNumbColors.Coral
        else -> ScrollNumbColors.Saffron
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 0.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = stringResource(R.string.block_title),
                        style = ScrollNumbType.BrandDisplay,
                        fontWeight = FontWeight.Bold,
                        color = ScrollNumbColors.Cream
                    )
                }
            }
        },
        containerColor = ScrollNumbColors.BackgroundDark
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            WarmGrainBackground(modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StatusCard(
                    statusText = statusText,
                    statusAccent = statusAccent,
                    ratio = ratio,
                    scrolled = uiState.globalScrollCount,
                    cap = uiState.dailyLimit
                )

                Spacer(modifier = Modifier.height(20.dp))

                SectionHeader(
                    title = stringResource(R.string.block_allow_up_to).uppercase()
                )
                Spacer(modifier = Modifier.height(10.dp))
                NumberStepper(
                    label = stringResource(R.string.block_allow_up_to_sub),
                    value = uiState.dailyLimit,
                    onValueChange = viewModel::setDailyLimit,
                    step = 5,
                    range = 5..200
                )

                Spacer(modifier = Modifier.height(20.dp))

                SectionHeader(
                    title = stringResource(R.string.block_cooldown).uppercase()
                )
                Spacer(modifier = Modifier.height(10.dp))
                NumberStepper(
                    label = stringResource(R.string.block_cooldown_sub),
                    value = uiState.blockDurationMins,
                    onValueChange = viewModel::setBlockDurationMins,
                    step = 1,
                    range = 2..120,
                    suffix = " mins"
                )

                Spacer(modifier = Modifier.height(20.dp))

                MasterToggleRow(
                    enabled = uiState.shieldEnabled,
                    onToggle = viewModel::toggleShield
                )

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun StatusCard(
    statusText: String,
    statusAccent: Color,
    ratio: Float,
    scrolled: Int,
    cap: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ScrollNumbCorners.lg))
            .background(ScrollNumbColors.InkRaised)
            .padding(20.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusAccent)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusText.uppercase(),
                style = ScrollNumbType.Microcopy,
                fontWeight = FontWeight.Bold,
                color = statusAccent
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.block_status_sub, scrolled, cap),
            style = MaterialTheme.typography.bodyLarge,
            color = ScrollNumbColors.Cream
        )
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ScrollNumbColors.InkWhisper)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = ratio.coerceAtMost(1f))
                    .background(statusAccent)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = ScrollNumbType.Microcopy,
        fontWeight = FontWeight.Bold,
        color = ScrollNumbColors.CreamMuted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp)
    )
}

@Composable
private fun MasterToggleRow(enabled: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ScrollNumbCorners.lg))
            .background(ScrollNumbColors.InkRaised)
            .clickable(onClick = onToggle)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.block_master),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = ScrollNumbColors.Cream
            )
            Text(
                text = stringResource(R.string.block_master_sub),
                style = ScrollNumbType.Microcopy,
                color = ScrollNumbColors.CreamMuted
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = ScrollNumbColors.Ink,
                checkedTrackColor = ScrollNumbColors.Saffron,
                uncheckedThumbColor = ScrollNumbColors.CreamMuted,
                uncheckedTrackColor = ScrollNumbColors.InkWhisper,
                uncheckedBorderColor = ScrollNumbColors.InkLine
            )
        )
    }
}

