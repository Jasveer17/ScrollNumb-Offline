package com.scrollnumb.app.service

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.scrollnumb.app.core.designsystem.components.avatarPainter as painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrollnumb.app.core.designsystem.theme.CabinetGrotesk
import com.scrollnumb.app.core.util.AvatarHelper
import kotlinx.coroutines.delay

// Fixed brand colors consistent with the Shield theme overlay
private val CardBg = Color(0xFF14110C)
private val CardBgRaised = Color(0xFF1C1814)
private val CardBorderColor = Color(0x33FFD642)
private val SaffronGold = Color(0xFFFFD642)
private val CreamLight = Color(0xFFF5F0E8)
private val CreamDim = Color(0xFFB8B0A2)

@Composable
fun AwarenessNudgeCard(
    reelCount: Int = 15,
    onDismiss: () -> Unit,
    onTakeBreak: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Shield avatar: male avatar for male users, female avatar for female users
    val avatarRes = remember {
        AvatarHelper.getShieldAvatar(context)
    }

    // Safety auto-dismiss after 30 seconds of inactivity
    LaunchedEffect(Unit) {
        delay(30_000L)
        onDismiss()
    }

    // Subtle scale-in animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val cardScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
        label = "shieldModalScale"
    )

    Box(
        modifier = modifier
            .widthIn(max = 340.dp)
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .shadow(elevation = 24.dp, shape = RoundedCornerShape(26.dp), spotColor = Color(0x99000000))
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(
                    listOf(CardBg, CardBgRaised)
                )
            )
            .border(1.2.dp, CardBorderColor, RoundedCornerShape(26.dp))
            .padding(horizontal = 22.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Close 'X' Button in top right
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopEnd
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        tint = CreamDim,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // ── BIG THING 1: The Avatar ──
            Image(
                painter = painterResource(id = avatarRes),
                contentDescription = "Mindfulness Avatar",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(125.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Microcopy Chip: REELS LOCKED / AWARENESS CHECK
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(SaffronGold.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(SaffronGold)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = "AWARENESS CHECK",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = SaffronGold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── BIG THING 2: The Quote (Where "Take the nap." lies) ──
            Text(
                text = "Time for a breather?",
                fontFamily = CabinetGrotesk,
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
                lineHeight = 32.sp,
                letterSpacing = (-0.8).sp,
                color = CreamLight,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle: Valuable things missing reminder
            Text(
                text = "You’ve scrolled through $reelCount reels. Take a moment to choose what feels right.",
                fontSize = 13.5.sp,
                color = CreamDim,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(22.dp))

            listOf(
                Triple(Icons.Rounded.Pause, "Take a break", onTakeBreak),
                Triple(Icons.Rounded.PlayArrow, "Keep scrolling", onDismiss)
            ).forEachIndexed { index, (icon, label, action) ->
                androidx.compose.material3.Button(
                    onClick = action,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (index == 0) CreamLight else CardBgRaised,
                        contentColor = if (index == 0) CardBg else CreamLight
                    )
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                if (index == 0) Spacer(Modifier.height(8.dp))
            }
        }
    }
}
