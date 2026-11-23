package com.scrollnumb.app.feature.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.scrollnumb.app.R
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import com.scrollnumb.app.core.designsystem.components.BenefitPill
import com.scrollnumb.app.core.designsystem.components.EmbossedText
import com.scrollnumb.app.core.designsystem.components.NumbnessPill
import com.scrollnumb.app.core.designsystem.components.NumberStepper
import com.scrollnumb.app.core.designsystem.components.PageIndicator
import com.scrollnumb.app.core.designsystem.components.PrimaryButton
import com.scrollnumb.app.core.designsystem.components.PrivacyPromiseCard
import com.scrollnumb.app.core.designsystem.components.WarmGrainBackground
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbCorners
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbType
import com.scrollnumb.app.core.permissions.PermissionsHelper
import com.scrollnumb.app.core.util.AvatarHelper
import kotlinx.coroutines.launch

private const val PAGE_BRAIN_1 = 0
private const val PAGE_BRAIN_2 = 1
private const val PAGE_BRAIN_3 = 2
private const val PAGE_BRAIN_4 = 3
private const val PAGE_BENEFITS = 4
private const val PAGE_LIMITS_MERGED = 5
private const val PAGE_PERMS = 6
private const val PAGE_WIDGET = 7

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    // Resume where the user left off: quitting mid-onboarding and reopening
    // lands on the same page instead of restarting from slide 1.
    val obPrefs = remember {
        context.getSharedPreferences("scroll_numb_prefs", android.content.Context.MODE_PRIVATE)
    }
    val pagerState = rememberPagerState(
        initialPage = remember { obPrefs.getInt("ONBOARD_PAGE", 0).coerceIn(0, 7) },
        pageCount = { 8 }
    )
    LaunchedEffect(pagerState.currentPage) {
        obPrefs.edit().putInt("ONBOARD_PAGE", pagerState.currentPage).apply()
    }
    val scope = rememberCoroutineScope()

    // One permission per story page ("question"): Continue advances the page
    // AND fires that page's single permission ask, so by the time the user
    // reaches the summary PermissionsPage most rows are already green. The
    // summary page stays as the required gate (accessibility + usage access).
    var showAccessibilityAsk by remember { mutableStateOf(false) }
    // Set when the widget-pin sheet was shown; completing onboarding is deferred
    // until the user returns from the launcher so navigating away can't cancel
    // the launcher's pin dialog.
    var widgetPinRequested by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && widgetPinRequested) {
                widgetPinRequested = false
                viewModel.completeOnboarding(onComplete)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val questionNotifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result re-read on the summary page's ON_RESUME check */ }
    fun askPermissionForPage(page: Int) {
        when (page) {
            PAGE_BRAIN_1 -> if (!PermissionsHelper.hasNotificationPermission(context)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    questionNotifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            PAGE_BRAIN_2 -> if (!PermissionsHelper.canDrawOverlays(context)) {
                PermissionsHelper.openOverlaySettings(context)
            }
            PAGE_BRAIN_4 -> if (!PermissionsHelper.isIgnoringBatteryOptimizations(context)) {
                PermissionsHelper.openBatteryOptimizationSettings(context)
            }
            // Accessibility last (needs the Play-policy disclosure dialog);
            // granting it bounces the user straight back into the app.
            PAGE_BENEFITS -> if (!PermissionsHelper.isAccessibilityServiceEnabled(context)) {
                showAccessibilityAsk = true
            }
        }
    }

    // System back = previous page (instead of exiting the flow).
    androidx.activity.compose.BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    // Measured height of the bottom dots+button overlay (incl. nav-bar inset).
    // Slides previously reserved a hardcoded 140dp, but on 3-button-nav phones
    // the overlay is ~156dp tall — the dots sat ON the subtitle text.
    val density = LocalDensity.current
    var bottomOverlayHeight by remember { mutableStateOf(150.dp) }
    val slideBottomInset = bottomOverlayHeight + 12.dp

    Box(modifier = Modifier.fillMaxSize()) {
        WarmGrainBackground(modifier = Modifier.fillMaxSize())

        // Animated aurora drifting behind every slide
        val aurora = rememberInfiniteTransition(label = "obAurora")
        val drift by aurora.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(16000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "obDrift"
        )
        Box(
            modifier = Modifier
                .size(460.dp)
                .graphicsLayer {
                    // dp-based offsets: raw px placed these glows at wildly
                    // different spots per screen density (device-portability
                    // invariant). Values = old px ÷ 2.625 reference density.
                    translationX = (-84f + drift * 114f).dp.toPx()
                    translationY = (76f + drift * 91f).dp.toPx()
                }
                .background(
                    Brush.radialGradient(
                        listOf(ScrollNumbColors.Saffron.copy(alpha = 0.13f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(400.dp)
                .align(Alignment.BottomEnd)
                .graphicsLayer {
                    translationX = (84f - drift * 122f).dp.toPx()
                    translationY = (-46f - drift * 76f).dp.toPx()
                }
                .background(
                    Brush.radialGradient(
                        listOf(ScrollNumbColors.Coral.copy(alpha = 0.10f), Color.Transparent)
                    )
                )
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = pagerState.currentPage != PAGE_PERMS
        ) { page ->
            val active = pagerState.currentPage == page
            when (page) {
                PAGE_BRAIN_1 -> OnboardingSlide(
                    illustration = AvatarHelper.getAvatarForPage(context, "your_brain_starts_everyday"),
                    chapter = "01  •  THE RESET",
                    title = "Your brain wakes\nup brand new.",
                    subtitle = "Every morning you get a clean slate — sharp focus, zero fog. Then the feed comes for it.",
                    active = active,
                    bottomInset = slideBottomInset
                )
                PAGE_BRAIN_2 -> OnboardingSlide(
                    illustration = AvatarHelper.getAvatarForPage(context, "then_you_start_scrolling"),
                    chapter = "02  •  THE PULL",
                    title = "Then the\nscrolling starts.",
                    subtitle = "One reel becomes fifty. Each swipe trades a little of your spark for a hit of nothing.",
                    active = active,
                    bottomInset = slideBottomInset
                )
                PAGE_BRAIN_3 -> OnboardingSlide(
                    illustration = AvatarHelper.getAvatarForPage(context, "every_reel_drains_your_brain_more"),
                    chapter = "03  •  THE COST",
                    title = "Every reel numbs\nyou a little more.",
                    subtitle = "Dopamine spikes. Attention shrinks. The feed gets sharper while you get duller.",
                    active = active,
                    bottomInset = slideBottomInset
                )
                PAGE_BRAIN_4 -> OnboardingSlide(
                    illustration = AvatarHelper.getAvatarForPage(context, "your_ability_to_focus_drops"),
                    chapter = "04  •  THE TURN",
                    title = "Until focus feels\nimpossible.",
                    subtitle = "Books feel slow. Work feels heavy. That's not you — that's the scroll. And it's reversible.",
                    active = active,
                    bottomInset = slideBottomInset
                )
                PAGE_BENEFITS -> OnboardingBenefitsSlide(active = active, bottomInset = slideBottomInset)
                PAGE_LIMITS_MERGED -> OnboardingLimitsMergedSlide(
                    limitValue = uiState.dailyLimit,
                    onLimitChange = viewModel::setDailyLimit,
                    cooldownValue = uiState.blockDurationMins,
                    onCooldownChange = viewModel::setBlockDurationMins,
                    active = active,
                    bottomInset = slideBottomInset
                )
                PAGE_PERMS -> PermissionsPage(
                    // Widget invite comes AFTER the required grants — it was
                    // previously unreachable except through the rapid-tap bug.
                    onComplete = { scope.launch { pagerState.animateScrollToPage(PAGE_WIDGET) } }
                )
                PAGE_WIDGET -> OnboardingWidgetSlide(
                    onAdd = {
                        // Real pin request: the launcher shows its own confirm
                        // sheet. A success-callback PendingIntent is required by
                        // several launchers to even display the dialog. We do
                        // NOT finish onboarding here — finishing navigates away
                        // and some launchers cancel the pending pin sheet with
                        // the caller. Completion happens when the user returns
                        // (ON_RESUME) instead.
                        var requested = false
                        try {
                            val mgr = android.appwidget.AppWidgetManager.getInstance(context)
                            val component = android.content.ComponentName(
                                context, com.scrollnumb.app.widget.ScrollNumbWidgetReceiver::class.java
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                mgr.isRequestPinAppWidgetSupported
                            ) {
                                val cb = android.app.PendingIntent.getBroadcast(
                                    context, 0,
                                    Intent("com.scrollnumb.app.WIDGET_PINNED").setPackage(context.packageName),
                                    android.app.PendingIntent.FLAG_IMMUTABLE or
                                        android.app.PendingIntent.FLAG_UPDATE_CURRENT
                                )
                                requested = mgr.requestPinAppWidget(component, null, cb)
                            }
                        } catch (e: Exception) { /* launcher without pin support */ }
                        // Launcher can't pin (unsupported): don't strand the user
                        // on a dead button — just finish onboarding.
                        if (!requested) viewModel.completeOnboarding(onComplete)
                        else widgetPinRequested = true
                    },
                    onSkip = {
                        viewModel.completeOnboarding(onComplete)
                    }
                )
            }
        }

        // Visible back arrow on every page after the first.
        if (pagerState.currentPage > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 48.dp)
                    .size(44.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(ScrollNumbColors.InkRaised.copy(alpha = 0.75f))
                    .clickable {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = ScrollNumbColors.Cream
                )
            }
        }

        // Play-policy disclosure for the benefits-page accessibility ask.
        if (showAccessibilityAsk) {
            AccessibilityDisclosureDialog(
                onDismiss = { showAccessibilityAsk = false },
                onOpenSettings = {
                    showAccessibilityAsk = false
                    PermissionsHelper.openAccessibilitySettings(context)
                }
            )
        }

        AnimatedVisibility(
            visible = pagerState.currentPage != PAGE_PERMS && pagerState.currentPage != PAGE_WIDGET,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Placed BEFORE the inset/padding modifiers so the reported
                // size includes them — the slides pad by exactly this height.
                .onSizeChanged {
                    bottomOverlayHeight = with(density) { it.height.toDp() }
                }
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            enter = fadeIn(animationSpec = tween(durationMillis = 220)),
            exit = fadeOut(animationSpec = tween(durationMillis = 180))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PageIndicator(
                    pageCount = 8,
                    currentPage = pagerState.currentPage
                )
                Spacer(modifier = Modifier.height(20.dp))
                PrimaryButton(
                    text = stringResource(R.string.onboarding_continue),
                    onClick = {
                        // Rapid-tap guard: a second tap mid-animation used to
                        // read the half-settled currentPage and land PAST the
                        // permission page (straight onto the widget page with
                        // nothing granted). Ignore taps while scrolling and
                        // never let Continue jump beyond PAGE_PERMS.
                        if (!pagerState.isScrollInProgress) {
                            val page = pagerState.currentPage
                            val next = (page + 1).coerceAtMost(PAGE_PERMS)
                            // Persist the TARGET page synchronously BEFORE firing
                            // the ask. Several asks open external Settings, and if
                            // the process is killed there mid-animation the pager
                            // had only saved the current (story) page — so the
                            // return dumped the user back onto a telling slide.
                            obPrefs.edit().putInt("ONBOARD_PAGE", next).apply()
                            askPermissionForPage(page)
                            scope.launch {
                                pagerState.animateScrollToPage(next)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Staggered entrance for one slide element: fade + a gentle 36px rise,
 * starting [delayMillis] after the page becomes the ACTIVE pager page.
 * Resets when the page scrolls away, so swiping back replays it. Deliberately
 * subtle — no scale/bounce — to keep the flow calm and readable.
 */
@Composable
private fun Modifier.slideReveal(active: Boolean, delayMillis: Int): Modifier {
    val progress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = if (active) {
            tween(durationMillis = 520, delayMillis = delayMillis, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 120)
        },
        label = "slideReveal"
    )
    return this.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 36f
    }
}

@Composable
private fun OnboardingSlide(
    illustration: Int,
    chapter: String,
    title: String,
    subtitle: String,
    active: Boolean = true,
    bottomInset: Dp = 150.dp
) {
    // Gentle float + tilt makes the static PNG feel alive.
    val bob = rememberInfiniteTransition(label = "slideBob")
    val lift by bob.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "slideLift"
    )
    val tilt by bob.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(3400, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "slideTilt"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .padding(top = 40.dp, bottom = bottomInset),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            ScrollNumbColors.Saffron.copy(alpha = 0.12f),
                            ScrollNumbColors.InkRaised.copy(alpha = 0.72f)
                        )
                    )
                )
                .border(1.dp, ScrollNumbColors.Saffron.copy(alpha = 0.20f), RoundedCornerShape(30.dp))
                .slideReveal(active, delayMillis = 0)
                .graphicsLayer {
                    translationY = lift
                    rotationZ = tilt
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = illustration),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            )
            Text(
                text = chapter,
                color = ScrollNumbColors.Saffron,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ScrollNumbColors.Ink.copy(alpha = 0.82f))
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Box(Modifier.slideReveal(active, delayMillis = 140)) {
            EmbossedText(
                text = title,
                style = ScrollNumbType.BrandHero,
                faceColor = ScrollNumbColors.Cream,
                depth = 4
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = ScrollNumbColors.CreamDim,
            modifier = Modifier.slideReveal(active, delayMillis = 300)
        )
    }
}

@Composable
private fun OnboardingBenefitsSlide(active: Boolean = true, bottomInset: Dp = 150.dp) {
    val context = LocalContext.current
    val avatarResId = remember { AvatarHelper.getAvatarForPage(context, "within_a_week_youll_scroll_less") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .padding(top = 64.dp, bottom = bottomInset),
        horizontalAlignment = Alignment.Start
    ) {
        Image(
            painter = painterResource(id = avatarResId),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .slideReveal(active, delayMillis = 0)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(Modifier.slideReveal(active, delayMillis = 120)) {
            EmbossedText(
                text = "One week.\nYou'll feel it.",
                style = ScrollNumbType.BrandHero,
                faceColor = ScrollNumbColors.Cream,
                depth = 4
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "People who see their scroll count cut it nearly in half within 7 days. Here's what comes back:",
            style = MaterialTheme.typography.bodyLarge,
            color = ScrollNumbColors.CreamDim,
            modifier = Modifier.slideReveal(active, delayMillis = 240)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.slideReveal(active, delayMillis = 380)
        ) {
            BenefitPill(text = stringResource(R.string.onboarding_benefit_focus))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.slideReveal(active, delayMillis = 500)
        ) {
            BenefitPill(text = stringResource(R.string.onboarding_benefit_mental))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.slideReveal(active, delayMillis = 620)
        ) {
            BenefitPill(text = stringResource(R.string.onboarding_benefit_sleep))
        }
    }
}

@Composable
private fun OnboardingLimitsMergedSlide(
    limitValue: Int,
    onLimitChange: (Int) -> Unit,
    cooldownValue: Int,
    onCooldownChange: (Int) -> Unit,
    active: Boolean = true,
    bottomInset: Dp = 150.dp
) {
    val context = LocalContext.current
    val avatars = remember { AvatarHelper.getSmartLimitsAvatars(context) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Scrollable: the full inset (dots + button + nav bar) can push the
            // steppers past the fold on short screens — scrolling beats clipping.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
            .padding(top = 8.dp, bottom = bottomInset),
        horizontalAlignment = Alignment.Start
    ) {
        // Scale the avatar pair to the actual screen: at full size the row is
        // ~356dp wide, which clipped on 320-360dp phones.
        val avatarScale = (
            androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp / 411f
            ).coerceIn(0.7f, 1f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp * avatarScale)
                .slideReveal(active, delayMillis = 0),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = avatars.first),
                contentDescription = null,
                modifier = Modifier
                    .size(160.dp * avatarScale)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Image(
                painter = painterResource(id = avatars.second),
                contentDescription = null,
                modifier = Modifier
                    .size(180.dp * avatarScale)
                    .offset(x = (-24).dp * avatarScale),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(Modifier.slideReveal(active, delayMillis = 120)) {
            EmbossedText(
                text = "Draw your line.",
                style = ScrollNumbType.BrandHero,
                faceColor = ScrollNumbColors.Cream,
                depth = 4
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Your daily reel allowance — and the cooldown that guards it when you cross it.",
            style = MaterialTheme.typography.bodyLarge,
            color = ScrollNumbColors.CreamDim,
            modifier = Modifier.slideReveal(active, delayMillis = 220)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(Modifier.slideReveal(active, delayMillis = 340)) {
            NumberStepper(
                label = stringResource(R.string.block_allow_up_to),
                value = limitValue,
                onValueChange = onLimitChange,
                step = 5,
                range = 5..200,
                suffix = " reels"
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(Modifier.slideReveal(active, delayMillis = 460)) {
            NumberStepper(
                label = stringResource(R.string.block_cooldown),
                value = cooldownValue,
                onValueChange = onCooldownChange,
                step = 1,
                range = 2..120,
                suffix = " mins"
            )
        }
    }
}

@Composable
private fun OnboardingWidgetSlide(
    onAdd: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    // Preview mirrors the REAL widget (ScrollNumbWidget): the user's own
    // boy/girl avatar in the gold status ring — not the old brain art.
    val avatarRes = remember { AvatarHelper.getScrollStatusAvatar(context, 0f) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .padding(top = 64.dp, bottom = 100.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    // Cap, don't fix: 270dp + the page's 56dp padding overflowed
                    // 320dp-wide phones.
                    .widthIn(max = 270.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF181410))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFD642))
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = avatarRes),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFF181410))
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "FRESH",
                        color = Color(0xFFFFD642),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "12",
                        color = Color(0xFFF5F0E8),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "reels of 50",
                        color = Color(0xB3F5F0E8),
                        fontSize = 11.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_widget_title),
            style = ScrollNumbType.BrandHero,
            color = ScrollNumbColors.Cream
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_widget_sub),
            style = MaterialTheme.typography.bodyLarge,
            color = ScrollNumbColors.CreamDim
        )
        Spacer(modifier = Modifier.height(28.dp))
        NumbnessPill(
            label = stringResource(R.string.onboarding_add_widget),
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        NumbnessPill(
            label = stringResource(R.string.onboarding_not_now),
            onClick = onSkip,
            inverse = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PermissionsPage(onComplete: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasAccessibility by remember { mutableStateOf(PermissionsHelper.isAccessibilityServiceEnabled(context)) }
    var hasNotif by remember { mutableStateOf(PermissionsHelper.hasNotificationPermission(context)) }
    var hasUsageAccess by remember { mutableStateOf(PermissionsHelper.hasUsageStatsPermission(context)) }
    var hasOverlay by remember { mutableStateOf(PermissionsHelper.canDrawOverlays(context)) }
    var hasBattery by remember { mutableStateOf(PermissionsHelper.isIgnoringBatteryOptimizations(context)) }
    var showAccessibilityGuide by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotif = PermissionsHelper.hasNotificationPermission(context)
        // Permanently denied (dialog auto-dismissed, no rationale): the tap
        // otherwise did NOTHING. Take the user to ScrollNumb's own
        // notification-settings page instead.
        if (!granted && !hasNotif) {
            val activity = context as? android.app.Activity
            val canAskAgain = activity != null &&
                androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, android.Manifest.permission.POST_NOTIFICATIONS
                )
            if (!canAskAgain) {
                PermissionsHelper.openAppNotificationSettings(context)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccessibility = PermissionsHelper.isAccessibilityServiceEnabled(context)
                hasNotif = PermissionsHelper.hasNotificationPermission(context)
                hasUsageAccess = PermissionsHelper.hasUsageStatsPermission(context)
                hasOverlay = PermissionsHelper.canDrawOverlays(context)
                hasBattery = PermissionsHelper.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Required for the pill/shield to work: accessibility + usage. The pill and
    // shield are accessibility overlays, so "Display over other apps" is NOT
    // actually needed — and on Android 11+ it's the hardest grant (App-Info
    // hop), so requiring it could dead-end onboarding. It stays as a silently
    // optional card, like notifications and battery.
    val allGranted = hasAccessibility && hasUsageAccess

    val avatarResId = remember { AvatarHelper.getAvatarForPage(context, "permissions") }
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                drawContent()
                if (scrollState.maxValue > 0) {
                    val viewHeight = size.height
                    val contentHeight = size.height + scrollState.maxValue
                    val scrollbarHeight = (viewHeight / contentHeight) * viewHeight
                    val scrollbarOffset = (scrollState.value.toFloat() / scrollState.maxValue) * (viewHeight - scrollbarHeight)
                    
                    drawRoundRect(
                        color = Color(0x66F4EFEA),
                        topLeft = androidx.compose.ui.geometry.Offset(x = size.width - 3.dp.toPx(), y = scrollbarOffset),
                        size = androidx.compose.ui.geometry.Size(width = 3.dp.toPx(), height = scrollbarHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                    )
                }
            }
            .verticalScroll(scrollState)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                ScrollNumbColors.Saffron.copy(alpha = 0.14f),
                                ScrollNumbColors.InkRaised
                            )
                        )
                    )
                    .border(1.dp, ScrollNumbColors.Saffron.copy(alpha = 0.24f), RoundedCornerShape(28.dp))
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(190.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(ScrollNumbColors.Saffron.copy(alpha = 0.18f), Color.Transparent)
                            )
                        )
                )
                Image(
                    painter = painterResource(id = avatarResId),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = "FINAL CHECKPOINT",
                    color = ScrollNumbColors.Saffron,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ScrollNumbColors.Ink.copy(alpha = 0.82f))
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            EmbossedText(
                text = "Last step.\nPower us up.",
                style = ScrollNumbType.BrandHero,
                faceColor = ScrollNumbColors.Cream,
                depth = 4
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enable counting on supported feeds, the shield, and reminders.",
                style = MaterialTheme.typography.bodyMedium,
                color = ScrollNumbColors.CreamDim
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Power meter — fills as grants land, selling "power us up"
            // literally. Greens out when the two required grants are in.
            val grantedCount = listOf(hasAccessibility, hasOverlay, hasUsageAccess, hasNotif, hasBattery).count { it }
            val meter by animateFloatAsState(
                targetValue = grantedCount / 5f,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                label = "permMeter"
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ScrollNumbColors.InkRaised)
                    .border(1.dp, ScrollNumbColors.InkLine, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SCROLLNUMB POWER",
                        style = ScrollNumbType.Microcopy,
                        color = ScrollNumbColors.Cream,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (allGranted) "CORE READY" else "$grantedCount/5 ONLINE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = if (allGranted) ScrollNumbColors.Sage else ScrollNumbColors.Saffron
                    )
                }
                Spacer(modifier = Modifier.height(7.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(ScrollNumbColors.InkWhisper)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(meter.coerceAtLeast(0.04f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        ScrollNumbColors.Saffron,
                                        if (allGranted) ScrollNumbColors.Sage else Color(0xFFFFE382)
                                    )
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            PrivacyPromiseCard()

            Spacer(modifier = Modifier.height(14.dp))

            PermissionCard(
                number = 1,
                title = stringResource(R.string.perm_1_title),
                subtitle = stringResource(R.string.perm_1_sub),
                isGranted = hasAccessibility,
                onClick = {
                    if (!hasAccessibility) {
                        showAccessibilityGuide = true
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            PermissionCard(
                number = 2,
                title = stringResource(R.string.perm_2_title),
                subtitle = stringResource(R.string.perm_2_sub),
                isGranted = hasOverlay,
                onClick = {
                    if (!hasOverlay) {
                        PermissionsHelper.openOverlaySettings(context)
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            PermissionCard(
                number = 3,
                title = stringResource(R.string.perm_3_title),
                subtitle = stringResource(R.string.perm_3_sub),
                isGranted = hasUsageAccess,
                onClick = {
                    if (!hasUsageAccess) {
                        PermissionsHelper.openUsageAccessSettings(context)
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            PermissionCard(
                number = 4,
                title = stringResource(R.string.perm_4_title),
                subtitle = stringResource(R.string.perm_4_sub),
                isGranted = hasNotif,
                onClick = {
                    if (!hasNotif) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            PermissionsHelper.openAppNotificationSettings(context)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Battery exemption is recommended, not required: the app-scoped
            // "ignore battery optimization" dialog grants it in place, but some
            // OEMs refuse — so it never blocks "Get started" (allGranted).
            PermissionCard(
                number = 5,
                title = stringResource(R.string.perm_5_title),
                subtitle = stringResource(R.string.perm_5_sub),
                isGranted = hasBattery,
                onClick = {
                    if (!hasBattery) {
                        PermissionsHelper.openBatteryOptimizationSettings(context)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            NumbnessPill(
                label = if (allGranted)
                    stringResource(R.string.onboarding_get_started)
                else
                    stringResource(R.string.onboarding_grant_perms),
                onClick = onComplete,
                enabled = allGranted,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showAccessibilityGuide) {
        AccessibilityDisclosureDialog(
            onDismiss = { showAccessibilityGuide = false },
            onOpenSettings = {
                showAccessibilityGuide = false
                PermissionsHelper.openAccessibilitySettings(context)
            }
        )
    }
}

@Composable
private fun PermissionCard(
    number: Int,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onClick: () -> Unit,
    emoji: String = "",
    required: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ScrollNumbCorners.lg))
            .background(ScrollNumbColors.InkRaised)
            .then(
                if (isGranted) Modifier
                else Modifier.border(
                    1.dp,
                    if (required) ScrollNumbColors.Saffron.copy(alpha = 0.35f)
                    else ScrollNumbColors.InkLine,
                    RoundedCornerShape(ScrollNumbCorners.lg)
                )
            )
            .clickable(enabled = !isGranted, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (isGranted) ScrollNumbColors.Sage.copy(alpha = 0.18f)
                    else ScrollNumbColors.InkWhisper
                )
        ) {
            if (isGranted) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = ScrollNumbColors.Sage
                )
            } else if (emoji.isNotEmpty()) {
                Text(text = emoji, fontSize = 18.sp)
            } else {
                Text(
                    text = number.toString(),
                    style = ScrollNumbType.NumeralMedium,
                    color = ScrollNumbColors.CreamDim
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = ScrollNumbType.Microcopy,
                    color = ScrollNumbColors.Cream
                )
                if (required && !isGranted) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "REQUIRED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = ScrollNumbColors.Saffron,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ScrollNumbColors.Saffron.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = ScrollNumbColors.CreamMuted
            )
        }
        if (!isGranted) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "→",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = ScrollNumbColors.CreamMuted
            )
        }
    }
}

private fun isAccessibilityServiceEnabled(
    context: android.content.Context,
    service: Class<out android.accessibilityservice.AccessibilityService>
): Boolean {
    var accessibilityEnabled = 0
    val serviceId = "${context.packageName}/${service.name}"
    try {
        accessibilityEnabled = Settings.Secure.getInt(
            context.applicationContext.contentResolver,
            android.provider.Settings.Secure.ACCESSIBILITY_ENABLED
        )
    } catch (e: Settings.SettingNotFoundException) {
    }
    val mStringColonSplitter = TextUtils.SimpleStringSplitter(':')
    if (accessibilityEnabled == 1) {
        val settingValue = Settings.Secure.getString(
            context.applicationContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (settingValue != null) {
            mStringColonSplitter.setString(settingValue)
            while (mStringColonSplitter.hasNext()) {
                val accessibilityService = mStringColonSplitter.next()
                if (accessibilityService.equals(serviceId, ignoreCase = true)) {
                    return true
                }
            }
        }
    }
    return false
}
