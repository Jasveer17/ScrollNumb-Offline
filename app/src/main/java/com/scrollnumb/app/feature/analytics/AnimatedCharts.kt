package com.scrollnumb.app.feature.analytics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import kotlinx.coroutines.delay

/** Pill-style period selector: Today / Week / Month / Year / All time. */
@Composable
fun PeriodTabs(
    selected: StatsPeriod,
    onSelect: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatsPeriod.entries.forEach { period ->
            val isSelected = period == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (isSelected) ScrollNumbColors.Saffron
                        else ScrollNumbColors.InkElevated
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(period)
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = period.label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) ScrollNumbColors.Ink else ScrollNumbColors.CreamDim
                )
            }
        }
    }
}

/**
 * Vertical bar chart whose bars grow with a staggered spring on appear.
 * [yMax] sets a strict Y-axis ceiling — bars are clamped to it so a spike
 * (e.g. today's count blowing past the daily cap) can never overshoot the
 * container; the data max is used when it exceeds the supplied ceiling.
 */
@Composable
fun AnimatedBarChart(
    bars: List<ChartBar>,
    modifier: Modifier = Modifier,
    height: Int = 160,
    yMax: Int = 0
) {
    val dataMax = bars.maxOfOrNull { it.value } ?: 0
    val maxValue = maxOf(dataMax, yMax, 1)

    // Three FIXED-height zones (value · bar · label) so a tall bar can never
    // push its column past the chart and overflow the card. Total column
    // height is deterministic and identical for every bar.
    val valueZone = 18
    val labelZone = 18
    val gap = 6

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val chartWidth = maxOf(maxWidth, (bars.size * 44).dp)
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .width(chartWidth)
                .height((valueZone + height + gap + labelZone).dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
        bars.forEachIndexed { index, bar ->
            val grow = remember(bars) { Animatable(0f) }
            LaunchedEffect(bars) {
                delay(60L * index)
                grow.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 220f))
            }
            val fraction = (bar.value.toFloat() / maxValue).coerceIn(0f, 1f)

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // value label (fixed zone, bottom-aligned)
                Box(
                    modifier = Modifier.height(valueZone.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (bar.value > 0) {
                        Text(
                            text = bar.value.toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ScrollNumbColors.CreamDim,
                            modifier = Modifier.graphicsLayer { alpha = grow.value }
                        )
                    }
                }
                // bar zone (fixed height; bar grows from the bottom, clamped)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((height * fraction * grow.value).coerceIn(3f, height.toFloat()).dp)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(ScrollNumbColors.Saffron, ScrollNumbColors.SaffronDim)
                                )
                            )
                    )
                }
                Spacer(Modifier.height(gap.dp))
                Text(
                    text = bar.label,
                    fontSize = 11.sp,
                    color = ScrollNumbColors.CreamMuted,
                    maxLines = 1
                )
            }
        }
        }
    }
}

/** Horizontal per-app share bars that fill with a spring on appear. */
@Composable
fun AppBreakdownBars(
    apps: List<AppBreakdown>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        apps.forEachIndexed { index, app ->
            val fill = remember(apps) { Animatable(0f) }
            LaunchedEffect(apps) {
                delay(80L * index)
                fill.animateTo(app.fraction, spring(dampingRatio = 0.8f, stiffness = 160f))
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ScrollNumbColors.Cream,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${app.count}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ScrollNumbColors.Saffron
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(ScrollNumbColors.InkElevated)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fill.value.coerceIn(0.02f, 1f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(ScrollNumbColors.Saffron, ScrollNumbColors.Coral)
                                )
                            )
                    )
                }
            }
        }
    }
}
