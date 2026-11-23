package com.scrollnumb.app.feature.quiz

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrollnumb.app.R
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors

/**
 * Premium quiz visuals — real emoji with subtle looping motion (little living
 * GIFs) for instant recognition, plus the real app icons for the "which app"
 * question and the permission scenes. Zero dependencies, all Compose.
 */

// ── Option art ─────────────────────────────────────────────────────────────

/** Which animated glyph an answer choice shows (or a real app icon instead). */
enum class QuizGlyph {
    CLOCK_LOW, CLOCK_MID, CLOCK_HIGH, CLOCK_MAX,   // hours scrolling
    MOON, SUNRISE, BOOK, ALLDAY,                    // when
    TARGET, SLEEP, LOTUS, HOURGLASS,                // what you want back
    FEATHER, BALANCE, SHIELD, FLAME, NOMERCY        // how tough / apps
}

/** One 0..1 looping phase, shared shape used by the animated bits. */
@Composable
private fun loopPhase(durationMs: Int, reverse: Boolean = false, label: String): Float {
    val t = rememberInfiniteTransition(label = label)
    val v by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = if (reverse) FastOutSlowInEasing else LinearEasing),
            repeatMode = if (reverse) RepeatMode.Reverse else RepeatMode.Restart
        ),
        label = "${label}v"
    )
    return v
}

// ── Animated emoji engine ───────────────────────────────────────────────────

/** Subtle looping motions that make an emoji feel alive without being busy. */
enum class EmojiMotion { BOB, PULSE, WIGGLE, SPIN, BREATHE, FLICKER, RISE, FLIP, SWAY, SQUISH }

/** Hold → smooth 180° turn → hold → complete the circle so the loop is seamless. */
private fun flipAngle(p: Float): Float = when {
    p < 0.40f -> 0f
    p < 0.54f -> FastOutSlowInEasing.transform((p - 0.40f) / 0.14f) * 180f
    p < 0.90f -> 180f
    else -> 180f + FastOutSlowInEasing.transform((p - 0.90f) / 0.10f) * 180f
}

/**
 * An emoji rendered as text with a gentle infinite motion. Amplitudes are
 * small and periods slow on purpose — premium, not bouncy. [selected] boosts
 * the motion slightly so the chosen option feels the most alive.
 */
@Composable
fun AnimatedEmoji(
    emoji: String,
    motion: EmojiMotion,
    sizeSp: TextUnit = 25.sp,
    selected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val restart = motion == EmojiMotion.SPIN || motion == EmojiMotion.FLIP
    val duration = when (motion) {
        EmojiMotion.BOB -> 2200
        EmojiMotion.PULSE -> 1900
        EmojiMotion.WIGGLE -> 1600
        EmojiMotion.SPIN -> 9000
        EmojiMotion.BREATHE -> 2600
        EmojiMotion.FLICKER -> 640
        EmojiMotion.RISE -> 2400
        EmojiMotion.FLIP -> 4200
        EmojiMotion.SWAY -> 2800
        EmojiMotion.SQUISH -> 2400
    }
    val t = rememberInfiniteTransition(label = "emoji$motion")
    val p by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = if (restart) LinearEasing else FastOutSlowInEasing),
            repeatMode = if (restart) RepeatMode.Restart else RepeatMode.Reverse
        ),
        label = "emojiP"
    )
    val boost = if (selected) 1.3f else 1f
    Text(
        text = emoji,
        fontSize = sizeSp,
        modifier = modifier.graphicsLayer {
            when (motion) {
                EmojiMotion.BOB -> translationY = (p - 0.5f) * 2f * 2.5f.dp.toPx() * boost
                EmojiMotion.PULSE -> {
                    val s = 1f + 0.08f * p * boost; scaleX = s; scaleY = s
                }
                EmojiMotion.WIGGLE -> rotationZ = (p - 0.5f) * 2f * 7f * boost
                EmojiMotion.SPIN -> rotationZ = p * 360f
                EmojiMotion.BREATHE -> {
                    val s = 1f + 0.055f * p * boost; scaleX = s; scaleY = s
                }
                EmojiMotion.FLICKER -> {
                    val s = 1f + 0.06f * p * boost
                    scaleX = s; scaleY = s
                    translationX = (p - 0.5f) * 2f * 1.2f.dp.toPx()
                }
                EmojiMotion.RISE -> translationY = -p * 3f.dp.toPx() * boost
                EmojiMotion.FLIP -> rotationZ = flipAngle(p)
                EmojiMotion.SWAY -> rotationZ = (p - 0.5f) * 2f * 9f * boost
                EmojiMotion.SQUISH -> {
                    scaleY = 1f - 0.10f * p * boost
                    scaleX = 1f + 0.04f * p * boost
                }
            }
        }
    )
}

