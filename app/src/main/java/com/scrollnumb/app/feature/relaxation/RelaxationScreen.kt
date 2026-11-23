package com.scrollnumb.app.feature.relaxation

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.scrollnumb.app.R
import com.scrollnumb.app.core.designsystem.components.NumbnessPill
import com.scrollnumb.app.core.designsystem.components.WarmGrainBackground
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbCorners
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelaxationScreen(
    onBack: () -> Unit,
    initialTab: Int = 0,
    viewModel: RelaxationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // Three swipeable pages: Math Focus (0), Breathe (1), Dark Room (2).
    // Swiping left advances (Math→Breathe→Dark), swiping right goes back —
    // the tab pills and the pager stay in sync in both directions.
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = initialTab.coerceIn(0, 2),
        pageCount = { 3 }
    )
    val scope = rememberCoroutineScope()
    val selectedTab = pagerState.currentPage
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val backgroundInteraction = remember { MutableInteractionSource() }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 20.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = ScrollNumbColors.Cream
                    )
                }
                Text(
                    text = stringResource(R.string.relax_title),
                    style = ScrollNumbType.BrandDisplay,
                    fontWeight = FontWeight.Bold,
                    color = ScrollNumbColors.Cream
                )
            }
        },
        containerColor = ScrollNumbColors.BackgroundDark
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            WarmGrainBackground(modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = backgroundInteraction,
                        indication = null
                    ) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                    // NO outer verticalScroll: it stole the horizontal drag from
                    // the pager so swiping between tabs didn't work. The tab bar
                    // stays fixed and the pager fills the rest, owning all
                    // horizontal gestures; each page scrolls vertically on its own.
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ScrollNumbCorners.pill))
                        .background(ScrollNumbColors.InkRaised)
                        .padding(4.dp)
                ) {
                    val tabWidth = maxWidth / 3
                    // Follow the live swipe fraction, not just the settled page,
                    // so the indicator glides under the finger during a drag.
                    val pageProgress = pagerState.currentPage + pagerState.currentPageOffsetFraction
                    Box(
                        modifier = Modifier
                            .offset(x = tabWidth * pageProgress)
                            .width(tabWidth)
                            .height(44.dp)
                            .clip(RoundedCornerShape(ScrollNumbCorners.pill))
                            .background(ScrollNumbColors.Saffron)
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TabPill(
                            label = stringResource(R.string.relax_math),
                            selected = selectedTab == 0,
                            modifier = Modifier.weight(1f)
                        ) { scope.launch { pagerState.animateScrollToPage(0) } }
                        TabPill(
                            label = stringResource(R.string.relax_breathe),
                            selected = selectedTab == 1,
                            modifier = Modifier.weight(1f)
                        ) { scope.launch { pagerState.animateScrollToPage(1) } }
                        TabPill(
                            label = stringResource(R.string.relax_dark_room),
                            selected = selectedTab == 2,
                            modifier = Modifier.weight(1f)
                        ) { scope.launch { pagerState.animateScrollToPage(2) } }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalAlignment = Alignment.Top
                ) { page ->
                    // Each page scrolls vertically on its own (keeps the math
                    // field reachable above the keyboard) without competing with
                    // the pager's horizontal swipe.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (page) {
                            0 -> MathFocusContent(uiState, viewModel)
                            1 -> BreathingContent(uiState, viewModel)
                            else -> DarkRoomContent(viewModel)
                        }
                    }
                }
            }

            if (uiState.showRewardAnimation) {
                RewardOverlay(
                    amount = uiState.rewardAmount,
                    isBreathing = uiState.rewardIsBreathing,
                    label = if (uiState.rewardIsBreathing) {
                        stringResource(R.string.relax_breathe_done)
                    } else {
                        stringResource(R.string.relax_correct)
                    }
                )
            }
        }
    }
}

