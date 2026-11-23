package com.scrollnumb.app.feature.quiz

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrollnumb.app.GenderSelectionActivity
import com.scrollnumb.app.R
import com.scrollnumb.app.core.designsystem.components.PrimaryButton
import com.scrollnumb.app.core.designsystem.components.PrivacyPromiseCard
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbTheme
import com.scrollnumb.app.core.permissions.PermissionsHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

// ── Flow model ────────────────────────────────────────────────────────────

/**
 * One answer choice. Shows either an animated glyph OR a real app icon as its
 * visual hook; [label] is what gets saved.
 */
private data class Option(
    val label: String,
    val glyph: QuizGlyph? = null,
    val iconRes: Int? = null
)

private data class Question(
    val title: String,
    val options: List<Option>
)

/** Which animated mini-mock to show above a permission ask. */
enum class PermVisual { NOTIF, ACCESSIBILITY, OVERLAY, BATTERY, USAGE }

private data class PermissionStep(
    val title: String,
    val benefit: String,
    // Plain "what happens when you tap Allow" line — kills the confusion of
    // landing on an unfamiliar system settings screen.
    val hint: String,
    val visual: PermVisual,
    val check: (Context) -> Boolean,
    val launch: (Context) -> Unit,
    val isRuntime: Boolean = false
)

private sealed class QuizStep {
    data class Ask(val q: Question, val index: Int) : QuizStep()
    data class Permission(val p: PermissionStep, val index: Int, val total: Int) : QuizStep()
    object Done : QuizStep()
}

@AndroidEntryPoint
class QuizActivity : AppCompatActivity() {

    private val questions = listOf(
        Question(
            "How many hours a day do you spend scrolling?",
            listOf(
                Option("Under 1 hour", glyph = QuizGlyph.CLOCK_LOW),
                Option("1–3 hours", glyph = QuizGlyph.CLOCK_MID),
                Option("3–5 hours", glyph = QuizGlyph.CLOCK_HIGH),
                Option("Honestly… too many", glyph = QuizGlyph.CLOCK_MAX)
            )
        ),
        Question(
            "When do you doomscroll the most?",
            listOf(
                Option("Late at night", glyph = QuizGlyph.MOON),
                Option("First thing in the morning", glyph = QuizGlyph.SUNRISE),
                Option("During work or study", glyph = QuizGlyph.BOOK),
                Option("All day, every day", glyph = QuizGlyph.ALLDAY)
            )
        ),
        Question(
            "What do you want back the most?",
            listOf(
                Option("My focus", glyph = QuizGlyph.TARGET),
                Option("My sleep", glyph = QuizGlyph.SLEEP),
                Option("My peace of mind", glyph = QuizGlyph.LOTUS),
                Option("My time", glyph = QuizGlyph.HOURGLASS)
            )
        ),
        Question(
            "Which app eats your hours?",
            listOf(
                Option("Instagram", iconRes = R.drawable.app_icon_instagram),
                Option("YouTube Shorts", iconRes = R.drawable.app_icon_youtube),
                Option("Snapchat", iconRes = R.drawable.app_icon_snapchat),
                Option("All of them", glyph = QuizGlyph.FLAME)
            )
        ),
        Question(
            "How tough should we be on you?",
            listOf(
                Option("Gentle nudges", glyph = QuizGlyph.FEATHER),
                Option("Balanced", glyph = QuizGlyph.BALANCE),
                Option("Strict", glyph = QuizGlyph.SHIELD),
                Option("Show no mercy", glyph = QuizGlyph.NOMERCY)
            )
        )
    )

