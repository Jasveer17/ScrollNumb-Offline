package com.scrollnumb.app.feature.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.scrollnumb.app.R
import com.scrollnumb.app.core.designsystem.components.NumbnessPill
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbCorners
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbType

/**
 * Prominent disclosure required by Google Play's User Data policy: must be
 * shown (with affirmative consent) on EVERY path that sends the user to the
 * system accessibility settings — onboarding AND settings. Explains why the
 * AccessibilityServices API is used and that nothing leaves the device.
 *
 * Presented as a calm, trust-building sheet (soft illustration + three
 * privacy rows) rather than a raw alert — the wording itself is unchanged
 * policy language.
 */
@Composable
fun AccessibilityDisclosureDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ScrollNumbCorners.xl))
                .background(ScrollNumbColors.InkRaised)
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Soft illustration in a sage halo — calm, not clinical.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(ScrollNumbColors.Sage.copy(alpha = 0.14f))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.br_brain_state_0),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "ScrollNumb uses Accessibility",
                style = ScrollNumbType.BrandDisplay,
                fontSize = 20.sp,
                letterSpacing = (-0.5).sp,
                fontWeight = FontWeight.Bold,
                color = ScrollNumbColors.Cream,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "ScrollNumb uses Android's Accessibility Service to detect when you're scrolling short-form video (Reels, Shorts, TikTok and similar) so it can count your scrolls and show the blocking screen when you reach your daily limit.",
                style = MaterialTheme.typography.bodyMedium,
                color = ScrollNumbColors.CreamDim,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(18.dp))

            PrivacyRow(
                icon = Icons.Rounded.Visibility,
                text = "Reads only scroll and screen-change events from those specific apps"
            )
            Spacer(Modifier.height(10.dp))
            PrivacyRow(
                icon = Icons.Rounded.Lock,
                text = "Never collects, stores, or shares the content you view, your messages, or keystrokes"
            )
            Spacer(Modifier.height(10.dp))
            PrivacyRow(
                icon = Icons.Rounded.PhoneAndroid,
                text = "Content processing stays on-device; only tracking health and permission status sync for fair leagues"
            )

            Spacer(Modifier.height(18.dp))

            Text(
                text = "Tap 'I Agree — Open Settings', then turn ScrollNumb ON. We'll bring you right back here.",
                style = MaterialTheme.typography.bodySmall,
                color = ScrollNumbColors.Saffron,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(14.dp))

            NumbnessPill(
                label = "I Agree — Open Settings",
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 2.dp)) {
                Text("Not now", color = ScrollNumbColors.CreamMuted)
            }
        }
    }
}

@Composable
private fun PrivacyRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ScrollNumbCorners.md))
            .background(ScrollNumbColors.InkElevated.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ScrollNumbColors.Sage,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = ScrollNumbColors.CreamDim,
            lineHeight = 18.sp
        )
    }
}
