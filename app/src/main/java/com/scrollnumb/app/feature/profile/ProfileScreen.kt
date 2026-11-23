package com.scrollnumb.app.feature.profile

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.scrollnumb.app.core.designsystem.components.avatarPainter as painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.scrollnumb.app.R
import com.scrollnumb.app.core.designsystem.components.NumbnessPill
import com.scrollnumb.app.core.designsystem.components.WarmGrainBackground
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbCorners
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbType
import com.scrollnumb.app.core.util.AvatarHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToXp: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScrollNumbColors.Ink)
    ) {
        WarmGrainBackground(modifier = Modifier.fillMaxSize())

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.profile_title),
                            style = ScrollNumbType.BrandDisplay,
                            fontWeight = FontWeight.Bold,
                            color = ScrollNumbColors.Cream
                        )
                    },
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.settings_title),
                                tint = ScrollNumbColors.Cream
                            )
                        }
                    },
                    expandedHeight = 40.dp,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                    scrollBehavior = scrollBehavior
                )
            }
        ) { innerPadding ->
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
                        bottom = com.scrollnumb.app.core.navigation.bottomNavUnderlayPadding()
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                    val context = androidx.compose.ui.platform.LocalContext.current
                    val ratio = if (uiState.dailyLimit > 0) {
                        (uiState.reelsToday.toFloat() / uiState.dailyLimit).coerceIn(0f, 1.6f)
                    } else 0f
                    val avatarResId = com.scrollnumb.app.core.util.AvatarHelper.getScrollStatusAvatar(context, ratio)
                    // A chosen photo/emoji becomes the profile picture; otherwise
                    // the scroll brain-state illustration is shown.
                    val chosen = uiState.avatar
                    val hasCustom = chosen.startsWith("content://") || chosen.startsWith("file://") ||
                        chosen.startsWith("http://") || chosen.startsWith("https://") ||
                        (chosen.isNotBlank() && chosen != "🧠" && chosen != "😎")

                    // Status ring: golden while under the daily limit (shade deepens
                    // as you near it), warming toward red the further over you go.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth().height(168.dp)
                            .clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(scrollRingColor(ratio).copy(alpha = .45f), ScrollNumbColors.InkRaised)))
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!hasCustom) com.scrollnumb.app.core.designsystem.components.AnimatedAvatar(avatarResId, Modifier.fillMaxHeight()) else Avatar(
                            avatar = chosen,
                            modifier = Modifier.size(96.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = uiState.displayName.ifEmpty { "Player 1" },
                        style = ScrollNumbType.BrandDisplay,
                        fontWeight = FontWeight.Bold,
                        color = ScrollNumbColors.Cream
                    )

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = uiState.username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ScrollNumbColors.Saffron
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    BioCard(bio = uiState.bio.ifEmpty { "Ready to conquer my digital habits." })

                    if (uiState.email.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        InfoRow(label = stringResource(R.string.profile_email_label), value = uiState.email)
                    }
                Spacer(modifier = Modifier.height(20.dp))

                    LoggedInActions(
                        joinedOn = uiState.joinedOn,
                        onEditProfile = onNavigateToEditProfile
                    )


                Spacer(modifier = Modifier.height(20.dp))

                XpCard(
                    totalXp = uiState.totalXp,
                    weeklyXp = uiState.weeklyXp,
                    level = uiState.level,
                    levelProgress = uiState.levelProgress,
                    leagueTier = uiState.leagueTier,
                    breakdown = uiState.xpBreakdown,
                    onClick = onNavigateToXp
                )

                Spacer(modifier = Modifier.height(20.dp))

                StatsGrid(
                    reelsToday = uiState.reelsToday,
                    dailyAvg = uiState.dailyAvg,
                    totalReels = uiState.totalReels,
                    streakDays = uiState.streakDays
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = stringResource(
                        R.string.settings_version,
                        com.scrollnumb.app.core.util.appVersionName(LocalContext.current)
                    ),
                    style = ScrollNumbType.Microcopy,
                    color = ScrollNumbColors.CreamMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

/**
 * Ring color keyed to today's scroll ratio (reels / dailyLimit). Golden while
 * under the limit — pale when far below, rich gold as you approach it — then
 * warms from orange to deep red the further past the limit you scroll.
 */
private fun scrollRingColor(ratio: Float): Color = when {
    ratio <= 1f -> androidx.compose.ui.graphics.lerp(
        Color(0xFFFFE9A3), Color(0xFFFFC53D), ratio.coerceIn(0f, 1f)
    )
    else -> androidx.compose.ui.graphics.lerp(
        Color(0xFFFF8A65), Color(0xFFE5372B), ((ratio - 1f) / 0.6f).coerceIn(0f, 1f)
    )
}

@Composable
private fun Avatar(
    avatar: Any,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val gender = context.getSharedPreferences("scroll_numb_prefs", android.content.Context.MODE_PRIVATE)
        .getString("USER_GENDER", "Male") ?: "Male"
    val defaultAvatar = painterResource(AvatarHelper.getDefaultAvatar(gender))
    val desc = stringResource(R.string.profile_title)
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(ScrollNumbColors.InkRaised),
        contentAlignment = Alignment.Center
    ) {
        when (avatar) {
            is Int -> {
                Image(
                    painter = painterResource(id = avatar),
                    contentDescription = desc,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is String -> {
                if (avatar.startsWith("content://") || avatar.startsWith("file://") || avatar.startsWith("http://") || avatar.startsWith("https://")) {
                    // A broken/unreadable photo URI falls back to the gender
                    // illustration instead of an empty circle.
                    coil.compose.AsyncImage(
                        model = Uri.parse(avatar),
                        contentDescription = desc,
                        contentScale = ContentScale.Crop,
                        error = defaultAvatar,
                        fallback = defaultAvatar,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (avatar == "🧠" || avatar == "😎" || avatar.isBlank()) {
                    Image(
                        painter = defaultAvatar,
                        contentDescription = desc,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (avatar.isNotEmpty()) {
                    Text(text = avatar, fontSize = 48.sp)
                }
            }
        }
    }
}

@Composable
private fun LoggedInActions(
    joinedOn: String,
    onEditProfile: () -> Unit
) {
    if (joinedOn.isNotEmpty()) {
        Text(
            text = stringResource(R.string.profile_joined_on, joinedOn),
            style = ScrollNumbType.Microcopy,
            color = ScrollNumbColors.CreamMuted
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    NumbnessPill(
        label = stringResource(R.string.profile_edit),
        onClick = onEditProfile,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun LoggedOutActions(onLogin: () -> Unit) {
    NumbnessPill(
        label = stringResource(R.string.profile_login),
        onClick = onLogin,
        inverse = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun XpCard(
    totalXp: Int,
    weeklyXp: Int,
    level: Int,
    levelProgress: Float,
    leagueTier: String,
    breakdown: com.scrollnumb.app.core.util.XpFormula.Breakdown,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ScrollNumbCorners.lg))
            .clickable { onClick() }
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = listOf(
                        ScrollNumbColors.Saffron.copy(alpha = 0.16f),
                        ScrollNumbColors.Saffron.copy(alpha = 0.04f)
                    )
                )
            )
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LEVEL $level",
                    style = ScrollNumbType.Microcopy,
                    fontWeight = FontWeight.Bold,
                    color = ScrollNumbColors.Saffron
                )
                Text(
                    text = "${com.scrollnumb.app.core.util.LeagueSystem.tierEmoji(leagueTier)} $leagueTier · ${weeklyXp} XP this week",
                    style = ScrollNumbType.Microcopy,
                    color = ScrollNumbColors.CreamMuted,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            com.scrollnumb.app.core.designsystem.components.FitWidth(
                modifier = Modifier.widthIn(max = 104.dp)
            ) {
                Text(
                    text = totalXp.toString(),
                    style = ScrollNumbType.NumeralMedium.copy(fontSize = 28.sp, lineHeight = 32.sp),
                    color = ScrollNumbColors.Saffron,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "XP",
                style = ScrollNumbType.Microcopy,
                fontWeight = FontWeight.Bold,
                color = ScrollNumbColors.Saffron
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { levelProgress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = ScrollNumbColors.Saffron,
            trackColor = ScrollNumbColors.Saffron.copy(alpha = 0.15f)
        )
        if (breakdown.total > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            XpBreakdownRow(
                label = "Streak",
                value = breakdown.streakXp
            )
            XpBreakdownRow(
                label = "Math focus",
                value = breakdown.mathXp
            )
            XpBreakdownRow(
                label = "Breathe",
                value = breakdown.breatheXp
            )
            XpBreakdownRow(
                label = "Scroll-less",
                value = breakdown.scrollLessXp
            )
        }
    }
}

@Composable
private fun XpBreakdownRow(label: String, value: Int) {
    if (value <= 0) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = ScrollNumbType.Microcopy,
            color = ScrollNumbColors.CreamMuted,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "+$value",
            style = ScrollNumbType.Microcopy,
            color = ScrollNumbColors.Saffron,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun BioCard(bio: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ScrollNumbCorners.lg))
            .background(ScrollNumbColors.InkRaised)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.profile_bio_label).uppercase(),
            style = ScrollNumbType.Microcopy,
            fontWeight = FontWeight.Bold,
            color = ScrollNumbColors.Saffron
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = bio,
            style = MaterialTheme.typography.bodyMedium,
            color = ScrollNumbColors.Cream
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ScrollNumbCorners.md))
            .background(ScrollNumbColors.InkRaised)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = ScrollNumbType.Microcopy,
            color = ScrollNumbColors.CreamMuted,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = ScrollNumbColors.Cream,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.4f)
        )
    }
}

@Composable
private fun StatsGrid(
    reelsToday: Int,
    dailyAvg: Int,
    totalReels: Int,
    streakDays: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatTile(stringResource(R.string.stats_reels_today), reelsToday.toString(), Modifier.weight(1f))
            StatTile(stringResource(R.string.stats_daily_avg), dailyAvg.toString(), Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatTile(stringResource(R.string.stats_total_reels), totalReels.toString(), Modifier.weight(1f))
            StatTile(
                stringResource(R.string.dash_streak),
                "${streakDays}d",
                Modifier.weight(1f),
                ScrollNumbColors.Sage
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = ScrollNumbColors.Saffron
) {
    Column(
        modifier = modifier
            .heightIn(min = 92.dp)
            .clip(RoundedCornerShape(ScrollNumbCorners.lg))
            .background(ScrollNumbColors.InkRaised)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = ScrollNumbType.Microcopy,
            color = ScrollNumbColors.CreamMuted,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = ScrollNumbType.NumeralMedium,
            color = accent,
            maxLines = 1
        )
    }
}