@Composable
private fun RewardOverlay(amount: Int, label: String, isBreathing: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScrollNumbColors.Ink.copy(alpha = 0.55f))
    ) {
        // Mathematical symbol burst radiates from center, rains toward the badge
        com.scrollnumb.app.feature.dashboard.VictoryBurst(
            glyphs = if (isBreathing) {
                listOf("✦", "○", "·", "✧", "○")
            } else {
                listOf("✦", "★", "+", "÷", "x", "Σ", "π", "√")
            },
            message = "",
            onFinished = { }
        )
        val scale by animateFloatAsState(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "reward_scale"
        )
        // Positioned in the lower third, not dead-center.
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.62f))
            Column(
                modifier = Modifier
                    .scale(scale)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(
                                ScrollNumbColors.InkRaised,
                                ScrollNumbColors.InkElevated
                            )
                        )
                    )
                    .border(
                        1.5.dp,
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(ScrollNumbColors.Saffron, ScrollNumbColors.Coral)
                        ),
                        RoundedCornerShape(28.dp)
                    )
                    .padding(horizontal = 30.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Soft success emblem — a sage-ringed check (math) / bloom
                // (breathing) that lands with the bounce for a calmer, more
                // rewarding beat than a bare text pop.
                val emblemColor = if (isBreathing) ScrollNumbColors.Saffron else ScrollNumbColors.Sage
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(emblemColor.copy(alpha = 0.16f))
                        .border(1.5.dp, emblemColor.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isBreathing) "✦" else "✓",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = emblemColor
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = if (isBreathing) "Focus restored" else label,
                    style = ScrollNumbType.BrandDisplay,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = ScrollNumbColors.Saffron,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isBreathing) label else "Next puzzle unlocked",
                    style = ScrollNumbType.Microcopy,
                    color = ScrollNumbColors.CreamMuted,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(ScrollNumbCorners.pill))
                        .background(ScrollNumbColors.Saffron.copy(alpha = 0.16f))
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (amount > 0) stringResource(R.string.relax_correct_xp, amount)
                        else stringResource(R.string.relax_cap_reached),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ScrollNumbColors.Cream
                    )
                }
            }
            Spacer(Modifier.weight(0.38f))
        }
    }
}

@Composable
private fun TabPill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(ScrollNumbCorners.pill))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) ScrollNumbColors.Ink else ScrollNumbColors.CreamDim
        )
    }
}

