package com.scrollnumb.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.scrollnumb.app.MainActivity
import com.scrollnumb.app.core.designsystem.components.BrainStateArt
import com.scrollnumb.app.core.util.AvatarHelper

class ScrollNumbWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val totalScrolls = prefs[intPreferencesKey("total_scrolls")] ?: 0
            val dailyLimit = (prefs[intPreferencesKey("daily_limit")] ?: 50).coerceAtLeast(1)

            val ratio = (totalScrolls.toFloat() / dailyLimit).coerceIn(0f, 1.6f)
            val state = BrainStateArt.fromRatio(ratio)
            val statusLabel = when (state) {
                BrainStateArt.FRESH -> "Fresh"
                BrainStateArt.STRAIN -> "Strain"
                BrainStateArt.DRAINED -> "Drained"
                BrainStateArt.TIRED -> "Tired"
                BrainStateArt.ROTTEN -> "Rotten"
            }
            val accent = when (state) {
                BrainStateArt.FRESH, BrainStateArt.STRAIN -> Color(0xFFFFD642)
                BrainStateArt.DRAINED -> Color(0xFFFF8A65)
                BrainStateArt.TIRED, BrainStateArt.ROTTEN -> Color(0xFFFF6B4A)
            }
            val avatarRes = AvatarHelper.getScrollStatusAvatar(context, ratio)
            val cream = Color(0xFFF5F0E8)
            val creamDim = Color(0xB3F5F0E8)

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF181410))
                    .cornerRadius(24.dp)
                    .clickable(
                        actionStartActivity(
                            Intent(context, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    )
                    .padding(12.dp)
            ) {
                // Nudge the status label down from the very top edge.
                Spacer(modifier = GlanceModifier.height(6.dp))
                // Brain-state label centered across the top — full widget width,
                // so a long word like "ROTTEN" can never collide with the count.
                Text(
                    text = statusLabel.uppercase(),
                    maxLines = 1,
                    modifier = GlanceModifier.fillMaxWidth(),
                    style = TextStyle(
                        color = ColorProvider(accent),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                )
                // Middle row grows to fill the space above the bottom bar: avatar
                // on the left, count STACKED to its right ("48" over "of 50").
                // Stacking avoids the horizontal squeeze that clipped "/ 50" to
                // "/ ..." on this narrow 2x2 widget, so the avatar stays full size.
                Row(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar with a soft status-tinted ring behind it.
                    Box(
                        modifier = GlanceModifier
                            .size(66.dp)
                            .background(accent)
                            .cornerRadius(33.dp)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(avatarRes),
                            contentDescription = statusLabel,
                            modifier = GlanceModifier
                                .size(62.dp)
                                .background(Color(0xFF181410))
                                .cornerRadius(31.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(12.dp))
                    // Count centered in the space right of the avatar, with the
                    // two lines sharing a center axis so "48" and "of 50" align.
                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = totalScrolls.toString(),
                            maxLines = 1,
                            style = TextStyle(
                                color = ColorProvider(cream),
                                fontSize = if (totalScrolls >= 100) 28.sp else 34.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        )
                        Text(
                            text = "of $dailyLimit",
                            maxLines = 1,
                            style = TextStyle(
                                color = ColorProvider(creamDim),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }
                // Full-width fill bar near the bottom, tinted by brain state.
                LinearProgressIndicator(
                    progress = ratio.coerceIn(0f, 1f),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .cornerRadius(3.dp),
                    color = ColorProvider(accent),
                    backgroundColor = ColorProvider(Color(0x33F5F0E8))
                )
                // Lift the bar up off the very bottom edge.
                Spacer(modifier = GlanceModifier.height(8.dp))
            }
        }
    }
}