    private val notifPermissionRequest =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
            // Permanently denied → Android auto-dismisses the dialog and the
            // "Allow" tap did NOTHING. Redirect to ScrollNumb's own
            // notification-settings page so the step always goes somewhere.
            if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                )
            ) {
                PermissionsHelper.openAppNotificationSettings(this)
            }
        }

    private fun permissionSteps(): List<PermissionStep> = listOf(
        PermissionStep(
            title = "Stay ahead of your limit",
            benefit = "Get one clear warning before you reach your daily reel limit. No spam.",
            hint = "Tap Allow when Android asks about notifications.",
            visual = PermVisual.NOTIF,
            check = { PermissionsHelper.hasNotificationPermission(it) },
            launch = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifPermissionRequest.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            isRuntime = true
        ),
        PermissionStep(
            title = "Let us count your reels",
            // Play policy requires a prominent, plain disclosure of Accessibility
            // use — keep the "reads only scroll events / stays on device" clause.
            benefit = "This makes ScrollNumb work. It only sees that a reel changed — never what you watch or type. Stays on your phone.",
            hint = "Find ScrollNumb, then flip the switch on.",
            visual = PermVisual.ACCESSIBILITY,
            check = { PermissionsHelper.isAccessibilityServiceEnabled(it) },
            launch = { PermissionsHelper.openAccessibilitySettings(it) }
        ),
        PermissionStep(
            title = "Show the counter on top",
            benefit = "So your count floats over Instagram and YouTube.",
            hint = "Turn on \"Display over other apps\".",
            visual = PermVisual.OVERLAY,
            check = { PermissionsHelper.canDrawOverlays(it) },
            launch = { PermissionsHelper.openOverlaySettings(it) }
        ),
        PermissionStep(
            title = "Keep counting in the background",
            benefit = "Stops Android from freezing your count mid-scroll.",
            hint = "Tap Allow.",
            visual = PermVisual.BATTERY,
            check = { PermissionsHelper.isIgnoringBatteryOptimizations(it) },
            launch = { PermissionsHelper.openBatteryOptimizationSettings(it) }
        ),
        PermissionStep(
            title = "Know which app is open",
            benefit = "So the counter shows up only in reel apps.",
            hint = "Find ScrollNumb, then allow usage access.",
            visual = PermVisual.USAGE,
            check = { PermissionsHelper.hasUsageStatsPermission(it) },
            launch = { PermissionsHelper.openUsageAccessSettings(it) }
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Interleave questions with only the permissions required for core tracking.
        val perms = permissionSteps()
        val steps = buildList {
            questions.forEachIndexed { i, q ->
                add(QuizStep.Ask(q, i))
                perms.getOrNull(i)?.let { add(QuizStep.Permission(it, i, perms.size)) }
            }
            add(QuizStep.Done)
        }

        // Resume where the user left off if they quit mid-quiz.
        val quizPrefs = getSharedPreferences("scroll_numb_prefs", Context.MODE_PRIVATE)
        val savedStep = quizPrefs.getInt("QUIZ_STEP", 0).coerceIn(0, steps.size - 1)

        setContent {
            ScrollNumbTheme(darkTheme = true) {
                QuizFlow(
                    steps = steps,
                    initialStep = savedStep,
                    onStepChanged = { quizPrefs.edit().putInt("QUIZ_STEP", it).apply() },
                    onAnswer = { qIndex, answer -> saveAnswer(qIndex, answer) },
                    onFinished = {
                        quizPrefs.edit().putInt("QUIZ_STEP", 0).apply()
                        startActivity(Intent(this, GenderSelectionActivity::class.java))
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        finish()
                    }
                )
            }
        }
    }

    private fun saveAnswer(index: Int, answer: String) {
        getSharedPreferences("scroll_numb_prefs", Context.MODE_PRIVATE)
            .edit().putString("QUIZ_Q${index + 1}", answer).apply()
        // Quiz answers no longer tune the limits — "Draw your line" always
        // starts at the fixed defaults (50 reels / 20 min), user-editable there.
    }
}

// ── UI ────────────────────────────────────────────────────────────────────

@Composable
private fun QuizFlow(
    steps: List<QuizStep>,
    initialStep: Int,
    onStepChanged: (Int) -> Unit,
    onAnswer: (Int, String) -> Unit,
    onFinished: () -> Unit
) {
    var stepIndex by remember { mutableIntStateOf(initialStep) }
    val step = steps[stepIndex]
    val progress = (stepIndex + 1).toFloat() / steps.size

    LaunchedEffect(stepIndex) { onStepChanged(stepIndex) }

    // System back + visible arrow both step BACKWARD through the quiz.
    androidx.activity.compose.BackHandler(enabled = stepIndex > 0) { stepIndex-- }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScrollNumbColors.Ink)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .then(
                    if (stepIndex > 0) {
                        Modifier
                            .background(ScrollNumbColors.InkElevated)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { stepIndex-- }
                            )
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (stepIndex > 0) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = ScrollNumbColors.Cream
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // Progress track — thin, saffron fill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(ScrollNumbColors.InkElevated)
        ) {
            val animProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = spring(dampingRatio = 1f, stiffness = 120f),
                label = "quizProgress"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(animProgress)
                    .height(3.dp)
                    .background(ScrollNumbColors.Saffron)
            )
        }

        AnimatedContent(
            targetState = stepIndex,
            transitionSpec = {
                (slideInHorizontally(
                    initialOffsetX = { it / 3 },
                    animationSpec = tween(200)
                ) + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(
                        targetOffsetX = { -it / 3 },
                        animationSpec = tween(160)
                    ) + fadeOut(tween(160)))
            },
            label = "quizStep",
            modifier = Modifier.weight(1f)
        ) { idx ->
            when (val s = steps[idx]) {
                is QuizStep.Ask -> QuestionPage(
                    question = s.q,
                    questionNumber = s.index + 1,
                    totalQuestions = 5,
                    onSelected = { answer ->
                        onAnswer(s.index, answer)
                        stepIndex = idx + 1
                    }
                )
                is QuizStep.Permission -> PermissionPage(
                    step = s.p,
                    permNumber = s.index + 1,
                    permTotal = s.total,
                    onContinue = { stepIndex = idx + 1 }
                )
                QuizStep.Done -> {
                    LaunchedEffect(Unit) {
                        delay(280)
                        onFinished()
                    }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Setting things up…",
                            color = ScrollNumbColors.CreamDim,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fade + gentle rise, [delayMillis] after the step composes. Steps enter via
 * AnimatedContent, so composition == the step becoming visible. Subtle by
 * design — matches the onboarding slides' reveal.
 */
@Composable
private fun Modifier.quizReveal(delayMillis: Int): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 280, delayMillis = delayMillis),
        label = "quizReveal"
    )
    return graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 30f
    }
}

