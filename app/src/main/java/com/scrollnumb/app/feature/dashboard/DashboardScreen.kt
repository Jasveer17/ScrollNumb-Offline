package com.scrollnumb.app.feature.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import com.scrollnumb.app.core.util.AvatarHelper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.scrollnumb.app.R
import com.scrollnumb.app.core.designsystem.components.AnimatedOdometer
import com.scrollnumb.app.core.designsystem.components.AppBarBrand
import com.scrollnumb.app.core.designsystem.components.AppIconTile
import com.scrollnumb.app.core.designsystem.components.BrainStateArt
import com.scrollnumb.app.core.designsystem.components.ListCard
import com.scrollnumb.app.core.designsystem.components.NumbnessPill
import com.scrollnumb.app.core.designsystem.components.WarmGrainBackground
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbCorners
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToRelaxation: () -> Unit,
    onNavigateToBreathing: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToShield: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showStreakSheet by remember { mutableStateOf(false) }
    // Live bounds of the features the one-time tour spotlights.
    val tourTargets = remember {
        androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>()
    }
    val ratio = if (uiState.dailyLimit > 0) {
        (uiState.globalScrollCount.toFloat() / uiState.dailyLimit).coerceIn(0f, 1.6f)
    } else 0f

    val brainArt = BrainStateArt.fromRatio(ratio)
    val stateLabel = stringResource(
        when (brainArt) {
            BrainStateArt.FRESH -> R.string.brain_state_fresh
            BrainStateArt.STRAIN -> R.string.brain_state_strain
            BrainStateArt.DRAINED -> R.string.brain_state_drained
            BrainStateArt.TIRED -> R.string.brain_state_tired
            BrainStateArt.ROTTEN -> R.string.brain_state_rotten
        }
    )
    val stateAccent = when (brainArt) {
        BrainStateArt.FRESH -> ScrollNumbColors.Saffron
        BrainStateArt.STRAIN -> ScrollNumbColors.Saffron
        BrainStateArt.DRAINED -> ScrollNumbColors.CoralDim
        BrainStateArt.TIRED -> ScrollNumbColors.Coral
        BrainStateArt.ROTTEN -> ScrollNumbColors.Coral
    }
    // Glow behind the state word is a FILL: warm highlight while healthy
    // (gold dark / lime light — was reading green on paper), coral once over.
    val glowAccent = when (brainArt) {
        BrainStateArt.FRESH, BrainStateArt.STRAIN -> ScrollNumbColors.Highlight
        else -> stateAccent
    }

    Box(modifier = Modifier.fillMaxSize().background(ScrollNumbColors.BackgroundDark)) {
    WarmGrainBackground(modifier = Modifier.fillMaxSize())
    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
            ) {
                // Row (not overlapping Box halves): on narrow screens / large
                // system fonts the brand and the streak-chip cluster used to
                // overlap — weight(1f) makes the brand yield space instead.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppBarBrand(
                        modifier = Modifier.weight(1f),
                        iconSize = 40.dp,
                        fontSize = 24.sp
                    )
                    Row(
                        modifier = Modifier
                            .onGloballyPositioned { tourTargets["topbar"] = it.boundsInRoot() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StreakChip(
                            days = uiState.streakCount,
                            onClick = { showStreakSheet = true }
                        )
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = onNavigateToShield,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Lock,
                                contentDescription = stringResource(R.string.dash_block_status),
                                tint = if (uiState.shieldEnabled) ScrollNumbColors.Saffron else ScrollNumbColors.Cream,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.settings_title),
                                tint = ScrollNumbColors.Cream,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
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
                    // Bottom clearance: this screen draws under the floating
                    // nav bar (see MainActivity), so scrolled-to-end content
                    // must stop above it.
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = com.scrollnumb.app.core.navigation.bottomNavUnderlayPadding()
                    ),
                horizontalAlignment = Alignment.Start
            ) {
                // ── Shared state ──
                val scrollBudget = if (uiState.dailyLimit > 0)
                    (1f - uiState.globalScrollCount.toFloat() / uiState.dailyLimit).coerceIn(0f, 1f)
                else 0f
                val decompressionPercent = (uiState.decompressionTasksToday * 100 / 3).coerceIn(0, 100)
                // "Reclaimed" uses the user's REAL pace today (actual seconds per
                // reel from tracked time) rather than a flat assumption; falls back
                // to a 15s estimate only before any scrolling is recorded.
                val reelsAvoided = (uiState.dailyLimit - uiState.globalScrollCount).coerceAtLeast(0)
                val secPerReel = if (uiState.globalScrollCount > 0 && uiState.totalSecondsToday > 0)
                    uiState.totalSecondsToday.toFloat() / uiState.globalScrollCount
                else 15f
                val minutesSaved = (reelsAvoided * secPerReel / 60f).toInt()
                var showGrassMenu by remember { mutableStateOf(false) }
                var activeGrass by remember { mutableStateOf<String?>(null) }
                var showRemainingCount by remember { mutableStateOf(false) }

                // ── HERO: scroll count (left) + glowing brain state (right) ──
                // Carded like every other section (refined-mockup alignment):
                // InkRaised surface reads as "paper card" in light and "raised
                // ink" in dark via the active palette.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Height wraps the left column (IntrinsicSize.Min), so the
                        // title never touches the card's top edge and the undo row
                        // never clips at the bottom — the old fixed 244dp did both
                        // once card padding was added.
                        .height(IntrinsicSize.Min)
                        .onGloballyPositioned { tourTargets["hero"] = it.boundsInRoot() }
                        .clip(RoundedCornerShape(ScrollNumbCorners.lg))
                        .background(ScrollNumbColors.InkRaised)
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.dash_you_scrolled),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = ScrollNumbColors.Cream
                        )
                        // Scale the hero font down as digit count grows so the
                        // last digit never overflows the weighted column and gets
                        // clipped by the odometer's clipToBounds (e.g. "126" → "12⃝").
                        // Device-proof sizing: scale to THIS screen's width (411dp
                        // reference) and cancel the system font-scale — sp grows
                        // with the user's font-size setting, which pushed the
                        // numeral out of its half-width column on other phones.
                        val heroScale =
                            com.scrollnumb.app.core.designsystem.components.rememberDeviceTextScale()
                        val heroFontSize = when (uiState.globalScrollCount.toString().length) {
                            1, 2 -> 120.sp * heroScale
                            3 -> 92.sp * heroScale
                            else -> 70.sp * heroScale
                        }
                        // FitWidth guarantees the numeral can never clip: if the
                        // digits still outgrow the column on a narrow device or
                        // odd font, the whole odometer scales down to fit.
                        com.scrollnumb.app.core.designsystem.components.FitWidth {
                            AnimatedOdometer(
                                count = uiState.globalScrollCount,
                                style = ScrollNumbType.NumeralHero.copy(
                                    color = ScrollNumbColors.Cream,
                                    fontSize = heroFontSize
                                )
                            )
                        }
                        Text(
                            text = stringResource(R.string.dash_daily_cap, uiState.dailyLimit),
                            style = MaterialTheme.typography.bodyLarge,
                            color = ScrollNumbColors.CreamMuted
                        )
                        // Thin cap-progress bar: sage → coral as the day's
                        // budget is consumed, mirroring the pill's ring.
                        Spacer(Modifier.height(8.dp))
                        val capProgress = if (uiState.dailyLimit > 0)
                            (uiState.globalScrollCount.toFloat() / uiState.dailyLimit).coerceIn(0f, 1f)
                        else 0f
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.88f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(ScrollNumbColors.InkElevated)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(capProgress)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        androidx.compose.ui.graphics.lerp(
                                            ScrollNumbColors.Highlight,
                                            ScrollNumbColors.Coral,
                                            capProgress
                                        )
                                    )
                            )
                        }
                        // Undo an accidental count (e.g. a tap mis-detected as a
                        // scroll). Decrements the total and, via the service's
                        // scrollUpdates collector, the shield/cap too.
                        if (uiState.globalScrollCount > 0) {
                            Spacer(Modifier.height(6.dp))
                            val canUndo = uiState.undosLeft > 0
                            Text(
                                text = stringResource(R.string.dash_undo_last_count, uiState.undosLeft),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (canUndo) ScrollNumbColors.Saffron else ScrollNumbColors.CreamMuted,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(
                                        if (canUndo) Modifier.clickable { viewModel.recoverFocus() }
                                        else Modifier
                                    )
                                    // ≥40dp tall touch target (was 4dp padding on
                                    // ~16dp text — too small to hit reliably).
                                    .padding(vertical = 10.dp, horizontal = 8.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1.05f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Gently breathing glow + slowly rotating rings behind the state word.
                        val glowAnim = rememberInfiniteTransition(label = "heroGlow")
                        val pulse by glowAnim.animateFloat(
                            initialValue = 0.85f,
                            targetValue = 1.08f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2600, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "heroPulse"
                        )
                        val spin by glowAnim.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(36000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "heroSpin"
                        )
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            // Sized for the clipped hero card: outer ring peaks at
                            // 0.40 * 1.08 * 1.15 ≈ 0.497 of the box — as large as
                            // the card allows (mockup look) with no flat cut edge.
                            val baseR = size.minDimension * 0.40f * pulse
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(glowAccent.copy(alpha = 0.42f), Color.Transparent),
                                    center = Offset(cx, cy),
                                    radius = baseR * 1.25f
                                ),
                                radius = baseR * 1.25f,
                                center = Offset(cx, cy)
                            )
                            // two faint orbiting rings
                            rotate(degrees = spin, pivot = Offset(cx, cy)) {
                                drawCircle(
                                    color = glowAccent.copy(alpha = 0.16f),
                                    radius = baseR,
                                    center = Offset(cx, cy),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                                )
                                drawCircle(
                                    color = glowAccent.copy(alpha = 0.10f),
                                    radius = baseR * 1.15f,
                                    center = Offset(cx, cy),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                                )
                            }
                        }
                        com.scrollnumb.app.core.designsystem.components.NeonText(
                            text = stateLabel,
                            style = ScrollNumbType.BrandDisplay.copy(
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Black
                            ),
                            color = stateAccent
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── Touch Some Grass ──
                Box(Modifier.onGloballyPositioned { tourTargets["grass"] = it.boundsInRoot() }) {
                    FadingBrainPill(
                        state = brainArt,
                        count = uiState.globalScrollCount,
                        dailyLimit = uiState.dailyLimit,
                        onClick = { showGrassMenu = true }
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ── Two clean rings: scroll remaining + decompression ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { tourTargets["rings"] = it.boundsInRoot() }
                        .clip(RoundedCornerShape(ScrollNumbCorners.lg))
                        .background(ScrollNumbColors.InkRaised)
                        .padding(vertical = 22.dp, horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        MetricRing(
                            modifier = Modifier.weight(1f),
                            percent = (scrollBudget * 100).toInt(),
                            valueText = if (showRemainingCount) reelsAvoided.toString() else "${(scrollBudget * 100).toInt()}%",
                            label = if (showRemainingCount) "scrolls\nleft" else "scroll\nremaining",
                            color = ScrollNumbColors.Highlight,
                            // Ring sweep is a FILL (neon in light theme); the label
                            // must stay a readable text accent.
                            labelColor = ScrollNumbColors.Saffron,
                            onClick = { showRemainingCount = !showRemainingCount }
                        )
                        MetricRing(
                            modifier = Modifier.weight(1f),
                            percent = decompressionPercent,
                            valueText = "$decompressionPercent%",
                            label = "decompression\ncompleted",
                            color = ScrollNumbColors.Sage,
                            onClick = onNavigateToRelaxation
                        )
                    }
                    if (minutesSaved > 0) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "✨ $minutesSaved min of your life reclaimed today",
                            style = ScrollNumbType.Microcopy,
                            fontWeight = FontWeight.SemiBold,
                            color = ScrollNumbColors.Sage,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Daily tip with brain illustration ──
                val dailyTip = remember { com.scrollnumb.app.core.util.DailyTips.today() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ScrollNumbCorners.lg))
                        .background(ScrollNumbColors.InkRaised)
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "💡 TIP OF THE DAY",
                            style = ScrollNumbType.Microcopy,
                            fontWeight = FontWeight.Bold,
                            color = ScrollNumbColors.Saffron,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = dailyTip,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ScrollNumbColors.CreamDim,
                            lineHeight = 21.sp
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Image(
                        painter = painterResource(id = R.drawable.br_brain_state_0),
                        contentDescription = stringResource(R.string.a11y_brain_state),
                        modifier = Modifier.size(112.dp)
                    )
                }

                Spacer(Modifier.height(22.dp))

                // ── TOP APPS ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ScrollNumbCorners.lg))
                        .clickable(onClick = onNavigateToStats)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.dash_top_apps).uppercase(),
                        style = ScrollNumbType.Microcopy,
                        fontWeight = FontWeight.Bold,
                        color = ScrollNumbColors.Cream
                    )
                    Text(
                        text = stringResource(R.string.dash_today).uppercase(),
                        style = ScrollNumbType.Microcopy,
                        color = ScrollNumbColors.CreamMuted
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ScrollNumbCorners.lg))
                        .clickable(onClick = onNavigateToStats),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AppIconTile(
                        name = "Reels",
                        count = uiState.instagramCount,
                        iconRes = R.drawable.app_icon_instagram
                    )
                    AppIconTile(
                        name = "Shorts",
                        count = uiState.youtubeCount,
                        iconRes = R.drawable.app_icon_youtube
                    )
                    AppIconTile(
                        name = "TikTok",
                        count = uiState.tiktokCount,
                        iconRes = R.drawable.app_icon_tiktok
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ── Block status + Streak: one card, two halves with a divider ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ScrollNumbCorners.lg))
                        .background(ScrollNumbColors.InkRaised),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusHalf(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Lock,
                        iconTint = if (uiState.shieldEnabled) ScrollNumbColors.Saffron else ScrollNumbColors.CreamMuted,
                        label = stringResource(R.string.dash_block_status).uppercase(),
                        value = if (uiState.shieldEnabled) stringResource(R.string.dash_block_on) else stringResource(R.string.dash_block_off),
                        onClick = onNavigateToShield
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(44.dp)
                            .background(ScrollNumbColors.InkLine)
                    )
                    StatusHalf(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.LocalFireDepartment,
                        iconTint = streakColor(uiState.streakCount),
                        label = stringResource(R.string.dash_streak).uppercase(),
                        value = "${uiState.streakCount}d",
                        onClick = { showStreakSheet = true }
                    )
                }

                // ── Grass menu + activities (triggered by Touch Some Grass) ──
                if (showGrassMenu) {
                    GrassMenuSheet(
                        onDismiss = { showGrassMenu = false },
                        onBreathing = { showGrassMenu = false; onNavigateToBreathing() },
                        onDarkRoom = { showGrassMenu = false; activeGrass = "dark" },
                        onMoreExercises = {
                            showGrassMenu = false
                            onNavigateToRelaxation()
                        }
                    )
                }
                if (showStreakSheet) {
                    StreakDialog(
                        days = uiState.streakCount,
                        longest = uiState.longestStreak,
                        freezeTokens = uiState.freezeTokens,
                        onDismiss = { showStreakSheet = false }
                    )
                }
                when (activeGrass) {
                    "dark" -> DarkRoomDialog(
                        awardXp = { viewModel.awardGrassXp("dark") },
                        onComplete = { activeGrass = null },
                        onDismiss = { activeGrass = null }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Footer motto as a soft banner strip (refined-mockup alignment).
                Text(
                    text = stringResource(R.string.dash_footer),
                    style = ScrollNumbType.Microcopy,
                    color = ScrollNumbColors.CreamMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ScrollNumbCorners.lg))
                        .background(ScrollNumbColors.InkRaised.copy(alpha = 0.6f))
                        .padding(vertical = 12.dp, horizontal = 16.dp)
                )

                Spacer(Modifier.height(48.dp))
            }
        }
        }
    }
    // One-time spotlight tour (covers content + top bar; the bottom tabs stay
    // undimmed on their step, which highlights them for free).
    AppTourOverlay(targets = tourTargets)
    }
}

