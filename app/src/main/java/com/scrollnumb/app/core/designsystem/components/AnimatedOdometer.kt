package com.scrollnumb.app.core.designsystem.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.pow

/**
 * Mechanical odometer counter with a CONTINUOUS roll. The whole number is a
 * single float animation and every digit column is positioned from it each
 * frame, so rapid consecutive counts retarget the roll mid-flight instead of
 * restarting a discrete enter/exit transition — the old per-digit
 * AnimatedContent read as a stutter under fast scrolling. Tabular figures
 * ("tnum") keep each column a constant width, killing horizontal jitter.
 */
@Composable
fun AnimatedOdometer(
    count: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current
) {
    // Roll only UPWARD. On a decrement (Undo) the digit-column count shrinks to
    // the target immediately while the wheels still hold the old value — the
    // leading wheel vanished mid-roll. Snapping decrements avoids the artifact
    // (undo is rare; the roll is for counting up).
    var previousCount by remember { mutableStateOf(count) }
    val animated by animateFloatAsState(
        targetValue = count.toFloat(),
        animationSpec = if (count >= previousCount) {
            tween(durationMillis = 260, easing = FastOutSlowInEasing)
        } else {
            snap()
        },
        label = "odometerValue"
    )
    LaunchedEffect(count) { previousCount = count }
    // Each digit renders as its OWN Text, so per-style tracking must be
    // neutralised: the hero style's -4sp letterSpacing (and tabular "tnum"
    // cells) added visible gaps/unevenness BETWEEN the digit columns.
    val digitStyle = style.copy(letterSpacing = 0.sp)
    val digits = max(1, count.toString().length)
    val value = max(0f, animated)
    Row(modifier = modifier) {
        for (place in digits - 1 downTo 0) {
            OdometerDigit(place = place, value = value, style = digitStyle)
        }
    }
}

/**
 * One rolling digit column: the current digit plus its successor stacked a
 * full line apart, translated by the fractional part of the animated value —
 * a real odometer wheel. Works in both directions (count decrement rolls back
 * down, e.g. "recover focus").
 */
@Composable
private fun OdometerDigit(place: Int, value: Float, style: TextStyle) {
    // A wheel only rolls while every lower wheel is wrapping (…9→…0): the
    // naive (value / 10^place) % 10 kept upper wheels parked BETWEEN digits
    // at rest (count 13 rendered a tens wheel 30% into its roll).
    val power = 10f.pow(place)
    val digit = ((value / power).toInt()) % 10
    val lower = value % power
    val fraction = (lower - (power - 1f)).coerceIn(0f, 1f)
    // Custom layout: the column is as wide as the CURRENT digit (animating to
    // the incoming digit's width mid-roll). A plain Box sized to the WIDER of
    // the two stacked digits — the hidden successor under a "1" padded it out,
    // so "148" read as "1 48".
    androidx.compose.ui.layout.Layout(
        content = {
            Text(text = digit.toString(), style = style, softWrap = false)
            Text(text = ((digit + 1) % 10).toString(), style = style, softWrap = false)
        },
        modifier = Modifier.clipToBounds()
    ) { measurables, constraints ->
        val current = measurables[0].measure(constraints)
        val next = measurables[1].measure(constraints)
        val width = (current.width + (next.width - current.width) * fraction).toInt()
        val height = maxOf(current.height, next.height)
        layout(width, height) {
            current.placeRelative(0, (-fraction * height).toInt())
            next.placeRelative(0, ((1f - fraction) * height).toInt())
        }
    }
}
