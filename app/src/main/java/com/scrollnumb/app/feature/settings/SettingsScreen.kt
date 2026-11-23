package com.scrollnumb.app.feature.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.scrollnumb.app.R
import com.scrollnumb.app.core.designsystem.components.WarmGrainBackground
import com.scrollnumb.app.core.designsystem.components.NumbnessPill
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbCorners
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbType
import com.scrollnumb.app.core.permissions.PermissionsHelper
import com.scrollnumb.app.feature.welcome.WelcomeActivity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToLegal: () -> Unit,

    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showPermissionsSheet by remember { mutableStateOf(false) }
    // Play policy: the accessibility prominent-disclosure dialog must gate
    // EVERY path into the system accessibility settings, not just onboarding.
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    var hasAccessibility by remember { mutableStateOf(PermissionsHelper.isAccessibilityServiceEnabled(context)) }
    var hasOverlay by remember { mutableStateOf(PermissionsHelper.canDrawOverlays(context)) }
    var hasNotif by remember { mutableStateOf(PermissionsHelper.hasNotificationPermission(context)) }
    var hasBattery by remember { mutableStateOf(PermissionsHelper.isIgnoringBatteryOptimizations(context)) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasNotif = PermissionsHelper.hasNotificationPermission(context) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Log a permission_granted event on the false→true transition.
                fun track(name: String, was: Boolean, now: Boolean) {
                }
                val a = PermissionsHelper.isAccessibilityServiceEnabled(context)
                val o = PermissionsHelper.canDrawOverlays(context)
                val n = PermissionsHelper.hasNotificationPermission(context)
                val b = PermissionsHelper.isIgnoringBatteryOptimizations(context)
                track("accessibility", hasAccessibility, a)
                track("overlay", hasOverlay, o)
                track("notifications", hasNotif, n)
                track("battery", hasBattery, b)
                hasAccessibility = a
                hasOverlay = o
                hasNotif = n
                hasBattery = b
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    Box(modifier = Modifier.fillMaxSize().background(ScrollNumbColors.BackgroundDark)) {
    WarmGrainBackground(modifier = Modifier.fillMaxSize())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = ScrollNumbType.BrandDisplay,
                        fontWeight = FontWeight.Bold,
                        color = ScrollNumbColors.Cream
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = ScrollNumbColors.Cream
                        )
                    }
                },
                expandedHeight = 40.dp,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                SettingsRow(
                    iconRes = R.drawable.br_ic_accessibility,
                    title = stringResource(R.string.settings_accessibility),
                    subtitle = stringResource(R.string.settings_accessibility_sub),
                    onClick = { showAccessibilityDisclosure = true }
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingsRow(
                    iconRes = R.drawable.br_ic_translate,
                    title = stringResource(R.string.settings_language),
                    subtitle = supportedLanguages.firstOrNull { it.code == uiState.languageCode }?.englishName
                        ?: uiState.languageCode.uppercase(),
                    onClick = { showLanguageSheet = true }
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingsRow(
                    iconRes = R.drawable.br_ic_widget,
                    title = stringResource(R.string.settings_widgets),
                    subtitle = stringResource(R.string.settings_widgets_sub),
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val manager = androidx.glance.appwidget.GlanceAppWidgetManager(context)
                                manager.requestPinGlanceAppWidget(
                                    com.scrollnumb.app.widget.ScrollNumbWidgetReceiver::class.java
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot pin widget", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingsRow(
                    iconRes = R.drawable.br_ic_email,
                    title = stringResource(R.string.settings_contact),
                    subtitle = stringResource(R.string.settings_contact_sub),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Jasveer17/ScrollNumb-Offline/issues"))
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No browser available", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(20.dp))

                // Single entry point — opens the Permissions sheet with all toggles.
                val grantedCount = listOf(hasAccessibility, hasOverlay, hasNotif, hasBattery).count { it }
                SettingsRow(
                    iconRes = R.drawable.br_ic_shield,
                    title = stringResource(R.string.settings_perm_section),
                    subtitle = "$grantedCount of 4 granted",
                    onClick = { showPermissionsSheet = true }
                )

                Spacer(modifier = Modifier.height(20.dp))
                SettingsRow(
                    iconRes = R.drawable.br_ic_palette,
                    title = "App theme",
                    subtitle = when (uiState.appTheme) {
                        "DARK" -> "Ink (dark)"
                        "LIGHT" -> "Paper (light)"
                        "KAWAII" -> "Kawaii 🌸"
                        "DONGHUA" -> "Donghua 🏮"
                        else -> "Follow system"
                    },
                    onClick = { showThemeSheet = true }
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingsToggleRow(
                    iconRes = R.drawable.br_ic_shield,
                    title = "Awareness checks",
                    subtitle = "Ask \"What video was 2 videos ago?\" before limit",
                    checked = uiState.awarenessChecksEnabled,
                    onCheckedChange = { viewModel.setAwarenessChecksEnabled(it) }
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingsRow(
                    iconRes = R.drawable.br_ic_star_rate,
                    title = stringResource(R.string.settings_rate_us),
                    subtitle = null,
                    onClick = {
                        // Native in-app review sheet; falls back to the store page.
                        val activity = context as? android.app.Activity
                        if (activity != null) {
                            com.scrollnumb.app.core.util.ReviewHelper.request(activity)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingsRow(
                    iconRes = R.drawable.br_ic_update,
                    title = stringResource(R.string.settings_check_updates),
                    subtitle = stringResource(R.string.settings_check_updates_sub),
                    onClick = {
                        val activity = context as? android.app.Activity
                        if (activity != null) {
                            com.scrollnumb.app.core.util.UpdateHelper.checkNow(activity)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingsRow(
                    iconRes = R.drawable.br_ic_document,
                    title = stringResource(R.string.settings_legal),
                    subtitle = null,
                    onClick = onNavigateToLegal
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = stringResource(R.string.settings_version, com.scrollnumb.app.core.util.appVersionName(context)),
                    style = ScrollNumbType.Microcopy,
                    color = ScrollNumbColors.CreamMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
        }

        if (showLanguageSheet) {
            LanguagePickerSheet(onDismiss = { showLanguageSheet = false })
        }
        if (showThemeSheet) {
            ThemePickerSheet(
                current = uiState.appTheme,
                onSelect = { theme ->
                    showThemeSheet = false
                    viewModel.setTheme(theme)
                },
                onDismiss = { showThemeSheet = false }
            )
        }
        if (showAccessibilityDisclosure) {
            com.scrollnumb.app.feature.onboarding.AccessibilityDisclosureDialog(
                onDismiss = { showAccessibilityDisclosure = false },
                onOpenSettings = {
                    showAccessibilityDisclosure = false
                    PermissionsHelper.openAccessibilitySettings(context)
                }
            )
        }

        if (showPermissionsSheet) {
            PermissionsSheet(
                hasAccessibility = hasAccessibility,
                hasOverlay = hasOverlay,
                hasNotif = hasNotif,
                hasBattery = hasBattery,
                onAccessibility = { if (!hasAccessibility) showAccessibilityDisclosure = true },
                onOverlay = { if (!hasOverlay) PermissionsHelper.openOverlaySettings(context) },
                onNotif = {
                    if (!hasNotif) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            PermissionsHelper.openAppNotificationSettings(context)
                        }
                    }
                },
                onBattery = { PermissionsHelper.openBatteryOptimizationSettings(context) },
                onDismiss = { showPermissionsSheet = false }
            )
        }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionsSheet(
    hasAccessibility: Boolean,
    hasOverlay: Boolean,
    hasNotif: Boolean,
    hasBattery: Boolean,
    onAccessibility: () -> Unit,
    onOverlay: () -> Unit,
    onNotif: () -> Unit,
    onBattery: () -> Unit,
    onDismiss: () -> Unit
) {
    // skipPartiallyExpanded → opens fully so all four rows are visible at once.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                text = "Permissions",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp(),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Tap any item to grant it. Required ones keep tracking and the shield working.",
                style = ScrollNumbType.Microcopy,
                color = ScrollNumbColors.CreamMuted,
                modifier = Modifier.padding(bottom = 14.dp)
            )
            PermissionStatusRow(
                title = stringResource(R.string.settings_accessibility),
                isGranted = hasAccessibility,
                onClick = onAccessibility
            )
            Spacer(modifier = Modifier.height(8.dp))
            PermissionStatusRow(
                title = stringResource(R.string.settings_overlay),
                isGranted = hasOverlay,
                onClick = onOverlay
            )
            Spacer(modifier = Modifier.height(8.dp))
            PermissionStatusRow(
                title = stringResource(R.string.settings_notifications),
                isGranted = hasNotif,
                onClick = onNotif
            )
            Spacer(modifier = Modifier.height(8.dp))
            PermissionStatusRow(
                title = stringResource(R.string.settings_battery),
                isGranted = hasBattery,
                onClick = onBattery
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemePickerSheet(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val themes = listOf(
        Triple("SYSTEM", "Follow system", "🌗"),
        Triple("DARK", "Ink — warm dark", "🌙"),
        Triple("LIGHT", "Paper — warm light", "☀️"),
        Triple("KAWAII", "Kawaii — candy pastels", "🌸"),
        Triple("DONGHUA", "Donghua — ink-wash night", "🏮")
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                text = "Choose your theme",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp(),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            themes.forEach { (key, label, emoji) ->
                val selected = current == key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ScrollNumbCorners.lg))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .clickable { onSelect(key) }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = emoji, fontSize = 20.sp())
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = label,
                        fontSize = 15.sp(),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsRow(
    iconRes: Int,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(RoundedCornerShape(ScrollNumbCorners.lg))
            .background(ScrollNumbColors.InkRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(ScrollNumbCorners.sm))
                .background(ScrollNumbColors.Saffron.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ScrollNumbColors.Saffron),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp(),
                fontWeight = FontWeight.SemiBold,
                color = ScrollNumbColors.Cream,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = ScrollNumbType.Microcopy,
                    color = ScrollNumbColors.CreamMuted,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        Image(
            painter = painterResource(id = R.drawable.br_ic_caret_right),
            contentDescription = null,
            colorFilter = ColorFilter.tint(ScrollNumbColors.CreamMuted),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SettingsToggleRow(
    iconRes: Int,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ScrollNumbCorners.md))
            .background(ScrollNumbColors.InkRaised)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(ScrollNumbCorners.sm))
                .background(ScrollNumbColors.Saffron.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ScrollNumbColors.Saffron),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp(),
                fontWeight = FontWeight.SemiBold,
                color = ScrollNumbColors.Cream,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = ScrollNumbType.Microcopy,
                    color = ScrollNumbColors.CreamMuted,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ScrollNumbColors.Ink,
                checkedTrackColor = ScrollNumbColors.Saffron,
                uncheckedThumbColor = ScrollNumbColors.CreamMuted,
                uncheckedTrackColor = ScrollNumbColors.InkWhisper
            )
        )
    }
}

private fun Int.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)

@Composable
private fun PermissionStatusRow(
    title: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ScrollNumbCorners.md))
            .background(ScrollNumbColors.InkRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    if (isGranted) ScrollNumbColors.Sage else ScrollNumbColors.Coral
                )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = ScrollNumbColors.Cream,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (isGranted) stringResource(R.string.settings_perm_granted)
                else stringResource(R.string.settings_perm_denied),
            style = ScrollNumbType.Microcopy,
            color = if (isGranted) ScrollNumbColors.Sage else ScrollNumbColors.CreamMuted
        )
    }
}