private data class TourStep(
    val emoji: String,
    val label: String,
    val title: String,
    val body: String,
    // Key into the live-captured bounds map; that feature gets SPOTLIT (scrim
    // cut out around it + glowing border). null → plain card, no cutout.
    val targetKey: String? = null
)

/**
 * Spotlight tour shown ONCE, right after a fresh onboarding
 * (APP_TOUR_PENDING armed by [OnboardingViewModel.completeOnboarding]).
 * Each step punches a glowing cutout in the scrim around the real feature
 * (bounds captured via onGloballyPositioned) and shows its card on the
 * opposite half of the screen. Tap anywhere or "Next" to advance.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.AppTourOverlay(
    targets: Map<String, androidx.compose.ui.geometry.Rect>
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("scroll_numb_prefs", android.content.Context.MODE_PRIVATE)
    }
    var show by remember {
        mutableStateOf(
            prefs.getBoolean("APP_TOUR_PENDING", false) &&
                !prefs.getBoolean("APP_TOUR_DONE", false)
        )
    }
    if (!show) return
    var step by remember { mutableStateOf(0) }

    val steps = remember {
        listOf(
            TourStep("👋", "QUICK START", "Meet your new dashboard", "Seven quick highlights show exactly how ScrollNumb counts, protects your limit, and rewards better breaks. Use Next so nothing important gets missed."),
            TourStep("🔢", "TODAY", "Your live reel count", "See today's reels, your daily cap, and your brain state here. If a reel is counted by mistake, Undo removes the latest count.", targetKey = "hero"),
            TourStep("🌱", "RECOVER", "Touch Some Grass", "Breathing, math, and dark-room breaks cool your brain and earn XP. Some activities can also unlock extra reels after your cap.", targetKey = "grass"),
            TourStep("📊", "AT A GLANCE", "Your two progress rings", "The left ring shows today's scroll budget. The right tracks decompression; tap it whenever you want a guided recovery activity.", targetKey = "rings"),
            TourStep("🔥", "CONTROL", "Streak, lock, and settings", "The flame tracks under-limit days. The lock blocks reels instantly. The gear contains limits, themes, haptics, permissions, and widgets.", targetKey = "topbar"),
            TourStep("💊", "IN REEL APPS", "The pill and shield", "In Instagram, Shorts, and Snapchat, the floating pill counts live. At your cap, the shield pauses reels until your cooldown ends."),
            TourStep("🏆", "EXPLORE", "Everything else is below", "Stats shows your scrolling trends. Open Profile for your streak, XP, and personal details.")
        )
    }

    fun finish() {
        prefs.edit().putBoolean("APP_TOUR_DONE", true)
            .putBoolean("APP_TOUR_PENDING", false).apply()
        show = false
    }

    val current = steps[step]
    var overlayHeightPx by remember { mutableStateOf(0f) }
    // The overlay itself can be OFFSET inside the composition root (the nav
    // host applies the status-bar inset above us), while target bounds are
    // root-relative — subtract our own origin or every cutout lands too low.
    var overlayOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val target = current.targetKey?.let { targets[it] }?.translate(-overlayOrigin)
    val saffron = ScrollNumbColors.Saffron

    Box(
        modifier = Modifier
            .matchParentSize()
            .onGloballyPositioned {
                overlayHeightPx = it.size.height.toFloat()
                overlayOrigin = it.boundsInRoot().topLeft
            }
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { /* Consume outside taps so the tour cannot be skipped accidentally. */ }
    ) {
        // Scrim with a punched-out, glowing window over the spotlit feature.
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer(
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                )
        ) {
            drawRect(Color(0xCC000000))
            target?.let { t ->
                val pad = 16f
                val topLeft = androidx.compose.ui.geometry.Offset(t.left - pad, t.top - pad)
                val cutSize = androidx.compose.ui.geometry.Size(
                    t.width + pad * 2,
                    t.height + pad * 2
                )
                val corner = androidx.compose.ui.geometry.CornerRadius(36f, 36f)
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = topLeft,
                    size = cutSize,
                    cornerRadius = corner,
                    blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                )
                drawRoundRect(
                    color = saffron,
                    topLeft = topLeft,
                    size = cutSize,
                    cornerRadius = corner,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f)
                )
            }
        }

        // Card sits on the opposite half of the spotlit feature.
        val cardAlignment = when {
            current.targetKey == null && step == 0 -> Alignment.Center
            target == null -> Alignment.BottomCenter
            target.center.y < overlayHeightPx * 0.5f -> Alignment.BottomCenter
            else -> Alignment.TopCenter
        }
        Column(
            modifier = Modifier
                .align(cardAlignment)
                .padding(20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(ScrollNumbColors.InkElevated, ScrollNumbColors.InkRaised)
                    )
                )
                .border(1.dp, ScrollNumbColors.Saffron.copy(alpha = 0.30f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(ScrollNumbColors.Saffron.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = current.emoji, fontSize = 27.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = current.label,
                        color = ScrollNumbColors.Saffron,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    )
                    Text(
                        text = "${step + 1} OF ${steps.size}",
                        color = ScrollNumbColors.CreamMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = current.title,
                color = ScrollNumbColors.Cream,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = current.body,
                color = ScrollNumbColors.CreamDim,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Step dots
                Row {
                    steps.forEachIndexed { i, _ ->
                        Box(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(if (i == step) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == step) ScrollNumbColors.Saffron
                                    else ScrollNumbColors.CreamMuted
                                )
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (step == 0) {
                    Text(
                        text = "Skip tour",
                        color = ScrollNumbColors.CreamMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { finish() }
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                } else {
                    Text(
                        text = "Back",
                        color = ScrollNumbColors.CreamDim,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { step-- }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = if (step == steps.lastIndex) "Done" else "Next",
                    color = ScrollNumbColors.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ScrollNumbColors.Saffron)
                        .clickable { if (step < steps.lastIndex) step++ else finish() }
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                )
            }
        }
    }
}

@Composable
private fun MetricRing(
    percent: Int,
    valueText: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    labelColor: Color = color,
    onClick: () -> Unit
) {
    val animated = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(percent) {
        animated.animateTo(
            (percent / 100f).coerceIn(0f, 1f),
            animationSpec = tween(900)
        )
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 11.dp.toPx()
            val r = (size.minDimension - stroke) / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = color.copy(alpha = 0.16f),
                radius = r,
                center = c,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated.value,
                useCenter = false,
                topLeft = Offset(c.x - r, c.y - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = valueText,
                style = ScrollNumbType.NumeralMedium,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                color = ScrollNumbColors.Cream
            )
            Text(
                text = label,
                style = ScrollNumbType.Microcopy,
                color = labelColor,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
/** Flame color warms up as the streak grows: ember → gold → blaze. */
private fun streakColor(days: Int): Color = when {
    days <= 0 -> ScrollNumbColors.CreamMuted
    days < 3 -> ScrollNumbColors.Sage
    days < 7 -> ScrollNumbColors.Saffron
    days < 14 -> ScrollNumbColors.CoralDim
    days < 30 -> ScrollNumbColors.Coral
    else -> Color(0xFFFF3B30)
}

private fun streakLabel(days: Int): String = when {
    days <= 0 -> "Start today — open less, live more."
    days < 3 -> "Nice start. Keep it going!"
    days < 7 -> "You're building a habit."
    days < 14 -> "One week strong! 🔥"
    days < 30 -> "Unstoppable."
    else -> "Legendary discipline. 👑"
}

@Composable
private fun StreakChip(days: Int, onClick: () -> Unit) {
    val color = streakColor(days)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(ScrollNumbCorners.pill))
            .background(color.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.LocalFireDepartment,
            contentDescription = "Streak",
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text("${days}d", color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun StreakDialog(
    days: Int,
    longest: Int,
    freezeTokens: Int,
    onDismiss: () -> Unit
) {
    val color = streakColor(days)
    val daysToNextFreeze = if (days <= 0) 7 else (7 - (days % 7)).let { if (it == 0) 7 else it }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(ScrollNumbCorners.xl))
                .background(ScrollNumbColors.InkRaised)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (days == 1) "1 day streak" else "$days day streak",
                color = ScrollNumbColors.Cream,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = streakLabel(days),
                color = ScrollNumbColors.CreamDim,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            StreakStatRow("🏆", "Best streak", if (longest == 1) "1 day" else "$longest days")
            StreakStatRow("❄️", "Freezes left", "$freezeTokens")
            StreakStatRow(
                "🛡️", "Next freeze",
                "in $daysToNextFreeze ${if (daysToNextFreeze == 1) "day" else "days"}"
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "A freeze automatically saves your streak on a day you go over your limit — one freeze per missed day. You earn one every 7 days.",
                color = ScrollNumbColors.CreamMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(ScrollNumbCorners.pill))
                    .background(ScrollNumbColors.Saffron)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Got it", color = ScrollNumbColors.Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun StreakStatRow(emoji: String, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Text(label, color = ScrollNumbColors.CreamDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = ScrollNumbColors.Cream, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun StatusHalf(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = ScrollNumbType.Microcopy,
                color = ScrollNumbColors.CreamMuted,
                fontSize = 10.sp
            )
            Text(
                text = value,
                style = ScrollNumbType.NumeralMedium,
                fontSize = 20.sp,
                color = ScrollNumbColors.Cream
            )
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = ScrollNumbColors.CreamMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun FadingBrainPill(
    state: BrainStateArt,
    count: Int,
    dailyLimit: Int,
    onClick: () -> Unit
) {
    val ratio = if (dailyLimit > 0) (count.toFloat() / dailyLimit).coerceIn(0f, 1.6f) else 0f
    val fillAlpha by animateFloatAsState(
        targetValue = (1f - ratio.coerceIn(0f, 1f)).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "pillFillAlpha"
    )
    // Highlight FILL (gold in dark, neon lime in light) with fixed dark
    // content — Ink flips light in the paper theme, which made the label
    // unreadable on a bright fill.
    val baseColor = ScrollNumbColors.Highlight
    val bg by animateColorAsState(
        targetValue = when {
            // At/over the daily cap the button turns a solid theme-aware RED
            // (Coral flips to the right red for dark vs paper themes) instead
            // of the old muted InkElevated, which read as "disabled/transparent"
            // exactly when it most needs to signal "you've hit your limit".
            ratio >= 1f -> ScrollNumbColors.Coral
            else -> baseColor.copy(alpha = 0.85f + 0.15f * fillAlpha)
        },
        animationSpec = tween(durationMillis = 600),
        label = "pillBg"
    )
    // On the red fill the label/arrow must stay high-contrast (cream), not the
    // dark OnHighlight used on the gold fill.
    val textColor = if (ratio >= 1f) ScrollNumbColors.Cream else ScrollNumbColors.OnHighlight
    val arrowVisible = ratio < 1f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(ScrollNumbCorners.pill))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        val context = LocalContext.current
        val avatarResId = remember(count, dailyLimit) {
            AvatarHelper.getScrollStatusAvatar(context, ratio)
        }
        Image(
            painter = painterResource(id = avatarResId),
            contentDescription = stringResource(R.string.a11y_brain_state),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
                .size(48.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Text(
            text = stringResource(R.string.dash_touch_grass),
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                // Reserve the avatar (left) and arrow (right) zones so the
                // centered label can't overlap them on narrow / large-font phones.
                .padding(horizontal = 64.dp)
                .align(Alignment.Center)
        )
        if (arrowVisible) {
            Text(
                text = "→",
                color = textColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 18.dp)
            )
        }
    }
}
