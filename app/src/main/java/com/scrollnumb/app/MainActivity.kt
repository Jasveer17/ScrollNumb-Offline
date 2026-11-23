package com.scrollnumb.app

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.scrollnumb.app.core.datastore.UserPreferences
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbTheme
import com.scrollnumb.app.core.navigation.BottomNavBar
import com.scrollnumb.app.core.navigation.Screen
import com.scrollnumb.app.core.navigation.ScrollNumbNavGraph
import com.scrollnumb.app.data.repository.ScrollRepository
import com.scrollnumb.app.data.repository.StreakRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var userPreferences: UserPreferences


    @Inject
    lateinit var scrollRepository: ScrollRepository

    @Inject
    lateinit var streakRepository: StreakRepository


    // Flexible in-app update: the launcher receives the Play update-consent result
    // (registered before the activity starts, as required by the Activity APIs).
    private val updateLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { /* download proceeds in the background once the user accepts */ }
    private var inAppUpdate: com.scrollnumb.app.core.util.InAppUpdateManager? = null

    // Deep-link/notification route target, consumed once by the NavHost. Held as
    // Compose state so onNewIntent (app already running) also triggers navigation.
    private var pendingRoute by mutableStateOf<String?>(null)
    private var permissionCheckVersion by mutableStateOf(0)

    private fun routeFromIntent(source: android.content.Intent): String? =
        source.getStringExtra("navigate_to") ?: source.data?.let { it.host ?: it.lastPathSegment }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inAppUpdate = com.scrollnumb.app.core.util.InAppUpdateManager(this, updateLauncher)
            .also { it.checkForUpdate() }
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
        // Fully transparent system bars (no default scrim) so the top status-bar
        // area reads as see-through instead of a faint dark band.
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        // Route target from either a notification extra or a deep link
        // (scrollnumb://<route>, e.g. scrollnumb://leaderboard).
        pendingRoute = routeFromIntent(intent)

        setContent {
            val appTheme by userPreferences.appTheme.collectAsState(initial = "SYSTEM")
            val effectiveTheme = appTheme
            val onboardingCompleted by userPreferences.onboardingCompleted.collectAsState(initial = false)
            val trackingEnabled by userPreferences.trackingEnabled.collectAsState(initial = true)
            val darkTheme = when (effectiveTheme) {
                "DARK", "DONGHUA" -> true
                "LIGHT", "KAWAII" -> false
                else -> isSystemInDarkTheme()
            }

            var initialStartDest by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                applyStoredLocale()
                // Everyone competes — the opt-in toggle was removed, so ensure any
                // previously opted-out user is shown on the leaderboard again.
                userPreferences.setLeaderboardOptIn(true)
                val completed = userPreferences.onboardingCompleted.first()
                initialStartDest = if (completed) Screen.Dashboard.route else Screen.Onboarding.route
            }

            ScrollNumbTheme(darkTheme = darkTheme, themeName = effectiveTheme) {
                val startDest = initialStartDest
                if (startDest != null) {
                    val navController = rememberNavController()
                    val mainPagerState = rememberPagerState(pageCount = { 4 })
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors.Ink)
                    ) {
                        Scaffold(
                            bottomBar = { BottomNavBar(navController, mainPagerState) },
                            // Same token every screen paints (ScrollNumbColors
                            // flips it per theme). The default colorScheme
                            // .background is a DIFFERENT token in the light/
                            // paper themes, which painted a square-cornered
                            // band behind the nav tray's rounded top corners.
                            containerColor = com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors.BackgroundDark,
                            modifier = Modifier.fillMaxSize()
                        ) { innerPadding ->
                            // Tab screens draw UNDER the floating nav tray: skip
                            // the bar-height bottom padding so their backgrounds
                            // and auroras run to the physical screen bottom
                            // (visible through the tray's rounded-corner
                            // cutouts). Each tab screen adds its own scroll
                            // clearance (BottomNavUnderlayClearance). Non-tab
                            // routes keep the full padding.
                            val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
                            val navEntry by navController.currentBackStackEntryAsState()
                            val underNavBar = com.scrollnumb.app.core.navigation
                                .isBottomNav(navEntry?.destination?.route)
                            ScrollNumbNavGraph(
                                navController = navController,
                                startDestination = startDest,
                                userPreferences = userPreferences,
                                mainPagerState = mainPagerState,
                                modifier = Modifier.padding(
                                    start = innerPadding.calculateStartPadding(layoutDirection),
                                    top = innerPadding.calculateTopPadding(),
                                    end = innerPadding.calculateEndPadding(layoutDirection),
                                    bottom = if (underNavBar) 0.dp
                                        else innerPadding.calculateBottomPadding()
                                )
                            )

                            LaunchedEffect(pendingRoute) {
                                when (pendingRoute) {
                                    null -> return@LaunchedEffect
                                    "relaxation" -> navController.navigate(Screen.Relaxation.route)
                                    "leaderboard" -> {
                                        mainPagerState.requestScrollToPage(1)
                                        navController.navigate(Screen.Dashboard.route) { launchSingleTop = true }
                                    }
                                    "analytics", "stats" -> {
                                        mainPagerState.requestScrollToPage(2)
                                        navController.navigate(Screen.Dashboard.route) { launchSingleTop = true }
                                    }
                                    "profile" -> {
                                        mainPagerState.requestScrollToPage(3)
                                        navController.navigate(Screen.Dashboard.route) { launchSingleTop = true }
                                    }
                                    "xp", "xp_detail" -> navController.navigate(Screen.XpDetail.route)
                                    "settings" -> navController.navigate(Screen.Settings.route)
                                    "dashboard" -> {
                                        mainPagerState.requestScrollToPage(0)
                                        navController.navigate(Screen.Dashboard.route) { launchSingleTop = true }
                                    }
                                }
                                // Consume so a recomposition/config-change can't re-navigate.
                                pendingRoute = null
                            }
                        }
                        // Theme atmosphere: petals (Kawaii), lantern motes +
                        // mist (Donghua), warm dust (Light). Non-interactive,
                        // very low alpha, single Canvas — zero jank.
                        com.scrollnumb.app.core.designsystem.components.ThemedParticles(
                            themeName = effectiveTheme,
                            modifier = Modifier.fillMaxSize()
                        )

                        val permissionVersion = permissionCheckVersion
                        val hasAccessibility = remember(permissionVersion) {
                            com.scrollnumb.app.core.permissions.PermissionsHelper
                                .isAccessibilityServiceEnabled(this@MainActivity)
                        }
                        val hasUsageAccess = remember(permissionVersion) {
                            com.scrollnumb.app.core.permissions.PermissionsHelper
                                .hasUsageStatsPermission(this@MainActivity)
                        }
                        var dismissedVersion by remember { mutableStateOf(-1) }
                        if (onboardingCompleted && dismissedVersion != permissionVersion) {
                            when {
                                !hasAccessibility -> {
                                    com.scrollnumb.app.feature.onboarding.AccessibilityDisclosureDialog(
                                        onDismiss = { dismissedVersion = permissionVersion },
                                        onOpenSettings = {
                                            com.scrollnumb.app.core.permissions.PermissionsHelper
                                                .openAccessibilitySettings(this@MainActivity)
                                        }
                                    )
                                }
                                !hasUsageAccess -> MandatoryPermissionDialog(
                                    title = "Usage Access is required",
                                    message = "ScrollNumb needs Usage Access to verify when tracked apps leave the foreground and keep competition counting reliable.",
                                    actionLabel = "Open Usage Access",
                                    onDismiss = { dismissedVersion = permissionVersion },
                                    onAction = {
                                        com.scrollnumb.app.core.permissions.PermissionsHelper
                                            .openUsageAccessSettings(this@MainActivity)
                                    }
                                )
                                !trackingEnabled -> MandatoryPermissionDialog(
                                    title = "Scroll tracking is paused",
                                    message = "Turn tracking back on in Settings to keep counting reels and remain eligible for ranked leagues.",
                                    actionLabel = "Open Settings",
                                    onDismiss = { dismissedVersion = permissionVersion },
                                    onAction = {
                                        dismissedVersion = permissionVersion
                                        navController.navigate(Screen.Settings.route)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun applyStoredLocale() {
        val code = userPreferences.languageCode.first()
        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    override fun onResume() {
        super.onResume()

        permissionCheckVersion++
        // Install an update that finished downloading while backgrounded.
        inAppUpdate?.completeIfDownloaded()
        // Refresh the widget from the app's real today count so it can't stay
        // stuck on a stale service value or diverge from what the app shows.
        lifecycleScope.launch {
            try {
                // Finalize yesterday before today's recompute can roll the XP day
                // key forward and make its pending restraint impossible to amend.
                com.scrollnumb.app.service.DailyResetWorker.runReset(this@MainActivity)
                val count = scrollRepository.getTodayStats()?.totalScrolls ?: 0
                val limit = userPreferences.dailyLimit.first()
                val streakDays = streakRepository.getStreakStatus().first()?.currentStreak ?: 0
                com.scrollnumb.app.core.util.XpLedger.recompute(
                    userPreferences = userPreferences,
                    streakDays = streakDays,
                    reelsToday = count,
                    dailyLimit = limit
                )
                com.scrollnumb.app.widget.WidgetUpdater.update(this@MainActivity, count, limit)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Resume refresh failed", e)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun MandatoryPermissionDialog(
        title: String,
        message: String,
        actionLabel: String,
        onDismiss: () -> Unit,
        onAction: () -> Unit
    ) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            title = { androidx.compose.material3.Text(title) },
            text = { androidx.compose.material3.Text(message) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = onAction) {
                    androidx.compose.material3.Text(actionLabel)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    androidx.compose.material3.Text("Not now")
                }
            }
        )
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // App was already running — route the new deep link/notification target.
        routeFromIntent(intent)?.let { pendingRoute = it }
    }

    override fun onDestroy() {
        inAppUpdate?.unregister()
        super.onDestroy()
    }

}
