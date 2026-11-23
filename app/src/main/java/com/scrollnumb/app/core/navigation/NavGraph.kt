package com.scrollnumb.app.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.scrollnumb.app.core.datastore.UserPreferences
import com.scrollnumb.app.feature.analytics.AnalyticsScreen
import com.scrollnumb.app.feature.dashboard.DashboardScreen
import com.scrollnumb.app.feature.dashboard.ShieldScreen
import com.scrollnumb.app.feature.onboarding.OnboardingScreen
import com.scrollnumb.app.feature.profile.EditProfileScreen
import com.scrollnumb.app.feature.profile.ProfileScreen
import com.scrollnumb.app.feature.relaxation.RelaxationScreen
import com.scrollnumb.app.feature.settings.LegalScreen
import com.scrollnumb.app.feature.settings.PrivacyPolicyScreen
import com.scrollnumb.app.feature.settings.SettingsScreen
import com.scrollnumb.app.feature.settings.TermsOfServiceScreen
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val NAV_DURATION = 320

// Index of OnboardingScreen's PAGE_PERMS. A returning user signing in on a fresh
// device already saw the intro tour on their old device, so we open the onboarding
// pager straight on this permissions page instead of replaying the whole
// "continue ×5" walkthrough. Keep in sync with OnboardingScreen's page constants.
private const val ONBOARDING_PERMISSIONS_PAGE = 6

private val bottomNavRoutes = setOf(
    Screen.Dashboard.route
)

// Non-private: MainActivity also needs it to skip the Scaffold's bottom
// padding on tab routes (their screens draw under the floating nav tray).
fun isBottomNav(route: String?) = route != null && route in bottomNavRoutes

private fun bottomNavEnter(): EnterTransition =
    fadeIn(animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing))

private fun bottomNavExit(): ExitTransition =
    fadeOut(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing))

private fun horizontalForward(): EnterTransition =
    slideInHorizontally(
        initialOffsetX = { it / 6 },
        animationSpec = tween(durationMillis = NAV_DURATION, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(durationMillis = NAV_DURATION, easing = FastOutSlowInEasing))

private fun horizontalForwardExit(): ExitTransition =
    slideOutHorizontally(
        targetOffsetX = { -it / 8 },
        animationSpec = tween(durationMillis = NAV_DURATION, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(durationMillis = NAV_DURATION, easing = FastOutSlowInEasing))

private fun horizontalBack(): EnterTransition =
    slideInHorizontally(
        initialOffsetX = { -it / 8 },
        animationSpec = tween(durationMillis = NAV_DURATION, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(durationMillis = NAV_DURATION, easing = FastOutSlowInEasing))

private fun horizontalBackExit(): ExitTransition =
    slideOutHorizontally(
        targetOffsetX = { it / 6 },
        animationSpec = tween(durationMillis = NAV_DURATION, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(durationMillis = NAV_DURATION, easing = FastOutSlowInEasing))

@Composable
fun ScrollNumbNavGraph(
    navController: NavHostController,
    startDestination: String,
    userPreferences: UserPreferences,
    mainPagerState: PagerState,
    modifier: Modifier = Modifier
) {
    // Log a screen_view event whenever the visible destination changes.
    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry?.destination?.route) {
        currentEntry?.destination?.route?.takeUnless { it == Screen.Dashboard.route }?.let {
        }
    }
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier.fillMaxSize(),
        enterTransition = {
            val targetIsBottom = isBottomNav(targetState?.destination?.route)
            val initialIsBottom = isBottomNav(initialState?.destination?.route)
            when {
                targetIsBottom && initialIsBottom -> bottomNavEnter()
                targetIsBottom && !initialIsBottom -> bottomNavEnter()
                else -> horizontalForward()
            }
        },
        exitTransition = {
            val targetIsBottom = isBottomNav(targetState?.destination?.route)
            val initialIsBottom = isBottomNav(initialState?.destination?.route)
            when {
                targetIsBottom && initialIsBottom -> bottomNavExit()
                targetIsBottom && !initialIsBottom -> bottomNavExit()
                else -> horizontalForwardExit()
            }
        },
        popEnterTransition = {
            val targetIsBottom = isBottomNav(targetState?.destination?.route)
            when {
                targetIsBottom -> bottomNavEnter()
                else -> horizontalBack()
            }
        },
        popExitTransition = {
            val initialIsBottom = isBottomNav(initialState?.destination?.route)
            when {
                initialIsBottom -> bottomNavExit()
                else -> horizontalBackExit()
            }
        }
    ) {
        composable(Screen.Onboarding.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            OnboardingScreen(
                onComplete = {
                    val intent = android.content.Intent(context, com.scrollnumb.app.MainActivity::class.java)
                    context.startActivity(intent)
                    (context as? android.app.Activity)?.finish()
                }
            )
        }
        composable(Screen.Dashboard.route) {
            val scope = rememberCoroutineScope()
            BackHandler(enabled = mainPagerState.currentPage != 0) {
                scope.launch { mainPagerState.animateScrollToPage(0) }
            }
            HorizontalPager(
                state = mainPagerState,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = androidx.compose.ui.Alignment.Top
            ) { page ->
                when (page) {
                    0 -> DashboardScreen(
                        onNavigateToRelaxation = { navController.navigate(Screen.Relaxation.route) },
                        onNavigateToBreathing = { navController.navigate(Screen.RelaxationBreathe.route) },
                        onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                        onNavigateToShield = { navController.navigate(Screen.Shield.route) },
                        onNavigateToStats = {
                            scope.launch { mainPagerState.animateScrollToPage(1) }
                        }
                    )
                    1 -> AnalyticsScreen()
                    else -> ProfileScreen(
                        onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                        onNavigateToEditProfile = { navController.navigate(Screen.EditProfile.route) },
                        onNavigateToXp = { navController.navigate(Screen.XpDetail.route) }
                    )
                }
            }
        }
        composable(Screen.Shield.route) {
            ShieldScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLegal = { navController.navigate(Screen.Legal.route) },
            )
        }
        composable(Screen.Legal.route) {
            LegalScreen(
                onBack = { navController.popBackStack() },
                onNavigateToPrivacy = { navController.navigate(Screen.PrivacyPolicy.route) },
                onNavigateToTerms = { navController.navigate(Screen.TermsOfService.route) }
            )
        }
        composable(Screen.PrivacyPolicy.route) {
            PrivacyPolicyScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.TermsOfService.route) {
            TermsOfServiceScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Relaxation.route) {
            RelaxationScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.RelaxationBreathe.route) {
            RelaxationScreen(
                onBack = { navController.popBackStack() },
                initialTab = 1
            )
        }
        composable(Screen.EditProfile.route) {
            EditProfileScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
        composable(Screen.XpDetail.route) {
            com.scrollnumb.app.feature.xp.XpDetailScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