/** 48dp animated emoji tile for a quiz option — instant meaning, alive. */
@Composable
fun OptionGlyph(glyph: QuizGlyph, selected: Boolean, modifier: Modifier = Modifier) {
    val tileBg = if (selected) ScrollNumbColors.Saffron.copy(alpha = 0.16f) else ScrollNumbColors.InkElevated
    val (emoji, motion) = when (glyph) {
        QuizGlyph.CLOCK_LOW -> "😌" to EmojiMotion.BREATHE
        QuizGlyph.CLOCK_MID -> "😐" to EmojiMotion.BOB
        QuizGlyph.CLOCK_HIGH -> "😩" to EmojiMotion.WIGGLE
        QuizGlyph.CLOCK_MAX -> "🫠" to EmojiMotion.SQUISH
        QuizGlyph.MOON -> "🌙" to EmojiMotion.BOB
        QuizGlyph.SUNRISE -> "🌅" to EmojiMotion.RISE
        QuizGlyph.BOOK -> "💻" to EmojiMotion.BOB
        QuizGlyph.ALLDAY -> "♾️" to EmojiMotion.PULSE
        QuizGlyph.TARGET -> "🎯" to EmojiMotion.PULSE
        QuizGlyph.SLEEP -> "😴" to EmojiMotion.BOB
        QuizGlyph.LOTUS -> "🧘" to EmojiMotion.BREATHE
        QuizGlyph.HOURGLASS -> "⏳" to EmojiMotion.FLIP
        QuizGlyph.FEATHER -> "🪶" to EmojiMotion.SWAY
        QuizGlyph.BALANCE -> "⚖️" to EmojiMotion.SWAY
        QuizGlyph.SHIELD -> "🛡️" to EmojiMotion.PULSE
        QuizGlyph.FLAME -> "🔥" to EmojiMotion.FLICKER
        QuizGlyph.NOMERCY -> "😈" to EmojiMotion.PULSE
    }
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tileBg),
        contentAlignment = Alignment.Center
    ) {
        AnimatedEmoji(emoji = emoji, motion = motion, sizeSp = 25.sp, selected = selected)
    }
}

/** 48dp tile showing a real app icon (Instagram / YouTube / …). */
@Composable
fun OptionAppIcon(iconRes: Int, selected: Boolean, modifier: Modifier = Modifier) {
    val tileBg = if (selected) ScrollNumbColors.Saffron.copy(alpha = 0.16f) else ScrollNumbColors.InkElevated
    val breathe = loopPhase(2400, reverse = true, label = "iconBreathe")
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tileBg),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .graphicsLayer {
                    // Gentle heartbeat so the tile feels alive like the rest.
                    val s = if (selected) 1f + 0.06f * breathe else 1f
                    scaleX = s; scaleY = s
                }
        )
    }
}

// ── Permission scenes ────────────────────────────────────────────────────────

/**
 * A clear, premium "what this unlocks" scene shown above each permission ask —
 * one big animated emoji hero (plus real app icons where they clarify) and a
 * one-line caption, so users who skip the paragraph still get it at a glance.
 */
