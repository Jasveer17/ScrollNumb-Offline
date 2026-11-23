package com.scrollnumb.app.feature.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.core.util.SoundFx
import com.scrollnumb.app.core.util.XpFormula
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ── Menu ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrassMenuSheet(
    onDismiss: () -> Unit,
    onBreathing: () -> Unit,
    onDarkRoom: () -> Unit,
    onMoreExercises: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ScrollNumbColors.InkRaised
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Text(
                text = "Touch some grass 🌿",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = ScrollNumbColors.Cream
            )
            Text(
                text = "A short break away from the feed. Your brain will thank you.",
                fontSize = 14.sp,
                color = ScrollNumbColors.CreamDim,
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
            )
            GrassOption(
                emoji = "🫁",
                title = "Guided breathing",
                subtitle = "One calm minute. In, hold, out.",
                meta = "1 min",
                accent = ScrollNumbColors.Sage,
                onClick = onBreathing
            )
            Spacer(Modifier.height(12.dp))
            GrassOption(
                emoji = "🌙",
                title = "Dark room",
                subtitle = "Pure black. Rest your eyes.",
                meta = "30s",
                accent = DarkRoomAccent,
                onClick = onDarkRoom
            )
            Spacer(Modifier.height(12.dp))
            GrassOption(
                emoji = "🧩",
                title = "Math focus",
                subtitle = "Quick puzzles to reset your attention.",
                meta = "+XP",
                accent = ScrollNumbColors.Saffron,
                onClick = onMoreExercises
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

// Cool indigo for the dark-room card — a fixed brand tint (the sheet's colors
// are theme-agnostic on the elevated surface, like the other accents here).
private val DarkRoomAccent = Color(0xFF7C86E8)

@Composable
private fun GrassOption(
    emoji: String,
    title: String,
    subtitle: String,
    meta: String,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ScrollNumbColors.InkElevated)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tinted rounded icon tile — gives each activity a distinct, cheerful
        // color chip instead of a bare floating emoji.
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 24.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = ScrollNumbColors.Cream,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(subtitle, fontSize = 13.sp, color = ScrollNumbColors.CreamMuted, maxLines = 2)
        }
        Spacer(Modifier.width(10.dp))
        // Meta pill (duration / reward) in the card's accent color.
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(accent.copy(alpha = 0.16f))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                meta,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}

// ── Activities ────────────────────────────────────────────────────────────

private const val DARK_TOTAL_SEC = 30

@Composable
fun DarkRoomDialog(
    awardXp: suspend () -> Int,
    onComplete: () -> Unit,
    onDismiss: () -> Unit
) {
    FullScreenActivity(onDismiss = onDismiss, background = Color.Black) {
        var secondsLeft by remember { mutableIntStateOf(DARK_TOTAL_SEC) }
        // Null until the rest finishes; then holds the XP actually banked
        // (0 if the daily decompression cap is already reached).
        var gained by remember { mutableStateOf<Int?>(null) }

        LaunchedEffect(Unit) {
            while (secondsLeft > 0) {
                delay(1000)
                secondsLeft--
            }
            SoundFx.playChime()
            gained = awardXp()
        }

        val g = gained
        if (g != null) {
            VictoryBurst(
                glyphs = listOf("🍃", "🌙", "✨", "🌿", "⭐"),
                message = "Eyes: rested",
                xp = g,
                onFinished = onComplete
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = "Close your eyes.",
                    fontSize = 17.sp,
                    color = Color(0x66F5F0E8)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "${secondsLeft}",
                    fontSize = 13.sp,
                    color = Color(0x33F5F0E8)
                )
            }
        }
    }
}

@Composable
private fun FullScreenActivity(
    onDismiss: () -> Unit,
    background: Color = ScrollNumbColors.Ink,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
        ) {
            content()
            Text(
                text = "✕",
                fontSize = 20.sp,
                color = ScrollNumbColors.CreamMuted,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 8.dp, end = 16.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
                    .padding(8.dp)
            )
        }
    }
}

// ── Victory burst (reusable: math symbols / nature motifs) ───────────────

private data class BurstParticle(
    val angle: Float,
    val speed: Float,
    val glyph: String,
    val size: Float,
    val spin: Float
)

@Composable
fun VictoryBurst(
    glyphs: List<String>,
    message: String,
    xp: Int? = null,
    onFinished: () -> Unit
) {
    val particles = remember {
        val rng = Random(System.nanoTime())
        List(18) {
            BurstParticle(
                angle = rng.nextFloat() * 6.283f,
                speed = 220f + rng.nextFloat() * 320f,
                glyph = glyphs[it % glyphs.size],
                size = 18f + rng.nextFloat() * 14f,
                spin = (rng.nextFloat() - 0.5f) * 540f
            )
        }
    }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(1400, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        delay(400)
        onFinished()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        particles.forEach { p ->
            val dist = p.speed * t.value
            Text(
                text = p.glyph,
                fontSize = p.size.sp,
                modifier = Modifier.graphicsLayer {
                    translationX = cos(p.angle) * dist
                    translationY = sin(p.angle) * dist - 60f * t.value
                    alpha = (1f - t.value).coerceIn(0f, 1f)
                    rotationZ = p.spin * t.value
                }
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    alpha = t.value
                    scaleX = 0.8f + 0.2f * t.value
                    scaleY = 0.8f + 0.2f * t.value
                }
                .clip(RoundedCornerShape(28.dp))
                .background(ScrollNumbColors.InkRaised.copy(alpha = 0.94f))
                .padding(horizontal = 28.dp, vertical = 20.dp)
        ) {
            Text(
                text = "✓",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = ScrollNumbColors.Sage
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = ScrollNumbColors.Cream
            )
            if (xp != null) {
                Text(
                    text = if (xp > 0) "+$xp XP" else "Daily cap reached",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ScrollNumbColors.Saffron,
                    modifier = Modifier.graphicsLayer { alpha = t.value }
                )
            }
        }
    }
}