@Composable
fun MathFocusContent(uiState: RelaxationUiState, viewModel: RelaxationViewModel) {
    var answerInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val submitAnswer = {
        focusManager.clearFocus()
        keyboardController?.hide()
        viewModel.checkMathAnswer(answerInput)
    }

    LaunchedEffect(uiState.currentMathProblem) {
        answerInput = ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .clip(RoundedCornerShape(ScrollNumbCorners.lg))
            .background(ScrollNumbColors.InkRaised)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.relax_math_caption),
            style = ScrollNumbType.Microcopy,
            color = ScrollNumbColors.CreamMuted
        )

        Spacer(modifier = Modifier.height(20.dp))

        val problem = uiState.currentMathProblem
        if (problem != null) {
            Text(
                text = "${problem.num1} ${problem.operator} ${problem.num2} = ?",
                style = ScrollNumbType.BrandDisplay.copy(fontSize = 28.sp, fontWeight = FontWeight.Black),
                color = ScrollNumbColors.Cream
            )

            Spacer(modifier = Modifier.height(20.dp))

            val fieldBorder = when (uiState.isMathCorrect) {
                true -> ScrollNumbColors.Sage
                false -> ScrollNumbColors.Coral
                else -> ScrollNumbColors.CreamDim
            }
            OutlinedTextField(
                value = answerInput,
                onValueChange = { answerInput = it },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { submitAnswer() }
                ),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    fontSize = 24.sp,
                    color = ScrollNumbColors.Cream
                ),
                modifier = Modifier.width(150.dp),
                isError = uiState.isMathCorrect == false,
                shape = RoundedCornerShape(ScrollNumbCorners.md),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = fieldBorder,
                    unfocusedBorderColor = fieldBorder,
                    cursorColor = ScrollNumbColors.Saffron
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            val feedbackHeight = if (uiState.mathFeedbackVisible) 28.dp else 0.dp
            Box(
                modifier = Modifier.height(feedbackHeight),
                contentAlignment = Alignment.Center
            ) {
                when (uiState.isMathCorrect) {
                    true -> Text(
                        text = stringResource(R.string.relax_correct),
                        color = ScrollNumbColors.Sage,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    false -> Text(
                        text = stringResource(R.string.relax_try_again),
                        color = ScrollNumbColors.Coral,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    null -> {}
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            NumbnessPill(
                label = stringResource(R.string.relax_submit),
                onClick = submitAnswer,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun DarkRoomContent(viewModel: RelaxationViewModel) {
    var running by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .clip(RoundedCornerShape(ScrollNumbCorners.lg))
            .background(ScrollNumbColors.InkRaised)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.relax_dark_room_caption),
            style = ScrollNumbType.Microcopy,
            color = ScrollNumbColors.CreamMuted,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(ScrollNumbColors.Ink)
                .border(1.5.dp, ScrollNumbColors.InkLine, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🌑", fontSize = 44.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        NumbnessPill(
            label = stringResource(R.string.relax_dark_room_start),
            onClick = { running = true },
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (running) {
        com.scrollnumb.app.feature.dashboard.DarkRoomDialog(
            awardXp = { viewModel.awardDarkRoomXp() },
            onComplete = { running = false },
            onDismiss = { running = false }
        )
    }
}

@Composable
fun BreathingContent(uiState: RelaxationUiState, viewModel: RelaxationViewModel) {
    val isActive = uiState.isBreathingActive
    val phase = uiState.breathingPhase

    val targetScale = when (phase) {
        BreathingPhase.INHALE, BreathingPhase.HOLD_IN -> 1.4f
        BreathingPhase.EXHALE, BreathingPhase.HOLD_OUT -> 0.85f
        BreathingPhase.IDLE -> 1f
    }
    val animDuration = when (phase) {
        BreathingPhase.INHALE, BreathingPhase.EXHALE -> 4000
        else -> 200
    }

    val scale by animateFloatAsState(
        targetValue = if (isActive) targetScale else 1f,
        animationSpec = tween(
            durationMillis = animDuration,
            easing = if (phase == BreathingPhase.INHALE || phase == BreathingPhase.EXHALE)
                FastOutSlowInEasing
            else
                LinearEasing
        ),
        label = "breathe_scale"
    )

    val phaseLabel = when (phase) {
        BreathingPhase.INHALE -> stringResource(R.string.relax_breathe_in)
        BreathingPhase.EXHALE -> stringResource(R.string.relax_breathe_out)
        BreathingPhase.HOLD_IN, BreathingPhase.HOLD_OUT -> stringResource(R.string.relax_hold)
        BreathingPhase.IDLE -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.relax_breathe_caption),
            style = ScrollNumbType.Microcopy,
            color = ScrollNumbColors.CreamMuted
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Fixed-size arena that RESERVES the fully-inflated footprint (halo
        // 160dp × 1.4 ≈ 224dp + margin). .scale() is a draw-only transform, so
        // without this reserved box the expanding circle painted OVER the
        // caption above and the timer below at peak inhale.
        Box(
            modifier = Modifier.size(248.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(ScrollNumbColors.Saffron.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .clip(CircleShape)
                    .background(ScrollNumbColors.Saffron),
                contentAlignment = Alignment.Center
            ) {
                if (isActive && phaseLabel.isNotEmpty()) {
                    // The inner circle is a fixed 108dp — cancel the system font
                    // scale so "Breathe out" never overflows it (same rule as
                    // the dashboard hero numeral).
                    val labelScale =
                        com.scrollnumb.app.core.designsystem.components.rememberDeviceTextScale()
                    Text(
                        text = phaseLabel,
                        style = ScrollNumbType.BrandDisplay.copy(
                            fontSize = ScrollNumbType.BrandDisplay.fontSize * labelScale,
                            lineHeight = ScrollNumbType.BrandDisplay.lineHeight * labelScale
                        ),
                        fontWeight = FontWeight.Bold,
                        color = ScrollNumbColors.Ink,
                        textAlign = TextAlign.Center
                    )
                }
            }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isActive) {
            Text(
                text = stringResource(R.string.relax_breathing_remaining, uiState.breathingTimeLeft),
                style = ScrollNumbType.BrandDisplay,
                fontWeight = FontWeight.Bold,
                color = ScrollNumbColors.Cream
            )
            Spacer(modifier = Modifier.height(12.dp))
            NumbnessPill(
                label = stringResource(R.string.relax_stop_breathing),
                onClick = { viewModel.toggleBreathing() },
                inverse = true,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            NumbnessPill(
                label = stringResource(R.string.relax_start_breathing),
                onClick = { viewModel.toggleBreathing() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