@Composable
fun PermissionScene(kind: PermVisual, granted: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(
                    listOf(ScrollNumbColors.InkElevated, ScrollNumbColors.InkRaised)
                )
            )
            .border(1.dp, ScrollNumbColors.InkLine, RoundedCornerShape(26.dp)),
        contentAlignment = Alignment.Center
    ) {
        when (kind) {
            PermVisual.NOTIF -> SceneNotif()
            PermVisual.ACCESSIBILITY -> SceneAccessibility()
            PermVisual.OVERLAY -> SceneOverlay()
            PermVisual.BATTERY -> SceneBattery()
            PermVisual.USAGE -> SceneUsage()
        }
        if (granted) {
            Text(
                text = "✓ Enabled",
                color = ScrollNumbColors.Sage,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ScrollNumbColors.Sage.copy(alpha = 0.16f))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            )
        }
    }
}

/** One-line scene caption, always the same voice and weight. */
@Composable
private fun SceneCaption(text: String) {
    Text(
        text = text,
        color = ScrollNumbColors.CreamDim,
        fontSize = 11.sp,
        letterSpacing = 0.2.sp
    )
}

/** Reusable ScrollNumb counter pill. */
@Composable
private fun CounterPill(count: Int, small: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ScrollNumbColors.Ink)
            .border(1.dp, ScrollNumbColors.Saffron, RoundedCornerShape(20.dp))
            .padding(horizontal = if (small) 8.dp else 11.dp, vertical = if (small) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🧠", fontSize = if (small) 10.sp else 13.sp)
        Spacer(Modifier.width(5.dp))
        Text(
            "$count",
            color = ScrollNumbColors.Saffron,
            fontSize = if (small) 12.sp else 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** A tiny "reel card" — the app's icon inside a phone-shaped card. */
@Composable
private fun ReelCard(iconRes: Int) {
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ScrollNumbColors.Ink)
            .border(1.dp, ScrollNumbColors.InkLine, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
        )
    }
}

@Composable
private fun SceneNotif() {
    // A real notification, not a metaphor: the actual card users will get,
    // dropping in from the top the way Android shows it. Loop: drop → hold.
    val cyc = loopPhase(3800, label = "notifCycle")
    val drop = FastOutSlowInEasing.transform((cyc / 0.22f).coerceIn(0f, 1f))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = drop
                    translationY = (1f - drop) * -46f
                }
                .clip(RoundedCornerShape(16.dp))
                .background(ScrollNumbColors.Ink)
                .border(1.dp, ScrollNumbColors.Saffron.copy(alpha = 0.38f), RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Image(
                painter = painterResource(R.mipmap.scrollnumbdark),
                contentDescription = null,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "ScrollNumb",
                        color = ScrollNumbColors.Cream,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("· now", color = ScrollNumbColors.CreamMuted, fontSize = 10.sp)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "Only 5 reels left today",
                    color = ScrollNumbColors.Saffron,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "45 of your 50-reel limit used",
                    color = ScrollNumbColors.CreamDim,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            AnimatedEmoji(emoji = "🔔", motion = EmojiMotion.WIGGLE, sizeSp = 18.sp)
        }
        Spacer(Modifier.height(14.dp))
        SceneCaption("One clear warning before you reach your limit")
    }
}

