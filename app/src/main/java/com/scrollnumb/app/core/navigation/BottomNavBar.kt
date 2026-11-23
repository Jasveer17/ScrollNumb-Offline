package com.scrollnumb.app.core.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.platform.LocalLayoutDirection
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbCorners
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbType
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private const val NAV_BAR_DURATION = 260

// Scroll-content bottom clearance for the four tab screens, which draw UNDER
// the floating nav bar (MainActivity skips the Scaffold's bar-height bottom
// padding on tab routes — see isBottomNav in NavGraph.kt — so page
// backgrounds continue to the physical screen bottom): pill (~80dp) + its
// 8dp bottom margin + breathing room, PLUS the live navigation-bar inset.
// The inset MUST be included here: all four tab screens zero their own
// Scaffold contentWindowInsets, so nothing else applies it — a fixed 96dp
// under-cleared by the inset and hid the last scrolled row behind the
// opaque pill on 3-button-nav devices (code-review find, 2026-07-12).
@Composable
fun bottomNavUnderlayPadding(): androidx.compose.ui.unit.Dp =
    96.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

@Composable
fun BottomNavBar(
    navController: NavHostController,
    pagerState: PagerState,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Stats,
        BottomNavItem.Profile
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.route == Screen.Dashboard.route
    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        modifier = modifier,
        visible = showBottomBar,
        enter = fadeIn(animationSpec = tween(NAV_BAR_DURATION, easing = FastOutSlowInEasing)) +
                slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(NAV_BAR_DURATION, easing = FastOutSlowInEasing)
                ),
        exit = fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                slideOutVertically(
                    targetOffsetY = { it / 2 },
                    animationSpec = tween(200, easing = FastOutSlowInEasing)
                )
    ) {
        // Two SIBLING layers, not parent-child: the dock tray must sit BEHIND
        // the glass pill with the pill's upper body rising above it — any
        // clipping parent cuts the pill at the tray's top edge (every flush
        // full-height tray variant capped the corner radius at 52-60dp for
        // exactly that reason, user-rejected). The tray is bottom-anchored
        // and short (only the pill's bottom ~28dp rests on it), so its 999dp
        // top corners clamp to the tray's own height → true semicircular
        // sides: the pill's shape minus the bottom rounding (user-requested).
        Box(modifier = Modifier.fillMaxWidth()) {
            val navBarInset = WindowInsets.navigationBars
                .asPaddingValues().calculateBottomPadding()
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // 28dp pill overlap + the pill's 8dp bottom margin + the
                    // system bar inset (gesture vs 3-button safe).
                    .height(36.dp + navBarInset)
                    .clip(RoundedCornerShape(topStart = 999.dp, topEnd = 999.dp))
                    .background(ScrollNumbColors.Ink)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(18.dp, RoundedCornerShape(ScrollNumbCorners.pill))
                    .clip(RoundedCornerShape(ScrollNumbCorners.pill))
                    .background(
                        // Fully opaque: the pill's upper body now floats OVER
                        // page content (docked-tray layout), so the old
                        // 0.94/0.88 alphas let cards/text bleed through it.
                        Brush.verticalGradient(
                            listOf(
                                ScrollNumbColors.InkElevated,
                                ScrollNumbColors.InkRaised
                            )
                        )
                    )
                    .border(
                        1.dp,
                        ScrollNumbColors.Cream.copy(alpha = 0.13f),
                        RoundedCornerShape(ScrollNumbCorners.pill)
                    )
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            ) {
                val selectedIndex = pagerState.currentPage
                val layoutDirection = LocalLayoutDirection.current
                val liquidBridge = remember { Path() }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .offset(y = 5.dp)
                ) {
                    val progress = (pagerState.currentPage +
                        pagerState.currentPageOffsetFraction).coerceIn(0f, items.lastIndex.toFloat())
                    val visualProgress = if (layoutDirection == LayoutDirection.Ltr) {
                        progress
                    } else {
                        items.lastIndex - progress
                    }
                    val centerX = size.width / items.size * (visualProgress + 0.5f)
                    val centerY = size.height / 2f
                    val stretch = abs(sin(PI * progress)).toFloat()
                    val radius = 20.dp.toPx() * (1f - 0.08f * stretch)
                    val separation = 11.dp.toPx() * stretch
                    val waist = radius * (1f - 0.3f * stretch)
                    liquidBridge.apply {
                        reset()
                        moveTo(centerX - separation, centerY - waist)
                        cubicTo(
                            centerX - separation * 0.35f, centerY - waist,
                            centerX + separation * 0.35f, centerY - waist,
                            centerX + separation, centerY - waist
                        )
                        lineTo(centerX + separation, centerY + waist)
                        cubicTo(
                            centerX + separation * 0.35f, centerY + waist,
                            centerX - separation * 0.35f, centerY + waist,
                            centerX - separation, centerY + waist
                        )
                        close()
                    }
                    drawPath(liquidBridge, ScrollNumbColors.Highlight)
                    drawCircle(
                        color = ScrollNumbColors.Highlight,
                        radius = radius,
                        center = androidx.compose.ui.geometry.Offset(centerX - separation, centerY)
                    )
                    drawCircle(
                        color = ScrollNumbColors.Highlight,
                        radius = radius,
                        center = androidx.compose.ui.geometry.Offset(centerX + separation, centerY)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    items.forEachIndexed { index, item ->
                        AddItem(
                            item = item,
                            selected = selectedIndex == index,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun RowScope.AddItem(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor by animateColorAsState(
        targetValue = if (selected) ScrollNumbColors.OnHighlight else ScrollNumbColors.CreamMuted,
        animationSpec = tween(NAV_BAR_DURATION),
        label = "nav_icon"
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) ScrollNumbColors.Saffron else ScrollNumbColors.CreamMuted,
        animationSpec = tween(NAV_BAR_DURATION),
        label = "nav_label"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(ScrollNumbCorners.md))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = item.iconRes),
                contentDescription = stringResource(item.titleRes),
                colorFilter = ColorFilter.tint(iconColor),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(item.titleRes),
            style = ScrollNumbType.Microcopy,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = labelColor,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
