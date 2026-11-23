package com.scrollnumb.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbCorners
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbType

// Brain-rot state is now rendered via AvatarHelper avatars on the dashboard;
// this enum only drives the state LABEL + accent color. The old per-state
// br_brain_state_* illustrations (and BrainStateIllustration) were dead.
enum class BrainStateArt {
    FRESH,
    STRAIN,
    DRAINED,
    TIRED,
    ROTTEN;

    companion object {
        fun fromRatio(ratio: Float): BrainStateArt = when {
            ratio < 0.2f -> FRESH
            ratio < 0.5f -> STRAIN
            ratio < 0.8f -> DRAINED
            ratio < 0.95f -> TIRED
            else -> ROTTEN
        }
    }
}

@Composable
fun NumberStepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 5,
    range: IntRange = 5..100,
    suffix: String = ""
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(ScrollNumbCorners.lg))
            .background(ScrollNumbColors.InkRaised)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = ScrollNumbType.Microcopy,
                color = ScrollNumbColors.CreamMuted
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$value$suffix",
                style = ScrollNumbType.NumeralMedium,
                color = ScrollNumbColors.Cream
            )
        }

        StepperButton(symbol = "−", enabled = value > range.first) {
            onValueChange((value - step).coerceAtLeast(range.first))
        }
        Spacer(modifier = Modifier.width(12.dp))
        StepperButton(symbol = "+", enabled = value < range.last) {
            onValueChange((value + step).coerceAtMost(range.last))
        }
    }
}

@Composable
private fun StepperButton(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (enabled) ScrollNumbColors.Saffron
                else ScrollNumbColors.InkWhisper
            )
            .clickable(enabled = enabled, onClick = onClick)
            .wrapContentSize(Alignment.Center)
    ) {
        Text(
            text = symbol,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) ScrollNumbColors.Ink else ScrollNumbColors.CreamMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun BenefitPill(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(ScrollNumbCorners.pill))
            .background(ScrollNumbColors.Saffron.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(ScrollNumbColors.Saffron)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = ScrollNumbType.Microcopy,
            color = ScrollNumbColors.Saffron
        )
    }
}