@Composable
private fun SceneAccessibility() {
    // A tiny Instagram Reels page with the ScrollNumb pill counting on top —
    // the count ticking 11 → 12 → 13 IS the story, so it pops each step.
    val tick = loopPhase(3600, label = "accTick")
    val count = 11 + (tick * 2.999f).toInt()
    val frac = (tick * 3f) % 1f
    val pop = 1f + 0.18f * (1f - (frac * 3f).coerceAtMost(1f))
    Row(verticalAlignment = Alignment.CenterVertically) {
        // ── Phone-shaped IG reel mock ──
        Box(
            modifier = Modifier
                .size(width = 78.dp, height = 132.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ScrollNumbColors.Ink)
                .border(1.dp, ScrollNumbColors.InkLine, RoundedCornerShape(14.dp))
        ) {
            // Top row: IG logo + username bar + "•••"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.app_icon_instagram),
                    contentDescription = null,
                    modifier = Modifier
                        .size(11.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier
                        .width(24.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ScrollNumbColors.Cream.copy(alpha = 0.35f))
                )
                Spacer(Modifier.weight(1f))
                Text("•••", color = ScrollNumbColors.CreamDim, fontSize = 6.sp)
            }
            // Right-hand action stack: like / comment / share, small + muted
            Column(
                verticalArrangement = Arrangement.spacedBy(7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 5.dp)
                    .graphicsLayer { alpha = 0.55f }
            ) {
                Text("♡", color = ScrollNumbColors.Cream, fontSize = 9.sp)
                Text("💬", fontSize = 8.sp)
                Text("➤", color = ScrollNumbColors.Cream, fontSize = 8.sp)
            }
            // Bottom caption bars — two short muted lines
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 9.dp)
            ) {
                Box(
                    Modifier
                        .width(42.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ScrollNumbColors.Cream.copy(alpha = 0.25f))
                )
                Box(
                    Modifier
                        .width(28.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ScrollNumbColors.Cream.copy(alpha = 0.16f))
                )
            }
            // Thin reel progress bar scrubbing along the bottom edge
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(frac.coerceIn(0.04f, 1f))
                    .height(2.dp)
                    .background(ScrollNumbColors.Cream.copy(alpha = 0.45f))
            )
            // ScrollNumb counter pill floating near the top of the reel
            CounterPill(
                count = count,
                small = true,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 22.dp)
                    .graphicsLayer { scaleX = pop; scaleY = pop }
            )
        }
        Spacer(Modifier.width(16.dp))
        SceneCaption("Counts every reel as it changes")
    }
}

@Composable
private fun SceneOverlay() {
    val bob = loopPhase(1800, reverse = true, label = "ovBob")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.TopCenter) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                ReelCard(R.drawable.app_icon_instagram)
                ReelCard(R.drawable.app_icon_youtube)
            }
            // The ScrollNumb pill floating ON TOP of the reel apps.
            CounterPill(
                count = 7,
                small = true,
                modifier = Modifier.graphicsLayer { translationY = (bob - 0.5f) * 8f.dp.toPx() }
            )
        }
        Spacer(Modifier.height(12.dp))
        SceneCaption("Floats on top of your reel apps")
    }
}

@Composable
private fun SceneBattery() {
    val drift = loopPhase(2600, label = "batDrift")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            AnimatedEmoji(emoji = "🔋", motion = EmojiMotion.PULSE, sizeSp = 46.sp)
            // Sleepy Zs drifting up — keeps counting even while the phone naps.
            Text(
                text = "💤",
                fontSize = 17.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 16.dp)
                    .graphicsLayer {
                        translationY = -drift * 14f.dp.toPx()
                        alpha = 1f - drift * 0.85f
                    }
            )
        }
        Spacer(Modifier.height(12.dp))
        SceneCaption("Keeps counting in the background")
    }
}

@Composable
private fun SceneUsage() {
    val grow = loopPhase(2000, reverse = true, label = "usageGrow")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedEmoji(emoji = "📊", motion = EmojiMotion.BREATHE, sizeSp = 30.sp)
            Spacer(Modifier.width(16.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(
                    Triple(R.drawable.app_icon_instagram, 44, ScrollNumbColors.Saffron),
                    Triple(R.drawable.app_icon_youtube, 30, ScrollNumbColors.Coral),
                    Triple(R.drawable.app_icon_snapchat, 20, ScrollNumbColors.Sage)
                ).forEachIndexed { i, (icon, barH, color) ->
                    val f = (0.55f + 0.45f * grow) * if (i == 0) 1f else 0.85f
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .width(16.dp)
                                .height((barH * f).dp)
                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                .background(color.copy(alpha = 0.85f))
                        )
                        Spacer(Modifier.height(5.dp))
                        Image(
                            painter = painterResource(icon),
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(5.dp))
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SceneCaption("Knows which reel app is open")
    }
}
