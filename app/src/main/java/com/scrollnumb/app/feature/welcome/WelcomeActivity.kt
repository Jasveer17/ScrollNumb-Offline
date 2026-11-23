package com.scrollnumb.app.feature.welcome

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrollnumb.app.MainActivity
import com.scrollnumb.app.core.designsystem.components.HolographicText
import com.scrollnumb.app.core.designsystem.components.PrimaryButton
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbTheme
import com.scrollnumb.app.feature.quiz.QuizActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScrollNumbTheme(darkTheme = true) {
                WelcomeScreen(
                    onGetStarted = {
                        startActivity(Intent(this, QuizActivity::class.java))
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun WelcomeScreen(
    onGetStarted: () -> Unit
) {
    val screenH = LocalConfiguration.current.screenHeightDp.toFloat()

    // Two soft aurora blobs drifting slowly behind the content.
    val transition = rememberInfiniteTransition(label = "aurora")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auroraDrift"
    )
    val feedScroll by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "feedScroll"
    )

    // Staggered entrance
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val heroAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(700), label = "heroAlpha"
    )
    val heroLift by animateFloatAsState(
        targetValue = if (entered) 0f else 40f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 120f), label = "heroLift"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScrollNumbColors.Ink)
    ) {
        // Aurora glows — radial gradients (soft on every device, no RenderEffect)
        Box(
            modifier = Modifier
                .size(420.dp)
                .graphicsLayer {
                    // dp-based offsets: raw px placed these glows at wildly
                    // different spots per screen density (device-portability
                    // invariant). Values = old px ÷ 2.625 reference density.
                    translationX = (-69f + drift * 91f).dp.toPx()
                    translationY = (190f + drift * 99f).dp.toPx()
                }
                .background(
                    Brush.radialGradient(
                        listOf(
                            ScrollNumbColors.Saffron.copy(alpha = 0.20f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(380.dp)
                .align(Alignment.TopEnd)
                .graphicsLayer {
                    translationX = (76f - drift * 107f).dp.toPx()
                    translationY = (495f - drift * 114f).dp.toPx()
                }
                .background(
                    Brush.radialGradient(
                        listOf(
                            ScrollNumbColors.Coral.copy(alpha = 0.14f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1.1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = heroAlpha
                    translationY = heroLift
                }
            ) {
                ScrollingFeedAnimation(progress = feedScroll)
                Spacer(Modifier.height(22.dp))
                Text(
                    text = "EVERY SCROLL COUNTS",
                    color = ScrollNumbColors.CreamMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.2.sp
                )
                Spacer(Modifier.height(18.dp))
                // 52sp × brand word ≈ 280dp — clipped on 320dp phones and at
                // large system font scales; scale it like the dashboard hero.
                val brandScale =
                    com.scrollnumb.app.core.designsystem.components.rememberDeviceTextScale()
                // FitWidth backstop: whatever the width/font, the brand word
                // scales to fit instead of clipping ("half showing" bug class).
                com.scrollnumb.app.core.designsystem.components.FitWidth {
                    HolographicText(
                        text = "ScrollNumb",
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 52.sp * brandScale,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-1).sp
                        )
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "See how much you really scroll.\nThen take your brain back.",
                    color = ScrollNumbColors.CreamDim,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "LIVE COUNT  •  ON-DEVICE PRIVACY  •  SMART LIMITS",
                    color = ScrollNumbColors.Saffron,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(ScrollNumbColors.Saffron.copy(alpha = 0.09f))
                        .border(1.dp, ScrollNumbColors.Saffron.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = heroAlpha },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PrimaryButton(
                    text = "Get Started",
                    onClick = onGetStarted
                )
                Spacer(Modifier.height(36.dp))
            }
        }
    }
}

@Composable
private fun ScrollingFeedAnimation(progress: Float) {
    Box(
        modifier = Modifier
            .size(width = 136.dp, height = 142.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(ScrollNumbColors.InkRaised.copy(alpha = 0.82f))
            .border(1.dp, ScrollNumbColors.Saffron.copy(alpha = 0.28f), RoundedCornerShape(28.dp))
    ) {
        repeat(3) { index ->
            val y = 8f + index * 88f - progress * 88f
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = y.dp)
                    .width(108.dp)
                    .height(76.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                ScrollNumbColors.Saffron.copy(alpha = 0.15f),
                                ScrollNumbColors.InkElevated
                            )
                        )
                    )
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(ScrollNumbColors.Coral.copy(alpha = 0.8f))
                    )
                    Spacer(Modifier.width(7.dp))
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(ScrollNumbColors.Cream.copy(alpha = 0.5f))
                    )
                    Spacer(Modifier.weight(1f))
                    Text("•••", color = ScrollNumbColors.CreamMuted, fontSize = 8.sp)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(ScrollNumbColors.Cream.copy(alpha = 0.28f))
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ScrollNumbColors.Cream.copy(alpha = 0.16f))
                )
            }
        }

        Text(
            text = "SWIPE UP",
            color = ScrollNumbColors.Saffron,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ScrollNumbColors.Ink.copy(alpha = 0.9f))
                .padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}