@Composable
private fun QuestionPage(
    question: Question,
    questionNumber: Int,
    totalQuestions: Int,
    onSelected: (String) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var selected by remember { mutableStateOf<String?>(null) }

    // Advance shortly after selection so the fade-out is visible.
    LaunchedEffect(selected) {
        if (selected != null) {
            delay(220)
            onSelected(selected!!)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(48.dp))
        Text(
            text = "QUESTION $questionNumber OF $totalQuestions",
            color = ScrollNumbColors.Saffron.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
            modifier = Modifier.quizReveal(0)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = question.title,
            color = ScrollNumbColors.Cream,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 36.sp,
            modifier = Modifier.quizReveal(60)
        )
        Spacer(Modifier.height(36.dp))

        question.options.forEachIndexed { i, opt ->
            val option = opt.label
            val isSelected = selected == option
            val othersFade = selected != null && !isSelected

            // Staggered entrance per option
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(50L * i)
                visible = true
            }
            val entryAlpha by animateFloatAsState(
                targetValue = if (!visible) 0f else if (othersFade) 0.15f else 1f,
                animationSpec = tween(if (othersFade) 180 else 280),
                label = "optAlpha$i"
            )
            val entryLift by animateFloatAsState(
                targetValue = if (visible) 0f else 24f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 220f),
                label = "optLift$i"
            )
            val borderColor = if (isSelected) ScrollNumbColors.Saffron else ScrollNumbColors.InkLine
            val fillColor = if (isSelected) ScrollNumbColors.SaffronSoft else ScrollNumbColors.InkRaised
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.02f else 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                label = "optScale$i"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .graphicsLayer {
                        alpha = entryAlpha
                        translationY = entryLift
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .background(fillColor)
                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = selected == null
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        selected = option
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Animated visual hook — lets users browse by glance.
                    when {
                        opt.iconRes != null -> OptionAppIcon(opt.iconRes, isSelected)
                        opt.glyph != null -> OptionGlyph(opt.glyph, isSelected)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = option,
                        color = if (isSelected) ScrollNumbColors.Saffron else ScrollNumbColors.Cream,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Text("✓", color = ScrollNumbColors.Saffron, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionPage(
    step: PermissionStep,
    permNumber: Int,
    permTotal: Int,
    onContinue: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var granted by remember { mutableStateOf(step.check(context)) }
    var launched by remember { mutableStateOf(false) }

    // Re-check on resume (user returns from system settings)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                granted = step.check(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Runtime permission dialogs (notifications) resolve in-place; auto-advance.
    LaunchedEffect(granted, launched) {
        if (granted && launched) {
            delay(280)
            onContinue()
        }
    }

    // Left-aligned to match the question pages so alignment stays consistent
    // as the user swipes between questions and permission steps.
    // Scrollable so the longer accessibility disclosure never gets clipped or
    // pushes the Allow button off-screen on short phones.
    val scroll = androidx.compose.foundation.rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(Modifier.height(16.dp))

        // Progress kicker so the flow is legible: "PERMISSION 2 OF 5".
        Text(
            text = "PERMISSION $permNumber OF $permTotal",
            color = ScrollNumbColors.Saffron.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
            modifier = Modifier.quizReveal(0)
        )
        Spacer(Modifier.height(16.dp))

        // Show, don't tell: an animated scene of what this permission unlocks.
        Box(Modifier.quizReveal(50)) {
            PermissionScene(kind = step.visual, granted = granted)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = step.title,
            color = ScrollNumbColors.Cream,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 37.sp,
            modifier = Modifier.quizReveal(100)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = step.benefit,
            color = ScrollNumbColors.CreamDim,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            modifier = Modifier.quizReveal(140)
        )
        Spacer(Modifier.height(14.dp))

        // Plain "what you'll actually tap" hint — only while not yet granted.
        if (!granted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ScrollNumbColors.Saffron.copy(alpha = 0.10f))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .quizReveal(170)
            ) {
                Text("→", color = ScrollNumbColors.Saffron, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = step.hint,
                    color = ScrollNumbColors.Cream,
                    fontSize = 18.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // Privacy reassurance only where the disclosure matters — Accessibility.
        if (step.visual == PermVisual.ACCESSIBILITY) {
            PrivacyPromiseCard(modifier = Modifier.quizReveal(190))
        }

        Spacer(Modifier.height(24.dp))

        if (granted) {
            Box(Modifier.quizReveal(200)) {
                PrimaryButton(text = "Continue", onClick = onContinue)
            }
        } else {
            Box(Modifier.quizReveal(200)) {
                PrimaryButton(
                    text = "Allow",
                    onClick = {
                        launched = true
                        step.launch(context)
                    }
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onContinue
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Maybe later",
                    color = ScrollNumbColors.CreamMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
