package com.scrollnumb.app.feature.analytics

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.scrollnumb.app.R
import com.scrollnumb.app.core.designsystem.components.WarmGrainBackground
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbCorners
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        rememberTopAppBarState()
    )
    Box(modifier = Modifier.fillMaxSize().background(ScrollNumbColors.BackgroundDark)) {
    WarmGrainBackground(modifier = Modifier.fillMaxSize())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.stats_title),
                        style = ScrollNumbType.BrandDisplay,
                        fontWeight = FontWeight.Bold,
                        color = ScrollNumbColors.Cream
                    )
                },
                expandedHeight = 40.dp,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    // Bottom clearance: draws under the floating nav bar
                    // (see MainActivity).
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 8.dp,
                        bottom = 8.dp + com.scrollnumb.app.core.navigation.bottomNavUnderlayPadding()
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (uiState.isLoading) {
                    // First Room load in flight (<~300ms): render nothing
                    // rather than flashing the "no data" empty state below.
                } else if (uiState.weeklyStats.isEmpty()) {
                    com.scrollnumb.app.core.designsystem.components.EmptyState(
                        emoji = "📊",
                        title = stringResource(R.string.stats_empty_title),
                        message = stringResource(R.string.stats_empty_msg),
                        modifier = Modifier.padding(top = 48.dp)
                    )
                } else {
                val todayStat = uiState.weeklyStats.lastOrNull()
                val totalReels = uiState.weeklyStats.sumOf { it.totalScrolls }
                val dailyAvg = if (uiState.weeklyStats.isNotEmpty()) totalReels / uiState.weeklyStats.size else 0
                val cap = uiState.dailyLimit

                // ── Animated period chart ────────────────────────────
                PeriodTabs(
                    selected = uiState.period,
                    onSelect = viewModel::selectPeriod,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ScrollNumbCorners.lg))
                        .background(ScrollNumbColors.InkRaised)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${uiState.periodTotal}",
                                style = ScrollNumbType.NumeralLarge,
                                color = ScrollNumbColors.Saffron
                            )
                            Text(
                                text = "reels · ~${uiState.periodMinutes} min",
                                style = ScrollNumbType.Microcopy,
                                color = ScrollNumbColors.CreamMuted,
                                modifier = Modifier.offset(y = (-6).dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    AnimatedBarChart(bars = uiState.chartBars)
                }

                if (uiState.appBreakdown.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(ScrollNumbCorners.lg))
                            .background(ScrollNumbColors.InkRaised)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "WHERE IT WENT",
                            style = ScrollNumbType.Microcopy,
                            fontWeight = FontWeight.Bold,
                            color = ScrollNumbColors.CreamMuted
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        AppBreakdownBars(apps = uiState.appBreakdown)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                BigStatCard(
                    title = stringResource(R.string.stats_reels_today).uppercase(),
                    value = (todayStat?.totalScrolls ?: 0).toString(),
                    caption = stringResource(R.string.stats_of_cap, cap),
                    highlighted = true
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BigStatCard(
                        title = stringResource(R.string.stats_daily_avg).uppercase(),
                        value = dailyAvg.toString(),
                        caption = stringResource(R.string.stats_reels_scrolled),
                        modifier = Modifier.weight(1f),
                        compact = true
                    )
                    BigStatCard(
                        title = stringResource(R.string.stats_total_reels).uppercase(),
                        value = totalReels.toString(),
                        caption = stringResource(R.string.stats_total_reels_week),
                        modifier = Modifier.weight(1f),
                        compact = true
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.stats_top_apps).uppercase(),
                    style = ScrollNumbType.Microcopy,
                    fontWeight = FontWeight.Bold,
                    color = ScrollNumbColors.CreamMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, bottom = 10.dp)
                )

                TopAppsCard(
                    ig = uiState.weeklyStats.sumOf { it.instagramScrolls },
                    yt = uiState.weeklyStats.sumOf { it.youtubeScrolls },
                    tt = uiState.weeklyStats.sumOf { it.tiktokScrolls },
                    fb = uiState.weeklyStats.sumOf { it.facebookScrolls },
                    snap = uiState.weeklyStats.sumOf { it.snapchatScrolls }
                )

                Spacer(modifier = Modifier.height(40.dp))
                } // end else — has data
            }
        }
        }
    }
    }
}

@Composable
private fun BigStatCard(
    title: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    highlighted: Boolean = false
) {
    Column(
        modifier = modifier
            .then(if (compact) Modifier else Modifier.fillMaxWidth())
            .heightIn(min = if (compact) 132.dp else 0.dp)
            .clip(RoundedCornerShape(ScrollNumbCorners.lg))
            .background(
                if (highlighted) {
                    Brush.horizontalGradient(
                        listOf(
                            ScrollNumbColors.Saffron.copy(alpha = 0.22f),
                            ScrollNumbColors.InkRaised
                        )
                    )
                } else {
                    Brush.linearGradient(listOf(ScrollNumbColors.InkRaised, ScrollNumbColors.InkRaised))
                }
            )
            .padding(horizontal = if (compact) 16.dp else 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            style = ScrollNumbType.Microcopy,
            fontWeight = FontWeight.Bold,
            color = ScrollNumbColors.CreamMuted,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(8.dp))
        com.scrollnumb.app.core.designsystem.components.FitWidth(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = value,
                style = if (compact) ScrollNumbType.NumeralMedium else ScrollNumbType.NumeralLarge,
                color = if (highlighted) ScrollNumbColors.Saffron else ScrollNumbColors.Cream,
                maxLines = 1,
                softWrap = false
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = caption,
            style = ScrollNumbType.Microcopy,
            color = ScrollNumbColors.CreamMuted,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TopAppsCard(
    ig: Int,
    yt: Int,
    tt: Int,
    fb: Int,
    snap: Int
) {
    val max = maxOf(ig, yt, tt, fb, snap).coerceAtLeast(1)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ScrollNumbCorners.lg))
            .background(ScrollNumbColors.InkRaised)
            .padding(vertical = 6.dp)
    ) {
        AppBar(label = "Instagram", count = ig, max = max, iconRes = R.drawable.app_icon_instagram)
        AppDivider()
        AppBar(label = "TikTok", count = tt, max = max, iconRes = R.drawable.app_icon_tiktok)
        AppDivider()
        AppBar(label = "YouTube Shorts", count = yt, max = max, iconRes = R.drawable.app_icon_youtube)
        AppDivider()
        AppBar(label = "Facebook", count = fb, max = max, iconRes = R.drawable.app_icon_facebook)
        AppDivider()
        AppBar(label = "Snapchat", count = snap, max = max, iconRes = R.drawable.app_icon_snapchat)
    }
}

@Composable
private fun AppBar(
    label: String,
    count: Int,
    max: Int,
    iconRes: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 15.spx(),
                fontWeight = FontWeight.SemiBold,
                color = ScrollNumbColors.Cream
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(ScrollNumbColors.InkWhisper)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = (count.toFloat() / max).coerceIn(0f, 1f))
                        .background(ScrollNumbColors.Saffron)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = count.toString(),
            style = ScrollNumbType.NumeralMedium,
            color = ScrollNumbColors.Cream
        )
    }
}

@Composable
private fun AppDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(ScrollNumbColors.InkWhisper)
    )
}

private fun Int.spx() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
