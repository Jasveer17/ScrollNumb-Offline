package com.scrollnumb.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Image
import com.scrollnumb.app.R
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.size
import com.scrollnumb.app.core.datastore.UserPreferences
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbTheme
import com.scrollnumb.app.core.util.Constants
import com.scrollnumb.app.core.util.AvatarHelper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import com.scrollnumb.app.data.repository.ScrollRepository
import com.scrollnumb.app.service.SeenResult
import com.scrollnumb.app.domain.model.BrainState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ScrollEvent(
    val platform: String,
    val timestamp: Long,
    val sessionId: String
)

@AndroidEntryPoint
class ScrollAccessibilityService : AccessibilityService(), ViewModelStoreOwner {

    private lateinit var windowManager: WindowManager
    private var floatingComposeView: ComposeView? = null
    private var floatingParams: WindowManager.LayoutParams? = null
    private var savedPillX = 0
    private var savedPillY = 0
    private var awarenessDebugReceiver: android.content.BroadcastReceiver? = null

    private val mViewModelStore = ViewModelStore()
    override val viewModelStore: ViewModelStore
        get() = mViewModelStore

    private val lifecycleOwner = object : androidx.lifecycle.LifecycleOwner {
        private val registry = androidx.lifecycle.LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
        fun start() { registry.currentState = Lifecycle.State.STARTED }
        fun stop() { registry.currentState = Lifecycle.State.DESTROYED }
    }

    private val savedStateRegistryOwner = object : androidx.savedstate.SavedStateRegistryOwner {
        private val controller = androidx.savedstate.SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle = lifecycleOwner.lifecycle
        override val savedStateRegistry = controller.savedStateRegistry
        fun init() { controller.performRestore(null) }
    }

    companion object {
        private const val TAG = "ScrollNumb"
        val scrollEvents = MutableSharedFlow<ScrollEvent>(extraBufferCapacity = 100)
        var isRunning = false
            private set
        // The live accessibility-service instance. Only an AccessibilityService
        // context may add a TYPE_ACCESSIBILITY_OVERLAY window, so the shield
        // overlay is attached through this (see BlockerOverlayService).
        @Volatile var instance: ScrollAccessibilityService? = null
            private set

        /** Settings cannot be closed by a normal background activity launch on
         * modern Android. The enabled accessibility service can issue the same
         * Back action the user would, returning to the activity that opened it. */
        fun returnFromPermissionSettings(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_BACK) == true

        var passEndTime = 0L

        // Set by BlockerOverlayService when the user clicks "Give me X more"
        // in the timer-completed screen. The polling engine picks this up
        // on the next pass and raises the effective daily cap.
        @Volatile var pendingCapExtension = 0

        // Manual block mechanism: set from the settings UI (ShieldScreen)
        // to block reels for a fixed period. 0 = no manual block.
        // Otherwise, the epoch-millis until which the block is active.
        @Volatile var manualBlockExpiryMs: Long = 0L

        // Human-readable label for the active manual block, displayed on
        // the LOCKED_FOR_DAY shield page. Set by the ViewModel alongside
        // manualBlockExpiryMs. Cleared on unblock / expiry.
        @Volatile var manualBlockMessage: String = ""

        // Monotonic counter incremented whenever manualBlockMessage changes.
        // The LOCKED_FOR_DAY Compose page reads this value to trigger
        // recomposition when the message updates (Compose caches the old
        // content otherwise, so "See you tomorrow" persists after a 7-day
        // block is set while the shield is already visible).
        @Volatile var manualBlockMessageVersion: Long = 0L

        // Live scroll count, updated by the service and read by the blocker overlay
        @Volatile var currentScrollCount = 0
    }

    @Inject
    lateinit var scrollRepository: ScrollRepository

    @Inject
    lateinit var userPreferences: UserPreferences


    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Posts overlay (pill/shield) view operations onto the main thread. The
    // polling engine now runs its heavy node scan on Dispatchers.Default, so
    // the few UI mutations must hop back to main via this handler. Posts run
    // sequentially on the single main looper → show/hide ordering is race-free.
    private val mainHandler = Handler(Looper.getMainLooper())

    // NOTE: gesture-based pauseMedia() was removed alongside canPerformGestures.
    // The full-screen shield already blocks interaction, so auto-pausing the
    // reel behind it does not justify synthetic-tap capability.

    @Volatile private var lastScrollTime = 0L
    @Volatile private var lastConsumedSignature = ""
    private var lastItemIndex = -1
    private var lastScrollY = -1
    // Pager position of the reel currently on screen, read from the
    // TYPE_VIEW_SCROLLED indices fired by the reel pager. This is the ONLY
    // reliable "which reel" signal when consecutive reels share author+caption
    // — exactly the case that broke counting on a single person's profile reels
    // (every reel is "Reel by <sameAuthor>" with no per-reel id in the tree).
    // -1 = unknown (no scroll index seen yet this surface).
    @Volatile private var reelPageIndex = -1
    // The pager page that was last COUNTED. A swipe that lands on a different
    // page is a genuine new reel; a loop-back to a prior page is not.
    @Volatile private var lastCountedPageIndex = Int.MIN_VALUE
    // Page index captured alongside a debounce-deferred (pending) count.
    @Volatile private var pendingPageIndex = Int.MIN_VALUE
    // True when reelPageIndex is the SYNTHETIC counter (toIndex == -1
    // surfaces: IG profile reels, single-channel Shorts) rather than a real
    // pager index. The fast count path must stay OFF on synthetic surfaces:
    // they rely on the slow-scroll auto-intent to count crossfade swipes,
    // and a fast count arming the settle window there suppressed auto-intent
    // for 2.5s and silently dropped the next crossfade reel.
    @Volatile private var reelPageIsSynthetic = false
    // Fast-path settle reconciliation. A fast count fires on the FIRST pass
    // after a swipe (pre-stability), so the signature it read may be a
    // mid-swipe transient. While this window is armed, a stable signature
    // change on the SAME page with NO new scroll intent is the settle of the
    // already-counted swipe: re-attribute the identity, never count again.
    // Only fast counts arm it (stable-path counts read settled sigs already).
    @Volatile private var settleReconcileUntilMs = 0L
    @Volatile private var settleReconcilePage = Int.MIN_VALUE
    @Volatile private var settleReconcilePlatform = ""
    // Panel scrolls (comments/description/share) must never mutate the reel
    // pager identity. The poller owns detection; the event thread reads this
    // flag before accepting pager events.
    @Volatile private var reelPanelOpen = false
    private var pageBeforePanel = Int.MIN_VALUE
    @Volatile private var instagramCommentLatchUntilMs = 0L
    // Last time the SYNTHETIC Instagram page index was bumped (profile reels with
    // toIndex == -1). Debounces a fling's burst of scroll events to one page step.
    private var lastSyntheticPageBumpMs = 0L
    private var lastReelSurfaceTime = 0L
    private var currentSessionId = UUID.randomUUID().toString()
    @Volatile private var currentPlatform = ""
    @Volatile private var lastKnownTrackedPackage = ""
    @Volatile private var lastKnownTrackedPlatform = ""
    private var capShieldContentVisible = false

    // ── Deterministic scroll-event queue (replaces the racy 5 s time window) ──
    // Set by onAccessibilityEvent / onGesture when a real reel scroll is detected.
    // Consumed by the polling loop the first time a signature change is observed.
    // Written from the accessibility-event thread (main) AND the polling loop
    // (Dispatchers.Default), so every shared scalar here is @Volatile for
    // cross-thread visibility.
    @Volatile private var scrollEventQueued = false
    @Volatile private var scrollEventPlatform: String = ""
    @Volatile private var scrollEventTimestamp: Long = 0L
    // A "strong" intent comes from a real swipe (TYPE_VIEW_SCROLLED / onGesture)
    // and always authorises a count. A "weak" intent is inferred from a
    // TYPE_WINDOW_CONTENT_CHANGED (slow-scroll fallback) and only counts when the
    // reel genuinely changed — this stops caption-expand / comment-open content
    // changes from phantom-counting the same reel.
    @Volatile private var scrollEventStrong = true
    @Volatile private var scrollEventUserDriven = false
    private val scrollEventTimeoutMs = 4_000L
    private val reelTransitionSettleMs = 180L
    @Volatile private var instagramUiInteractionSuppressUntilMs = 0L
    private val globalScrollCountFlow = MutableStateFlow(0)
    private var globalScrollCount: Int
        get() = globalScrollCountFlow.value
        set(value) {
            globalScrollCountFlow.value = value
            currentScrollCount = value
            updateWidgetCount(value)
        }

    private val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var lastCountedDate: String = dateKeyFormat.format(Date())

    // Once the day cap is hit, this latches until midnight. Every subsequent
    // entry into a tracked app shows the shield and force-exits the reel so
    // the count can never grow past the effective daily limit.
    @Volatile private var dayLocked: Boolean = false
    private var dayLockedDate: String = dateKeyFormat.format(Date())

    // Per-session cap extension. Incremented when the user clicks "Give me
    // X more" in the shield's timer-completed screen. The effective limit
    // used for cap comparisons is dailyLimit + capExtension.
    @Volatile private var capExtension = 0

    // The effective daily cap, considering both the user's base limit from
    // prefs and any session-level extensions granted by the user.
    private val effectiveDailyLimit: Int get() = dailyLimit + capExtension

    // Timestamp until which counting is suppressed after a cap extension.
    // Prevents dynamic screen content (seekbar, animations, etc.) from
    // triggering phantom scroll counts immediately after unlocking.
    private var capExtensionGraceMs = 0L

    /**
     * Call this when a real reel scroll is detected (TYPE_VIEW_SCROLLED or
     * onGesture).  It queues a scroll intent that the polling loop will
     * consume the first time it observes a signature change.
     */
    private fun queueScrollEvent(
        platform: String,
        packageName: String? = null,
        strong: Boolean = true,
        userDriven: Boolean = false
    ) {
        if (platform.isEmpty() || !isPlatformEnabled(platform)) return
        val now = System.currentTimeMillis()
        // Click-suppression applies on EVERY platform: a caption/comment tap on
        // YouTube reflows the tree just like on Instagram and was +1-counting.
        if (now < instagramUiInteractionSuppressUntilMs) return
        if (now - lastScrollTime < 300L) {
            // Ignore events within the debounce window of the last successful
            // count. Was 1000ms, which capped counting at ~1 reel/sec and lost
            // every other reel on fast scrolling; the SCROLL_DEBOUNCE_MS (300ms)
            // count gate + signature dedup downstream prevent double-counting.
            return
        }
        scrollEventQueued = true
        scrollEventStrong = strong
        scrollEventUserDriven = scrollEventUserDriven || userDriven
        scrollEventPlatform = platform
        scrollEventTimestamp = now
        packageName?.takeIf { it.isNotEmpty() }?.let { lastKnownTrackedPackage = it }
        lastKnownTrackedPlatform = platform
        // Count on the very next pass, not up to 300ms from now.
        pokePolling()
    }

    /** Clear a pending scroll intent (e.g., on platform switch or timeout). */
    private fun clearScrollEvent() {
        scrollEventQueued = false
        scrollEventStrong = true
        scrollEventUserDriven = false
        scrollEventPlatform = ""
        scrollEventTimestamp = 0L
    }

    private suspend fun refreshCountForDateChange() {
        val todayStr = dateKeyFormat.format(Date())
        var dayJustChanged = false
        var dayUnlocked = false
        if (todayStr != lastCountedDate) {
            // The date rolled over — read today's stats fresh so we don't carry
            // yesterday's accumulated count into the new day's limit.
            lastCountedDate = todayStr
            val stats = scrollRepository.getTodayStats()
            globalScrollCount = stats?.totalScrolls ?: 0
            dayJustChanged = true
            // Reset per-day state on midnight rollover so old signatures don't
            // carry into the new day and cap re-lock doesn't use stale data.
            seenVideosMutex.withLock { seenVideosList.clear() }
            recentVideoMemory.clear()
            setAwarenessPopupVisible(false)
            lastAwarenessPromptTimeMs = 0L
            lastAwarenessPromptCount = 0
            pendingSignature = ""
            pendingPrevSig = ""
            lastScreenSignature = ""
            lastConsumedSignature = ""
            Log.d(TAG, "date change: new day=$todayStr, count reset to $globalScrollCount, cleared seen+signature state")
        }
        if (todayStr != dayLockedDate) {
            if (manualBlockExpiryMs > 0 && System.currentTimeMillis() < manualBlockExpiryMs && shieldEnabled) {
                // Manual multi-day block is still active — keep locked
                // and refresh the date stamp so tomorrow's check also
                // catches the mismatch and keeps the lock.
                dayLocked = true
                dayLockedDate = todayStr
            } else {
                clearDayLock()
                manualBlockExpiryMs = 0L
                dayLockedDate = todayStr
                dayJustChanged = true
                dayUnlocked = true
            }
        }
        // Un-latch the day lock if the count is below the effective limit
        // (either because the user raised the base cap, or because they
        // granted a session extension via the "Give me X more" flow).
        // Skip if a manual block (settings UI) is active — manual blocks
        // override the cap-based auto-lock and should not be cleared here.
        // Also keep locked if the absolute hard cap of 200 reels is reached.
        if (dayLocked && globalScrollCount < effectiveDailyLimit && globalScrollCount < 200 &&
            !(manualBlockExpiryMs > 0 && System.currentTimeMillis() < manualBlockExpiryMs)) {
            Log.d(TAG, "day unlock: count=$globalScrollCount < effectiveLimit=$effectiveDailyLimit")
            clearDayLock()
            capExtension = 0
            shieldShownThisSurface = false
            dayJustChanged = true
            dayUnlocked = true
        }
        // When the day unlocks, reset the shield timer so the next time the
        // cap is hit the user gets a fresh block duration, not whatever was
        // leftover from the previous lock cycle.
        if (dayJustChanged) {
            Log.d(TAG, "calling resetLockTimer (dayJustChanged=true)")
            com.scrollnumb.app.service.BlockerOverlayService.instance?.resetLockTimer()
        }
    }

    // Coalesces widget refreshes: a Glance update is a FULL widget
    // recomposition + RemoteViews inflation + DataStore write, and running it
    // on EVERY count contended with the pill's 260ms odometer roll — measured
    // 22% janky frames / 200ms 99th-percentile while counting. The launcher
    // widget is invisible while the user is inside a reel app, so one update
    // shortly after the count burst ends is visually identical.
    private var widgetUpdateJob: kotlinx.coroutines.Job? = null

    private fun updateWidgetCount(count: Int) {
        widgetUpdateJob?.cancel()
        widgetUpdateJob = serviceScope.launch {
            kotlinx.coroutines.delay(800L)
            try {
                val manager = androidx.glance.appwidget.GlanceAppWidgetManager(this@ScrollAccessibilityService)
                val widget = com.scrollnumb.app.widget.ScrollNumbWidget()
                manager.getGlanceIds(widget.javaClass).forEach { glanceId ->
                    androidx.glance.appwidget.state.updateAppWidgetState(this@ScrollAccessibilityService, glanceId) { prefs ->
                        prefs[androidx.datastore.preferences.core.intPreferencesKey("total_scrolls")] = count
                        prefs[androidx.datastore.preferences.core.intPreferencesKey("daily_limit")] = dailyLimit
                    }
                    widget.update(this@ScrollAccessibilityService, glanceId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Seen videos list for deduplication (replaces visitedReelIndices)
    private val seenVideosList = SeenVideosList()
    private val seenVideosMutex = kotlinx.coroutines.sync.Mutex()
    private val recentVideoMemory = RecentVideoMemory()
    private val isAwarenessPromptVisibleFlow = MutableStateFlow(false)
    @Volatile private var awarenessChecksEnabled = true
    @Volatile private var lastAwarenessPromptTimeMs = 0L
    @Volatile private var lastAwarenessPromptCount = 0
    private val packageEventsChannel = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.CONFLATED)

    // Poked from onAccessibilityEvent so the polling engine runs a pass NOW
    // instead of sleeping out the fixed interval — this is what makes the pill
    // and the count react within ~100ms of a swipe/app-entry rather than up to
    // 300ms later. CONFLATED: an event burst collapses into a single wake-up.
    private val pollTrigger = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    @Volatile private var lastPollPokeMs = 0L

    // Battery Saver throttles this (non-top-app) process' CPU, which stretches
    // every detection pass — and a count needs 2-3 timed passes, so the lag
    // multiplied into a visibly late +1. When power save is on we sleep less
    // between passes to keep the wall-clock count latency roughly unchanged.
    // Passes are serialized (sleep → full scan → sleep), so a shorter sleep
    // can never stack scans; the slower scan itself is the throttle.
    @Volatile private var powerSaveMode = false
    private var powerSaveReceiver: android.content.BroadcastReceiver? = null

    private fun pokePolling() {
        val now = System.currentTimeMillis()
        // Fast pokes only when a swipe is actually pending; otherwise cap at the
        // idle cadence — event floods on NON-reel surfaces (home feed, DMs) were
        // driving full detection scans ~10x/sec through the queued-path gap.
        val minGapMs = if (scrollEventQueued) 35L else 300L
        if (now - lastPollPokeMs < minGapMs) return
        lastPollPokeMs = now
        pollTrigger.trySend(Unit)
    }

    private var wasInReelSurface = false
    // When the current reel surface was entered — used to tell the pager's
    // initial page-settle event apart from a genuine first swipe.
    private var surfaceEnteredAtMs = 0L
    private var shieldShownThisSurface = false
    // YouTube: opening a Short IS watching one, so the entry Short counts as
    // soon as its signature first stabilises (two agreeing passes) instead of
    // waiting for a swipe. Armed on genuine surface entry, consumed once, and
    // expires after ENTRY_COUNT_WINDOW_MS. performCount's seen-list dedup keeps
    // a re-opened Short from counting twice.
    @Volatile private var entryCountPending = false
    // Rate limit for event-driven poll pokes (see onAccessibilityEvent).
    @Volatile private var lastEventPokeMs = 0L
    // Provisional entry count: a cold YouTube player can take 3-4s to expose
    // channel/title nodes (device-verified 2.9-3.9s pill→entry gaps), which
    // made the entry +1 feel broken. After this delay with NO signature, the
    // entry Short is counted under a provisional identity and reconciled when
    // the real signature resolves (rolled back if it turns out already seen).
    @Volatile private var provisionalEntryActive = false
    private val ENTRY_PROVISIONAL_AFTER_MS = 600L
    // 10s: a cold YouTube start can take 6-7s before the Shorts tree exposes a
    // stable signature; warm opens resolve in <1.5s. Consumed on first count,
    // so the long window never risks a second one.
    private val ENTRY_COUNT_WINDOW_MS = 10_000L
    // Signature the user was on when they LEFT the surface/app. Leaving forces
    // currentPlatform to "" and the return then reads as a platform switch,
    // which clears seenVideosList — so the entry count would re-count the very
    // short the user paused to check another app (verified on device). The
    // entry count skips this one signature; a swipe to any other short counts
    // normally.
    private var sigAtLastLeave = ""

    // BrainPal Channel Consumer Jobs
    private var consumerJob: kotlinx.coroutines.Job? = null
    private var pollingJob: kotlinx.coroutines.Job? = null

    // Per-session scroll tracking state
    @Volatile private var lastScreenSignature = ""
    // Signature seen on the PREVIOUS poll pass — counting requires two
    // consecutive passes to agree (kills mid-swipe transient +2s).
    private var prevPassSignature = ""
    // Grace window after leaving a reel surface before fully resetting state.
    // Was 8_000L which kept wasInReelSurface true through entire profile visits,
    // causing phantom counts. Reduced to 2_000L — long enough to absorb a brief
    // reel-to-reel transition gap, short enough to clear before a profile visit
    // can trigger a false count.
    private val reelSurfaceGraceMs = 2_000L
    @Volatile private var pendingSignature: String = ""
    @Volatile private var pendingPrevSig: String = ""
    // Throttle date-change refresh (avoids creating SimpleDateFormat every 150ms)
    private var lastDateRefreshMs = 0L
    private val DATE_REFRESH_INTERVAL_MS = 5_000L

    // Local cached preferences
    @Volatile private var trackingEnabled = true
    @Volatile private var shieldEnabled = true
    @Volatile private var dailyLimit = Constants.DEFAULT_DAILY_LIMIT
    @Volatile private var blockDurationMins = 5
    @Volatile private var trackInstagram = true
    @Volatile private var trackYoutube = true
    @Volatile private var trackFacebook = true
    @Volatile private var trackSnapchat = true
    @Volatile private var trackLinkedin = true
    @Volatile private var trackTiktok = true
    
    // Time tracking variables
    private var dailyTimeLimitMins = 30
    private var activeSessionStartTime = 0L
    private var lastSavedSessionTime = 0L
    private var accumulatedTimeTodayMs = 0L
    private var timeTrackingJob: kotlinx.coroutines.Job? = null
    private lateinit var usageStatsMonitor: UsageStatsMonitor

    private val usageStatsManager: android.app.usage.UsageStatsManager by lazy {
        getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
    }

    private val targetPackages = mapOf(
        Constants.PKG_INSTAGRAM to Constants.PLATFORM_INSTAGRAM,
        Constants.PKG_YOUTUBE to Constants.PLATFORM_YOUTUBE,
        Constants.PKG_FACEBOOK to Constants.PLATFORM_FACEBOOK,
        Constants.PKG_SNAPCHAT to Constants.PLATFORM_SNAPCHAT,
        Constants.PKG_LINKEDIN to Constants.PLATFORM_LINKEDIN,
        Constants.PKG_TIKTOK to Constants.PLATFORM_TIKTOK,
        Constants.PKG_TIKTOK_AWEME to Constants.PLATFORM_TIKTOK,
        Constants.PKG_TIKTOK_TRILL to Constants.PLATFORM_TIKTOK
    )

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        savedStateRegistryOwner.init()
        lifecycleOwner.start()
        
        // Collect preferences dynamically
        serviceScope.launch {
            userPreferences.trackingEnabled.collect { trackingEnabled = it }
        }
        serviceScope.launch {
            userPreferences.shieldEnabled.collect { shieldEnabled = it }
        }
        serviceScope.launch {
            userPreferences.dailyLimit.collect { dailyLimit = it }
        }
        serviceScope.launch {
            userPreferences.dailyTimeLimitMins.collect { dailyTimeLimitMins = it }
        }
        serviceScope.launch {
            userPreferences.blockDurationMins.collect { blockDurationMins = it }
        }
        serviceScope.launch {
            userPreferences.trackedInstagram.collect { trackInstagram = it }
        }
        serviceScope.launch {
            userPreferences.trackedYoutube.collect { trackYoutube = it }
        }
        serviceScope.launch {
            userPreferences.trackedFacebook.collect { trackFacebook = it }
        }
        serviceScope.launch {
            userPreferences.trackedSnapchat.collect { trackSnapchat = it }
        }
        serviceScope.launch {
            userPreferences.trackedLinkedin.collect { trackLinkedin = it }
        }
        serviceScope.launch {
            userPreferences.trackedTiktok.collect { trackTiktok = it }
        }
        serviceScope.launch {
            userPreferences.awarenessChecksEnabled.collect { awarenessChecksEnabled = it }
        }

        // Restore a persisted manual block (e.g. "Lock me for the day") so a
        // reboot or the OS killing the service doesn't silently drop the lock.
        // Only re-apply when it hasn't expired; an expired stamp is cleared.
        serviceScope.launch {
            val expiry = userPreferences.manualBlockExpiry.first()
            if (expiry > System.currentTimeMillis()) {
                manualBlockExpiryMs = expiry
                manualBlockMessage = userPreferences.manualBlockMessageStored.first()
                manualBlockMessageVersion++
                dayLocked = true
                dayLockedDate = dateKeyFormat.format(Date())
            } else if (expiry != 0L) {
                userPreferences.clearManualBlock()
            }
        }

        usageStatsMonitor = UsageStatsMonitor(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        val info = this.serviceInfo ?: android.accessibilityservice.AccessibilityServiceInfo()
        // Narrowed event set (matches the static XML) — no global typeAllMask.
        info.eventTypes = android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED or
                android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED
        info.feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
        // FLAG_INCLUDE_NOT_IMPORTANT_VIEWS: Facebook marks its Reels action-rail
        // containers notImportantForAccessibility, so without this flag the
        // service gets a pruned tree and the rail is undetectable — the pill
        // never showed on FB reels (it "magically" worked whenever uiautomator
        // was attached, because uiautomator forces this flag globally).
        // FLAG_RETRIEVE_INTERACTIVE_WINDOWS: without it getWindows() is always
        // empty, which had silently killed every windows-based fallback (e.g.
        // findForegroundPackage's active-window scan).
        info.flags = android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        // Allowlist = tracked apps + LAUNCHERS. The launcher's
        // WINDOW_STATE_CHANGED is the only immediate "user went home" signal on
        // gesture-nav (a fully open allowlist fixed that but made EVERY app
        // generate accessibility events for us — visible jank system-wide,
        // amplified by FLAG_INCLUDE_NOT_IMPORTANT_VIEWS). With this list the OS
        // delivers nothing from unrelated apps.
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val defaultLauncher = packageManager.resolveActivity(
            homeIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName
        info.packageNames =
            (targetPackages.keys + knownLaunchers + listOfNotNull(defaultLauncher)).toTypedArray()
        // Match the XML: don't let the OS coalesce events for 500ms (default
        // carried over from the old config) — that lag delayed counting and
        // the pill by up to half a second per event. 50→30ms as part of the
        // count-latency pass (2026-07-12); events only come from allowlisted
        // packages, so the extra delivery volume is modest.
        info.notificationTimeout = 30L
        this.serviceInfo = info
        isRunning = true
        instance = this

        // Track Battery Saver so the polling engine can shorten its sleeps
        // (see startPollingEngine) — under power save the OS caps this
        // process' CPU and each detection pass runs slower.
        val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        powerSaveMode = powerManager.isPowerSaveMode
        powerSaveReceiver?.let { runCatching { unregisterReceiver(it) } }
        powerSaveReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                powerSaveMode = powerManager.isPowerSaveMode
            }
        }.also {
            registerReceiver(it, android.content.IntentFilter(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        }

        awarenessDebugReceiver?.let { runCatching { unregisterReceiver(it) } }
        awarenessDebugReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                Log.d(TAG, "Awareness debug broadcast received")
                mainHandler.post {
                    if (floatingComposeView == null) {
                        showFloatingWidget(Constants.PLATFORM_INSTAGRAM)
                    }
                    setAwarenessPopupVisible(true)
                }
            }
        }.also {
            val filter = android.content.IntentFilter("com.scrollnumb.app.TRIGGER_AWARENESS")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(it, filter, android.content.Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(it, filter)
            }
        }

        // The user just flipped the toggle ON in system Settings after OUR
        // hand-off (flag armed by PermissionsHelper.openAccessibilitySettings):
        // bring the app straight back to the front so onboarding/settings
        // resumes without pressing Back twice. Accessibility services are
        // exempt from background-activity-start restrictions. 10-min freshness
        // guard so a service reconnect (reboot, crash restart) never hijacks
        // the screen.
        try {
            com.scrollnumb.app.core.permissions.PermissionsHelper.returnToAppAfterPermissionGrant(
                this,
                com.scrollnumb.app.core.permissions.PermissionsHelper.KEY_RETURN_AFTER_ACCESS_GRANT
            )
        } catch (e: Exception) {
            Log.e(TAG, "return-to-app after accessibility grant failed", e)
        }

        startConsumerAndCleaner()

        // Load today's count from repository to sync on startup. This is
        // the single source of truth — we always re-read the current day's
        // value so the shield never fires on stale data.
        serviceScope.launch {
            val today = dateKeyFormat.format(Date())
            lastCountedDate = today
            dayLockedDate = today
            clearDayLock()
            val stats = scrollRepository.getTodayStats()
            globalScrollCount = stats?.totalScrolls ?: 0
        }

        // Keep the in-memory count in sync with DB-side mutations the service
        // didn't make itself — e.g. "recover focus" decrementing the last scroll.
        // Without this the shield/cap/widget would stay at the old count until the
        // next midnight/startup re-read, so recovering focus wouldn't unlock.
        serviceScope.launch {
            scrollRepository.scrollUpdates.collect { dbTotal ->
                if (dbTotal != globalScrollCount) globalScrollCount = dbTotal
            }
        }

        // Sync total time today
        serviceScope.launch {
            scrollRepository.getTotalTimeForTodayFlow().collect { totalTime ->
                accumulatedTimeTodayMs = totalTime ?: 0L
            }
        }
        
        // Start UsageStats polling fallback
        serviceScope.launch {
            usageStatsMonitor.pollForegroundApp(3000L).collect { pkg ->
                if (pkg != null) {
                    val platform = targetPackages[pkg]
                    if (platform != null && isPlatformEnabled(platform)) {
                        lastKnownTrackedPackage = pkg
                        lastKnownTrackedPlatform = platform
                        if (platform != currentPlatform) {
                            val hasPendingIntentForPlatform = scrollEventQueued
                            if (currentPlatform.isNotEmpty()) {
                                stopTimeTracking()
                            }
                            currentPlatform = platform
                            lastKnownTrackedPackage = pkg
                            lastKnownTrackedPlatform = platform
                            currentSessionId = java.util.UUID.randomUUID().toString()
                            serviceScope.launch {
                                seenVideosMutex.withLock {
                                    seenVideosList.clear()
                                }
                            }
                            lastItemIndex = -1
                            lastScrollY = -1
                            if (!hasPendingIntentForPlatform) {
                                clearScrollEvent()
                            }
                            wasInReelSurface = false
                            startTimeTracking(platform)
                        }
                    }
                }
            }
        }
    }

    private fun startConsumerAndCleaner() {
        startPollingEngine()

        consumerJob?.cancel()
        consumerJob = serviceScope.launch(Dispatchers.Default) {
            for (pkg in packageEventsChannel) {
                val platform = targetPackages[pkg] ?: continue
                if (isPlatformEnabled(platform)) {
                    lastKnownTrackedPackage = pkg
                    lastKnownTrackedPlatform = platform
                    if (platform != currentPlatform) {
                        val hasPendingIntentForPlatform = scrollEventQueued
                        if (currentPlatform.isNotEmpty()) {
                            stopTimeTracking()
                        }
                        currentPlatform = platform
                        lastKnownTrackedPackage = pkg
                        lastKnownTrackedPlatform = platform
                        currentSessionId = java.util.UUID.randomUUID().toString()
                        serviceScope.launch {
                            seenVideosMutex.withLock { seenVideosList.clear() }
                        }
                        lastItemIndex = -1
                        lastScrollY = -1
                        if (!hasPendingIntentForPlatform) {
                            clearScrollEvent()
                        }
                        wasInReelSurface = false
                        entryCountPending = false
                        startTimeTracking(platform)
                    }
                }
            }
        }

        // NOTE: The seenVideosList clear-on-interval cleaner was removed.
        // lastScreenSignature already prevents the same reel from being
        // re-counted within a session. Clearing seenVideosList every 60s
        // caused phantom counts when content metadata (view count, etc.)
        // changed the signature within that window.
    }

    // Sustained-period gate for hiding the shield when the user is off
    // tracked apps. 1 pass = 150ms, so we wait ~500ms of *consecutive*
    // non-tracked-app passes before we tear the shield down. Brief
    // detection glitches (1-2 passes with unknown package name) get
    // absorbed and don't cause the shield to blink.
    private var offTrackedAppSince: Long = 0L
    private val OFF_TRACKED_APP_THRESHOLD_MS = 200L

    // When the HOME launcher is the foreground root this long (sustained), the
    // user genuinely left the tracked app — tear the pill/shield down promptly
    // instead of riding the gesture-nav "pinned" grace for seconds. A brief
    // launcher flash during gesture nav stays under this threshold.
    private var launcherForegroundSince: Long = 0L
    private val LAUNCHER_HOME_THRESHOLD_MS = 300L

    // Require the user to be on a tracked app for this long before
    // re-showing the shield. Prevents the 3-second blink when the user
    // bounces between our app and a tracked app.
    private var onTrackedAppSince: Long = 0L
    private val ON_TRACKED_APP_STABLE_MS = 500L
    // Shorter stability window for the pill + counting. During a leave
    // transition (swipe-up to home/recents) the gate briefly re-detects the
    // tracked app (off→on→off in <1s), which flashed the pill and let a
    // gesture-induced scroll phantom-count. Requiring the app to be steadily
    // foreground for this long before showing the pill / counting absorbs that
    // flap. Only affects the first fraction of a second after (re)entry, so it
    // never blocks mid-session fast scrolling.
    private val ON_TRACKED_APP_PILL_STABLE_MS = 100L

    // Package name of our own app — when this is foreground, the gate
    // is neutral (neither "off tracked" nor "on tracked"). The user
    // navigating through our own UI shouldn't affect the shield state.
    private val OWN_PACKAGE = "com.scrollnumb.app"

    private val knownLaunchers = setOf(
        "com.nothing.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3"
    )

    private fun isLauncherPackage(pkg: String?): Boolean =
        pkg != null && (pkg in knownLaunchers || pkg.contains("launcher"))

    // Timestamp of the last poll pass where rootInActiveWindow returned a
    // usable node with a non-null package name. Used to detect when the
    // user has left the tracked app but no window-state event fires
    // (e.g. gesture navigation on some devices). If root stays unusable
    // for >500ms while currentPlatform is non-empty, the shield is
    // force-closed.
    private var lastUsableRootTime: Long = 0L

    // BrainPal-style foreground truth: the package of the most recent
    // accessibility event, plus when it arrived. On gesture-nav devices the
    // windows list / rootInActiveWindow stay pinned to the just-left app, but
    // the EVENT stream always reflects the real foreground app (the launcher
    // fires its own WINDOW_STATE_CHANGED on a home swipe). findForegroundPackage
    // prefers this over the stale window scan.
    @Volatile private var lastEventPackage: String? = null
    @Volatile private var lastEventTime: Long = 0L
    private val EVENT_PACKAGE_FRESH_MS = 1200L


    // Throttle for UsageStatsManager queries (gesture-nav cross-check).
    // queryEvents() is a binder call so we limit it to once per cycle.
    private var lastUsageStatsCheck: Long = 0L
    private val USAGE_STATS_CHECK_INTERVAL_MS = 1500L

    // Timestamp of the last accessibility event that came from the tracked app
    // itself. A foregrounded reel/Short fires continuous CONTENT_CHANGED events
    // (video progress, etc.); once it is backgrounded — including the gesture-nav
    // case where the launcher fires NO window-state event and the root stays
    // pinned to the app — those events stop. When this goes quiet we treat a
    // leave as likely and poll UsageStats far more aggressively so teardown
    // happens in ~1s instead of riding the 1.5s throttle + lag (~3.7s linger).
    @Volatile private var lastTrackedAppEventTime: Long = 0L
    private val TRACKED_APP_QUIET_MS = 500L
    // No tracked-app events for this long + not tracking → polling goes
    // dormant (no root fetches, 2s heartbeat) until a tracked event wakes it.
    private val DORMANT_AFTER_QUIET_MS = 3_000L
    private val USAGE_STATS_FAST_INTERVAL_MS = 300L

    // Gesture-nav pinned-root guard. After UsageStats confirms the user left a
    // tracked app, some launchers (Nothing) keep rootInActiveWindow pinned to
    // that app's now-background view tree, which would re-arm tracking and make
    // the pill flicker on the home screen until Android releases the tree. While
    // this holds the leaving package, a pinned root for it is ignored until
    // UsageStats re-confirms the app is genuinely foreground again.
    private var pinnedAwayPkg: String = ""

    // Once-per-day heartbeat: the day the service is active gets a stats row, so
    // the streak worker can distinguish a measured (under-limit) day from a day
    // the service never ran.
    private var lastEnsuredStatsDate = ""
    private suspend fun ensureTodayStatsRowIfNeeded() {
        val today = dateKeyFormat.format(Date())
        if (today == lastEnsuredStatsDate) return
        lastEnsuredStatsDate = today
        runCatching { scrollRepository.ensureTodayStatsRow() }
    }

    private fun startPollingEngine() {
        pollingJob?.cancel()
        // Adaptive poll interval:
        //   60ms (40ms on Battery Saver) — when a scroll intent is queued (user
        //         just swiped) → fast count response. Battery Saver slows each
        //         scan, so the shorter sleep keeps the two-pass confirmation at
        //         roughly the same wall-clock latency instead of ~2x late.
        //  300ms  — otherwise (idle on reel or not on tracked app) → 50% less main-thread work
        // This removes the constant 150ms UI-thread hammering that caused jank
        // while still responding quickly the moment the user actually swipes.
        pollingJob = serviceScope.launch(Dispatchers.Default) {
            while (isActive) {
                val dormant = currentPlatform.isEmpty() &&
                    System.currentTimeMillis() - lastTrackedAppEventTime > DORMANT_AFTER_QUIET_MS
                val intervalMs = when {
                    scrollEventQueued -> if (powerSaveMode) 35L else 50L
                    dormant -> 2_000L
                    else -> 300L
                }
                // Sleep the interval, but wake immediately on a pollTrigger poke.
                kotlinx.coroutines.withTimeoutOrNull(intervalMs) { pollTrigger.receive() }
                if (trackingEnabled) {
                    ensureTodayStatsRowIfNeeded()
                    pollScreenState()
                }
            }
        }
    }

    private data class ReelSurfaceState(
        val isActive: Boolean,
        val signature: String = "",
        val panelOpen: Boolean = false,
        val containerPresent: Boolean = false
    )

    private fun detectCurrentReelSurface(
        rootNode: AccessibilityNodeInfo,
        platform: String
    ): ReelSurfaceState {
        // Root-only detection. The old windows loop here was DEAD CODE — the
        // service never requested flagRetrieveInteractiveWindows, so getWindows()
        // was always empty and only this root fallback ever ran (every verified
        // behavior today came from it). Now that the flag is on (for
        // findForegroundPackage), keeping the loop would double-scan the active
        // window's tree each pass for nothing.
        val containerPresent = ScrollDetector.isReelsContainerPresent(rootNode, platform)
        val rootDetection = ScrollDetector.detectVideoSignature(rootNode, platform)
        return when (rootDetection.type) {
            DetectionType.REELS_ACTIVE -> ReelSurfaceState(
                isActive = true,
                signature = rootDetection.signature ?: "",
                containerPresent = containerPresent
            )
            DetectionType.PANEL_OPEN -> ReelSurfaceState(isActive = true, signature = rootDetection.signature ?: "", panelOpen = true, containerPresent = containerPresent)
            DetectionType.NOT_REELS -> ReelSurfaceState(isActive = false, containerPresent = containerPresent)
        }
    }

    /**
     * Single unified counting path.  Checks the seen-video buffer, writes
     * the scroll to Room (source of truth), syncs [globalScrollCount] to the
     * DB-computed total, and fires haptics + scroll events + cap enforcement.
     *
     * @return true if the scroll was counted, false if it was already seen.
     */
    private suspend fun performCount(
        signature: String,
        identityKey: String = signature,
        prevIdentity: String = "",
        currentTime: Long = System.currentTimeMillis(),
        // Entry counts follow a TAP by design (opening a Short IS a click), so
        // the tap-drift suppression window must not veto them — it was holding
        // every entry +1 hostage for the full 2.5s window (device-verified).
        bypassTapSuppress: Boolean = false,
        // Set only after a stable identical signature, changed pager page, real
        // scroll event, and debounce have already proved a profile-reel swipe.
        pageAdvanceAuthorized: Boolean = false
    ): Boolean {
        // Backstop: never re-count the SAME reel still sitting on the SAME pager
        // page. Catches loop/reflow transients (a YouTube Short looping and
        // briefly surfacing a different "longest" content-desc, or an Instagram
        // author/caption flip) where the content signature wobbles but no real
        // swipe happened. Gated on the page being unchanged so it does NOT block
        // a genuine swipe to another reel by the same author (profile reels),
        // where the page index advances even though author+caption look alike.
        if (lastConsumedSignature.isNotEmpty() &&
            reelPageIndex == lastCountedPageIndex &&
            !isLikelyDifferentReel(lastConsumedSignature, signature)) {
            return false
        }
        // Also block if the signature is the same (or very similar) and the
        // last count was within the debounce window, even if the page index
        // changed. This catches two scenarios:
        //   1. YouTube Short loop: the recycler may advance toIndex as it
        //      loops, making reelPageIndex != lastCountedPageIndex.
        //   2. Instagram page-index flapping: during a transition the ViewPager
        //      briefly reports a different index, causing two counts for one swipe.
        if (lastConsumedSignature.isNotEmpty() &&
            System.currentTimeMillis() - lastScrollTime < Constants.SCROLL_DEBOUNCE_MS &&
            !isLikelyDifferentReel(lastConsumedSignature, signature)) {
            return false
        }
        // Instagram UI-interaction safety: if a caption/comment/like click
        // happened within the last 2 seconds and this count has no scroll
        // event backing it, reject it. This catches edge cases where the
        // interaction suppress window expired but the signature change from
        // the UI action (e.g. closing a comment sheet) arrives without a
        // genuine swipe.
        if (!bypassTapSuppress && System.currentTimeMillis() < instagramUiInteractionSuppressUntilMs) {
            return false
        }
        // Dedup key is page-aware: the same content at a new pager page is a new
        // reel (same-author profile runs), while a loop-back to a prior page
        // collapses to an already-seen identity.
        // ALWAYS also add the bare signature (without #page) so that page-index
        // flapping (same reel briefly reporting page N then N+1 during a single
        // transition) cannot create two different identity keys for the same reel.
        val bareIdentity = signature
        val shouldCount = seenVideosMutex.withLock {
            // Profile viewers can expose only an author-level signature. Bypass
            // bare dedup only after a user-driven pager transition was validated;
            // weak content changes, loops and panels never receive this authority.
            val newPageIsCountable = pageAdvanceAuthorized && identityKey != bareIdentity
            val bareIdentityIsCountable = newPageIsCountable ||
                (!seenVideosList.isLikelySameRecentVideo(currentPlatform, signature) &&
                    seenVideosList.checkSeenState(currentPlatform, bareIdentity) == SeenResult.COUNT)
            if (bareIdentityIsCountable) {
                // Bare signature not seen yet — this is a genuinely new reel.
                // Check the page-aware identity separately so same-author reels
                // with identical content-desc still count when the page advances.
                // This two-tier check ensures page flaps never bypass dedup.
                val pageAwareSeen = seenVideosList.checkSeenState(currentPlatform, identityKey)
                when {
                    pageAwareSeen == SeenResult.COUNT -> {
                        seenVideosList.add(currentPlatform, bareIdentity)
                        seenVideosList.add(currentPlatform, identityKey)
                        if (prevIdentity.isNotEmpty()) {
                            seenVideosList.add(currentPlatform, prevIdentity)
                        }
                        true
                    }
                    pageAwareSeen == SeenResult.SKIP_CURRENT_VIDEO -> {
                        // Same content at the same page — absolute duplicate.
                        false
                    }
                    else -> {
                        // SKIP_RECENTLY_COUNTED — seen before in session.
                        false
                    }
                }
            } else {
                false
            }
        }
        if (!shouldCount) return false

        // Room is the source of truth — persist first, then sync the
        // in-memory counter so they can never drift apart.
        // Pass the user's base limit so the day latches "exceeded" against the
        // limit active at this moment (not a later-changed value). Streak uses this.
        val newTotal = scrollRepository.recordScroll(currentPlatform, currentSessionId, dailyLimit)
        globalScrollCount = newTotal

        recentVideoMemory.recordVideo(currentPlatform, signature)
        if (awarenessChecksEnabled && !dayLocked && newTotal < effectiveDailyLimit) {
            checkAndTriggerAwarenessNudge(newTotal, effectiveDailyLimit)
        }

        triggerHaptic(BrainState.fromScrollCount(newTotal))

        scrollEvents.tryEmit(
            ScrollEvent(
                platform = currentPlatform,
                timestamp = currentTime,
                sessionId = currentSessionId
            )
        )

        if ((newTotal >= effectiveDailyLimit || newTotal >= 200) &&
            currentTime > passEndTime && shieldEnabled) {
            dayLocked = true
            dayLockedDate = dateKeyFormat.format(Date())
        }

        return true
    }

    // Runs on Dispatchers.Default (the polling engine's dispatcher). The heavy
    // accessibility node scan (window iteration + BFS in detectCurrentReelSurface)
    // therefore stays OFF the main thread, so it never competes with the floating
    // pill's own Compose render frames — this is the fix for pill/scroll jank.
    // Node *reads* are thread-safe; the only main-thread work (showFloatingWidget /
    // hideFloatingWidget / setCapShieldVisible) is internally posted to mainHandler.
    private suspend fun pollScreenState() = kotlinx.coroutines.withContext(Dispatchers.Default) {
        if (!trackingEnabled) return@withContext

        // ── Dormancy ─────────────────────────────────────────────────────
        // Off tracked apps, don't touch rootInActiveWindow at all: fetching it
        // forces whatever app is foreground to BUILD its accessibility tree
        // every pass — with FLAG_INCLUDE_NOT_IMPORTANT_VIEWS that visibly
        // janked unrelated apps. Tracked-app events (OS allowlist) poke the
        // poll and wake us on the same pass, so entry stays instant.
        if (currentPlatform.isEmpty() &&
            System.currentTimeMillis() - lastTrackedAppEventTime > DORMANT_AFTER_QUIET_MS &&
            floatingComposeView == null && !capShieldContentVisible) {
            // Keep the midnight rollover honest while dormant — otherwise the
            // first pass of a new day briefly evaluated cap/shield against
            // yesterday's count.
            val nowDormant = System.currentTimeMillis()
            if (nowDormant - lastDateRefreshMs >= DATE_REFRESH_INTERVAL_MS) {
                lastDateRefreshMs = nowDormant
                refreshCountForDateChange()
            }
            return@withContext
        }

        if (!shieldEnabled) {
            clearDayLock()
            manualBlockExpiryMs = 0L
            manualBlockMessage = ""
        }

        // ── Manual block check ───────────────────────────────────────────
        // If the user set a manual block from the settings UI, enforce it
        // regardless of the scroll count. The expiry timestamp (epoch-millis)
        // lives in manualBlockExpiryMs; 0 means no manual block active.
        if (manualBlockExpiryMs > 0) {
            if (System.currentTimeMillis() < manualBlockExpiryMs && shieldEnabled) {
                dayLocked = true
                dayLockedDate = dateKeyFormat.format(Date())
            } else {
                // Manual block expired — clear it. If the normal cap-based
                // lock isn't active either, hide the shield.
                manualBlockExpiryMs = 0L
                manualBlockMessage = ""
                manualBlockMessageVersion++
                if (dayLocked && globalScrollCount < effectiveDailyLimit) {
                    clearDayLock()
                }
            }
        }

        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            val overlayService = com.scrollnumb.app.service.BlockerOverlayService.instance
            val isShieldActivelyShowing = overlayService != null && BlockerOverlayService.isOverlayShowing && overlayService.isShieldContentVisible
            if (isShieldActivelyShowing) {
                // rootInActiveWindow is null because OUR shield overlay is focused
                // — that alone doesn't mean the user left. But if the real
                // foreground window is now the launcher / another app (e.g. the
                // user closed Instagram via gesture nav), drop the shield so it
                // doesn't stay stuck over the home screen.
                val fg = findForegroundPackage()
                if (fg != null && fg != OWN_PACKAGE) {
                    val fgPlatform = targetPackages[fg]
                    if (fgPlatform == null || !isPlatformEnabled(fgPlatform)) {
                        forceOffTracked("shield up but foreground=$fg not tracked — left app")
                        return@withContext
                    }
                }
                // Permission-independent teardown: in the normal locked-reel
                // state the tracked app stays the ACTIVE window, so its root is
                // non-null and we never reach here. A sustained null active-root
                // while the shield is up therefore means the user left (home /
                // gesture nav). This fires even when Usage Access isn't granted,
                // which the usageStats cross-check below silently can't.
                if (lastUsableRootTime > 0 &&
                    System.currentTimeMillis() - lastUsableRootTime > 700 &&
                    currentPlatform.isNotEmpty()
                ) {
                    forceOffTracked("shield up but active root null >700ms — left app")
                    return@withContext
                }
                val trackedPkg = when {
                    fg != null && targetPackages[fg] != null -> fg
                    lastKnownTrackedPackage.isNotEmpty() -> lastKnownTrackedPackage
                    currentPlatform.isNotEmpty() -> targetPackages.entries.firstOrNull { it.value == currentPlatform }?.key
                    else -> null
                }
                val now = System.currentTimeMillis()
                if (trackedPkg != null && now - lastUsageStatsCheck >= USAGE_STATS_CHECK_INTERVAL_MS) {
                    lastUsageStatsCheck = now
                    if (!isTrackedAppConfirmedByUsageStats(trackedPkg)) {
                        pinnedAwayPkg = trackedPkg
                        forceOffTracked("shield up but usageStats says $trackedPkg paused/stopped")
                    }
                }
                return@withContext
            }

            // rootInActiveWindow is null. On some devices the launcher /
            // system UI doesn't expose a view tree through accessibility,
            // so this stays null for as long as the user is on the home
            // screen. If we're still tracking a platform after 500ms of
            // consecutive null roots, the user has likely left the tracked
            // app — force the shield closed.
            if (lastUsableRootTime > 0 &&
                System.currentTimeMillis() - lastUsableRootTime > 500 &&
                currentPlatform.isNotEmpty()
            ) {
                forceOffTracked("root null for >500ms")
            }
            return@withContext
        }

        // Refresh date/cap state periodically (not every 150ms poll — SimpleDateFormat
        // allocation every 150ms adds measurable overhead). 5s is fast enough for
        // midnight rollover and cap-raise to take effect.
        val nowForRefresh = System.currentTimeMillis()
        if (nowForRefresh - lastDateRefreshMs >= DATE_REFRESH_INTERVAL_MS) {
            lastDateRefreshMs = nowForRefresh
            refreshCountForDateChange()
        }

        // ── Cap extension check ──────────────────────────────────────────
        // If BlockerOverlayService set a pending cap extension (user clicked
        // "Give me X more" in the timer-completed screen), apply it now
        // and clear the day lock so the user can scroll again. Also set a
        // 5-second grace window to let the reel's content finish loading
        // through the shield overlay, preventing the first non-empty
        // signature (caption loaded) from triggering a phantom count.
        if (pendingCapExtension > 0) {
            capExtension += pendingCapExtension
            pendingCapExtension = 0
            capExtensionGraceMs = System.currentTimeMillis() + 5000
            if (dayLocked) {
                // Keep wasInReelSurface and lastScreenSignature intact —
                // the grace window handles signature convergence, and
                // preserving these avoids phantom entry/first counts.
                dayLocked = false
                setCapShieldVisible(false)
                Log.d(TAG, "cap extended by $capExtension (effective limit=${effectiveDailyLimit}), day unlocked")
            }
        }

        // ── Foreground-app gate ──────────────────────────────────────────
        // Determine whether the foreground app is a tracked app. Resolve
        // the package from `rootInActiveWindow` first. If that returns
        // null/empty (which happens during Reel transitions, comment
        // panel animations, scroll flings, etc.), fall back to the last
        // known tracked package (`currentPlatform`). This way the gate
        // doesn't misfire on transient view-tree states.
        val rootPackage = rootNode.packageName?.toString()?.takeIf { it.isNotEmpty() }

        // Update the last-known-usable-root timestamp for any non-null
        // package (including our own app). This keeps the null-root
        // timeout disarmed while any app with a known package is visible.
        if (rootPackage != null) {
            lastUsableRootTime = System.currentTimeMillis()
        }

        // If we're on our own app, do nothing — leave the shield state
        // alone. Navigating our own UI must not blink the shield, fire
        // the off-tracked gate, or reset the in-flight timer.
        if (rootPackage == OWN_PACKAGE) {
            offTrackedAppSince = 0L
            onTrackedAppSince = 0L
            if (currentPlatform.isNotEmpty()) { forceOffTracked("entered own app") } else { hideFloatingWidget(); setCapShieldVisible(false) }
            return@withContext
        }

        // ── Null-package cross-check ─────────────────────────────────────
        // If rootPackage is null (the foreground app's root node has no
        // packageName), the fallback to currentPlatform below keeps the
        // shield visible even after the user has left the tracked app.
        // Cross-check against the window list: if no tracked app window
        // exists, the user has left — force off-tracked immediately.
        if (rootPackage == null) {
            val foregroundPkg = findForegroundPackage()
            if (foregroundPkg != null && foregroundPkg != OWN_PACKAGE) {
                val fgPlatform = targetPackages[foregroundPkg]
                if (fgPlatform == null || !isPlatformEnabled(fgPlatform)) {
                    forceOffTracked("rootPkg=null, fg=$foregroundPkg not tracked")
                    return@withContext
                }
            }
            // foregroundPkg is null or our own app — can't determine the
            // foreground app. The null-root timeout above will handle it
            // via the lastUsableRootTime check (500ms timeout).
        }

        // ── System-UI-pinned cross-check (gesture nav fix) ───
        // On some devices (Nothing, Pixel), rootInActiveWindow stays pinned to
        // the launcher or system UI (com.android.systemui, com.nothing.launcher)
        // even when user is in a tracked app via gesture nav. If rootPackage is
        // a system UI, cross-check with UsageStats using the last known tracked
        // platform to see if it's actually still foreground.
        val launchers = knownLaunchers
        val systemUis = setOf("com.android.systemui", "com.nothing.applocker") + launchers
        var effectivePlatform: String? = null
        val platformToCheck = currentPlatform.takeIf { it.isNotEmpty() } ?: lastKnownTrackedPlatform
        val packageToCheck = lastKnownTrackedPackage.takeIf { it.isNotEmpty() }
            ?: targetPackages.entries.firstOrNull { it.value == platformToCheck }?.key
        if (rootPackage != null && systemUis.contains(rootPackage) && platformToCheck.isNotEmpty()) {
            val now = System.currentTimeMillis()
            // Home launcher held steadily = user left the app. usageStats can't
            // tell (it excludes launchers), so use a short sustained timer to
            // tear down promptly without false-firing on transient gesture flashes.
            if (rootPackage in launchers) {
                if (launcherForegroundSince == 0L) launcherForegroundSince = now
                if (now - launcherForegroundSince >= LAUNCHER_HOME_THRESHOLD_MS) {
                    launcherForegroundSince = 0L
                    forceOffTracked("home launcher sustained ${LAUNCHER_HOME_THRESHOLD_MS}ms — left app")
                    return@withContext
                }
            } else {
                launcherForegroundSince = 0L
            }
            if (now - lastUsageStatsCheck >= USAGE_STATS_CHECK_INTERVAL_MS) {
                lastUsageStatsCheck = now
                if (packageToCheck == null || !isTrackedAppConfirmedByUsageStats(packageToCheck)) {
                    if (packageToCheck != null) pinnedAwayPkg = packageToCheck
                    forceOffTracked("sysUI pinned ($rootPackage), usageStats says $platformToCheck not foreground")
                    return@withContext
                }
            }
            effectivePlatform = platformToCheck
        } else {
            launcherForegroundSince = 0L
        }

        val activePackage: String? = rootPackage ?: lastKnownTrackedPackage.takeIf { it.isNotEmpty() }

        if (activePackage == null) {
            // Truly unknown — no fallback either. Skip the pass; the
            // next pass should resolve the package.
            return@withContext
        }

        val activePlatform = effectivePlatform ?: targetPackages[activePackage]
        if (activePlatform == null || !isPlatformEnabled(activePlatform)) {
            val now = System.currentTimeMillis()
            // INSTANT dormancy: if the foreground package is a definitively
            // known foreign app (not system UI / launcher / IME ambiguity),
            // tear everything down on this very pass — no grace window.
            val isDefinitelyForeignApp = rootPackage != null &&
                targetPackages[rootPackage] == null &&
                rootPackage !in systemUis &&
                rootPackage != "android" &&
                !rootPackage.contains("inputmethod")
            if (isDefinitelyForeignApp) {
                forceOffTracked("foreign app in front: $rootPackage — instant dormant")
                return@withContext
            }
            // Ambiguous root (system UI, launcher, null) — keep the short
            // sustained gate to absorb transient detection glitches.
            if (offTrackedAppSince == 0L) {
                Log.d(TAG, "gate: OFF tracked app detected, rootPkg=$rootPackage currentPlatform=$currentPlatform activePlatform=$activePlatform enabled=${if (activePlatform != null) isPlatformEnabled(activePlatform) else "n/a"}")
                offTrackedAppSince = now
            }
            if (onTrackedAppSince != 0L) {
                onTrackedAppSince = 0L
            }
            hideFloatingWidget()
            if (now - offTrackedAppSince >= OFF_TRACKED_APP_THRESHOLD_MS) {
                // Only do the teardown work (and log) when there is actually
                // something left to clear. Once we are fully off-tracked and
                // idle on the home screen, forceOffTracked has nothing to do —
                // re-firing it every ~2s flooded logcat (rolling real events out
                // of the buffer) and churned state for no reason. Re-arm the
                // window silently so we stay ready for the next real session.
                val hasStateToClear = currentPlatform.isNotEmpty() ||
                    floatingComposeView != null || capShieldContentVisible
                if (hasStateToClear) {
                    forceOffTracked("FIRE after ${now - offTrackedAppSince}ms off tracked app")
                } else {
                    offTrackedAppSince = now
                }
            }
            return@withContext
        }

        // ── BrainPal event-truth teardown (gesture-nav pinned-root leave) ──
        // The root still names a tracked app, but on gesture-nav the launcher
        // keeps rootInActiveWindow pinned to the just-left app's tree, so this
        // would otherwise keep the pill up for seconds. The accessibility EVENT
        // stream is authoritative: if the most recent WINDOW_STATE_CHANGED came
        // from a fresh, non-tracked package (the launcher fired its own on a
        // home swipe), the user has left — tear down immediately instead of
        // waiting for the null-root / UsageStats / sustained-launcher timers.
        // While genuinely watching a reel the last window-state package is the
        // tracked app itself, so this never false-fires; a stale (>1.2s) event
        // is ignored so an idle paused reel doesn't trip it.
        val freshEvtPkg = lastEventPackage
        if (freshEvtPkg != null &&
            freshEvtPkg != activePackage &&
            targetPackages[freshEvtPkg] == null &&
            System.currentTimeMillis() - lastEventTime < EVENT_PACKAGE_FRESH_MS) {
            val freshEvtIsLauncherOrSystem = freshEvtPkg in launchers ||
                freshEvtPkg == "com.android.systemui" ||
                freshEvtPkg == "android"
            if (freshEvtIsLauncherOrSystem) {
                if (launcherForegroundSince == 0L) launcherForegroundSince = System.currentTimeMillis()
                if (System.currentTimeMillis() - launcherForegroundSince >= LAUNCHER_HOME_THRESHOLD_MS) {
                    launcherForegroundSince = 0L
                    forceOffTracked("event pkg $freshEvtPkg foreground, root pinned to $activePackage — left")
                    return@withContext
                }
            } else {
                forceOffTracked("event pkg $freshEvtPkg foreground, root pinned to $activePackage — left")
                return@withContext
            }
        } else {
            launcherForegroundSince = 0L
        }

        // ── Pinned-root re-entry guard (gesture-nav home flicker fix) ──
        // After UsageStats confirmed the user left this exact package, the
        // launcher can keep rootInActiveWindow pinned to its background tree.
        // That stale root would re-arm tracking below and re-show the pill on
        // the home screen every poll until Android releases the tree. Keep it
        // torn down — re-verifying with UsageStats no more than once per
        // interval — until the app is confirmed foreground again.
        if (pinnedAwayPkg.isNotEmpty()) {
            if (rootPackage == pinnedAwayPkg && activePackage == pinnedAwayPkg) {
                // FAST re-entry path: a FRESH TYPE_WINDOW_STATE_CHANGED from this exact
                // package proves the user genuinely re-opened it — a gesture-nav PINNED
                // background tree fires NO new window-state events (only the launcher
                // does), so this can't false-clear the real pinned-leave case. Without
                // this, the guard suppressed the pill for a full UsageStats throttle
                // window (~1.5-3s) on every re-entry → the "pill 2-3s late" bug.
                val freshSelfEvent = lastEventPackage == pinnedAwayPkg &&
                    System.currentTimeMillis() - lastEventTime < EVENT_PACKAGE_FRESH_MS
                if (freshSelfEvent) {
                    Log.d(TAG, "pinnedAway cleared: fresh window-state event from $pinnedAwayPkg (genuine re-entry)")
                    pinnedAwayPkg = ""
                } else {
                    val nowChk = System.currentTimeMillis()
                    if (nowChk - lastUsageStatsCheck < USAGE_STATS_CHECK_INTERVAL_MS) {
                        hideFloatingWidget()
                        setCapShieldVisible(false)
                        return@withContext
                    }
                    lastUsageStatsCheck = nowChk
                    if (!isTrackedAppConfirmedByUsageStats(pinnedAwayPkg)) {
                        hideFloatingWidget()
                        setCapShieldVisible(false)
                        return@withContext
                    }
                    // UsageStats now confirms foreground — genuinely back in the app.
                    pinnedAwayPkg = ""
                }
            } else {
                // Root changed to a different package / fell back — the stale
                // pinned tree is gone, drop the suspicion.
                pinnedAwayPkg = ""
            }
        }

        // ── UsageStats cross-check (gesture-nav fix) ──────────────────
        // On devices with gesture navigation, rootInActiveWindow stays
        // pinned to the tracked app's view tree even after the user has
        // swiped home. UsageStatsManager.queryEvents() reveals the real
        // activity lifecycle: when the tracked app gets ACTIVITY_PAUSED
        // without a matching ACTIVITY_RESUMED, the user has left.
        // This is throttled to once per 1.5s while the app is actively firing
        // events (cheap, since queryEvents() is a binder call). But when the
        // tracked app has gone quiet (no in-app events for TRACKED_APP_QUIET_MS
        // — the gesture-nav pinned-root leave, where no launcher window-state
        // event fires either), poll every 350ms so teardown lands in ~1s
        // instead of the ~3.7s the 1.5s throttle + UsageStats lag produced.
        // NOTE: the old "app went event-quiet → poll UsageStats → tear down"
        // path was REMOVED (BrainPal-style: trust the event stream). On-device
        // it produced ONLY false "no longer foreground" teardowns — UsageStats
        // ingest lag on fresh entries plus background Google-app RESUMED
        // records — killing the pill mid-Spotlight (whose UI is event-quiet)
        // and mid-reel. Every genuine quit is already caught instantly by the
        // launcher WINDOW_STATE event, the foreign-app root check, or the
        // null-root timeout; the sysUI-pinned and shield-up branches keep
        // their own UsageStats cross-checks for their narrow cases.

        // Back on a tracked app — reset the sustained-period counter so a
        // future exit starts the count from zero.
        if (offTrackedAppSince != 0L) {
            Log.d(TAG, "gate: ON tracked app, reset offTrackedAppSince (was ${System.currentTimeMillis() - offTrackedAppSince}ms)")
        }
        offTrackedAppSince = 0L

        // Update last known tracked platform for system UI cross-check
        if (activePlatform.isNotEmpty()) {
            lastKnownTrackedPlatform = activePlatform
            if (targetPackages[activePackage] == activePlatform) {
                lastKnownTrackedPackage = activePackage
            }
        }

        // Set (or keep) the on-tracked timer. The shield is only re-shown
        // after the user has been on a tracked app for ON_TRACKED_APP_STABLE_MS
        // — this prevents a 1-frame flash when the user bounces between our
        // app and a tracked app (rootPackage == OWN_PACKAGE branch above
        // also resets onTrackedAppSince, so a bounce is fully absorbed).
        if (onTrackedAppSince == 0L) {
            onTrackedAppSince = System.currentTimeMillis()
            Log.d(TAG, "gate: ON tracked app, set onTrackedAppSince=$onTrackedAppSince")
        }

        if (activePlatform != currentPlatform) {
                        val hasPendingIntentForActivePlatform = scrollEventQueued
            Log.d(TAG, "currentPlatform changed: $currentPlatform -> $activePlatform")
            currentPlatform = activePlatform
            instagramCommentLatchUntilMs = 0L
            if (!hasPendingIntentForActivePlatform) {
                clearScrollEvent()
            }
        }

val reelSurfaceState = detectCurrentReelSurface(rootNode, currentPlatform)
        val isReelSurface = reelSurfaceState.isActive
        val isPanelOpen = reelSurfaceState.panelOpen ||
            (currentPlatform == Constants.PLATFORM_INSTAGRAM &&
                System.currentTimeMillis() < instagramCommentLatchUntilMs)

        val panelWasOpen = reelPanelOpen
        if (isPanelOpen && !panelWasOpen) {
            pageBeforePanel = reelPageIndex
        }
        reelPanelOpen = isPanelOpen
        if (isPanelOpen) {
            pendingSignature = ""
            pendingPrevSig = ""
            pendingPageIndex = Int.MIN_VALUE
            settleReconcileUntilMs = 0L
            clearScrollEvent()
        } else if (panelWasOpen) {
            // Restore the real reel page after the sheet's RecyclerView emitted
            // its own indexes, then adopt the underlying reel as the baseline.
            reelPageIndex = pageBeforePanel
            pageBeforePanel = Int.MIN_VALUE
            pendingSignature = ""
            pendingPrevSig = ""
            pendingPageIndex = Int.MIN_VALUE
            clearScrollEvent()
            reelSurfaceState.signature.takeIf { it.isNotEmpty() }?.let {
                lastScreenSignature = it
                prevPassSignature = it
            }
            instagramUiInteractionSuppressUntilMs = System.currentTimeMillis() + 800L
        }

        if (isReelSurface || isPanelOpen || reelSurfaceState.containerPresent) {
            lastReelSurfaceTime = System.currentTimeMillis()
        }

        // Treat the presence of the reel CONTAINER as "on a reel surface" for
        // the pill/shield even before the strict full-screen signature resolves.
        // On Instagram the clips_viewer_view_pager is in the tree during the
        // slide-up entry animation, but isFullScreenInstagramViewer (>=70% of
        // screen) fails until the animation finishes — which delayed the pill by
        // 2-3s. isReelsContainerPresent already excludes the home feed, so
        // triggering on it is safe and shows the pill promptly on entry.
        // The grace window is kept short (2s) to absorb reel-to-reel transition
        // gaps without keeping the pill visible on YT home after leaving Shorts.
        val isRecentlyReelSurface = isReelSurface ||
            reelSurfaceState.containerPresent ||
            (lastReelSurfaceTime > 0L && System.currentTimeMillis() - lastReelSurfaceTime < reelSurfaceGraceMs)

        // Pill should only show when we are actually on or very recently left a
        // reel surface. Do NOT use isRecentlyReelSurface here — the grace window
        // is only for the count/signature state, not for pill visibility. The pill
        // must disappear the moment the user leaves reels (otherwise it lingers
        // 5-6 seconds on YT home or IG profile).
        val shouldShowPill = isReelSurface || isPanelOpen
        // Quick-access pill: also show when the reel CONTAINER is present (even
        // before signature resolves). This handles the entry animation case for
        // Instagram profile reels.
        val shouldShowPillWithContainer = shouldShowPill || reelSurfaceState.containerPresent

        if (isRecentlyReelSurface) {
            // If the day is already locked, skip counting entirely. The shield
            // is re-shown at the end of this branch based on `dayLocked`.
            val capReached = globalScrollCount >= effectiveDailyLimit || globalScrollCount >= 200
            val nowMs = System.currentTimeMillis()
            if (capReached && nowMs > passEndTime && shieldEnabled) {
                dayLocked = true
                dayLockedDate = dateKeyFormat.format(Date())
            }
            // Never count while a panel (comments / share-"Send to" sheet) is
            // open — its slide animation fires a scroll event + signature change
            // that was being miscounted as a new reel. Also require the app to be
            // steadily foreground (not a leave-transition flap), so the swipe-up
            // gesture used to background the app can't phantom-count a reel.
            val stableOnTracked = onTrackedAppSince > 0L &&
                nowMs - onTrackedAppSince >= ON_TRACKED_APP_PILL_STABLE_MS
            val shouldCount = !dayLocked && !isPanelOpen && stableOnTracked

            // Counting runs in a labeled scope so its early-exits (IG UI-interaction
            // suppress, pending-count) skip ONLY the count — never the pill/shield
            // render below. Previously they returned from the whole poll, so a TAP
            // that entered reels (which arms the 2.5s IG suppress window) withheld the
            // pill for ~2.5s → the "pill 2-3s late on entry" bug.
            run countScope@{
            if (shouldCount) {
                val currentSignature = reelSurfaceState.signature
                val currentTime = System.currentTimeMillis()
                // Signature STABILITY gate: a count needs the same signature on
                // two consecutive poll passes. Mid-swipe the tree briefly shows
                // transient reads (next reel's author bleeding in, half-loaded
                // chrome) — a transient counted AND the settled reel counted,
                // producing +2 for one swipe. Fast flings keep each reel on
                // screen for several 80ms passes, so real swipes are unaffected.
                val sigStable = currentSignature.isNotEmpty() &&
                    currentSignature == prevPassSignature
                prevPassSignature = currentSignature

                // ── Surface entry + deliberate YouTube entry count ──
                // These run BEFORE the click-suppression check: the very tap
                // that OPENS a Short fires TYPE_VIEW_CLICKED and arms the 2.5s
                // suppression window, which was delaying the entry count (and
                // surface marking) by ~2.5-3s after the Short was already
                // playing. Suppression exists to block tap-induced SIGNATURE
                // DRIFT (sheets, caption expands) from counting — a fresh
                // surface entry is not drift.
                if (currentTime >= capExtensionGraceMs && !wasInReelSurface) {
                    // ── Just entered a reel surface (tapped a thumbnail, swiped into reels, etc.)
                    wasInReelSurface = true
                    surfaceEnteredAtMs = System.currentTimeMillis()
                    shieldShownThisSurface = false
                    lastSyntheticPageBumpMs = 0L
                    pendingSignature = ""
                    pendingPrevSig = ""
                    pendingPageIndex = Int.MIN_VALUE
                    clearScrollEvent()
                    if (isReelSurface) {
                        showFloatingWidget(currentPlatform)
                        startTimeTracking(currentPlatform)
                    }
                    // YouTube AND Instagram count the ENTRY reel deliberately
                    // (below, on the first readable signature) — tapping a reel
                    // open is watching it, and users expect the +1 right away
                    // rather than only after the first swipe/comment/pause. The
                    // old phantom-count bug that made IG entry-counting unsafe is
                    // now covered by the two-pass stability + sigAtLastLeave /
                    // seen-state reconciliation in the entry path. Only the
                    // YouTube cold-player PROVISIONAL branch stays YT-only.
                    entryCountPending = currentPlatform == Constants.PLATFORM_YOUTUBE ||
                        currentPlatform == Constants.PLATFORM_INSTAGRAM
                    provisionalEntryActive = false
                    lastScreenSignature = currentSignature
                    // Reset page index on entry so the first real swipe is always
                    // detected as a page advance. Without this, a stale reelPageIndex
                    // from a home-feed RecyclerView scroll would contaminate the
                    // baseline and suppress the first count on profile reels.
                    reelPageIndex = -1
                    // Seed the page baseline at the reel we entered on, so the first
                    // genuine swipe (page advance) counts but entry itself does not.
                    lastCountedPageIndex = reelPageIndex
                }

                // Count the Short the user just opened on the FIRST readable
                // signature — no two-pass stability wait. Stability guards
                // against mid-swipe transients, but an entry follows a tap, not
                // a swipe. A half-loaded "ch_x_" sig is still the right Short
                // (the baseline enriches in place). Runs at most once per
                // surface entry; expires if the tree never resolves a signature
                // (the first swipe then counts as before).
                if (entryCountPending) {
                    if (currentTime - surfaceEnteredAtMs > ENTRY_COUNT_WINDOW_MS) {
                        entryCountPending = false
                        provisionalEntryActive = false
                    } else if (currentPlatform == Constants.PLATFORM_YOUTUBE &&
                        currentSignature.isEmpty() && !provisionalEntryActive &&
                        currentTime - surfaceEnteredAtMs >= ENTRY_PROVISIONAL_AFTER_MS
                    ) {
                        // Cold player, still no signature: count NOW under a
                        // provisional identity so the +1 lands when the Short
                        // starts playing, not seconds later. The resumed-same-
                        // short case reconciles below (sig == sigAtLastLeave →
                        // rollback), so no fresh-session gate is needed.
                        val prov = "ytprov_$surfaceEnteredAtMs"
                        if (performCount(signature = prov, identityKey = prov, currentTime = currentTime, bypassTapSuppress = true)) {
                            provisionalEntryActive = true
                            lastScrollTime = currentTime
                            instagramUiInteractionSuppressUntilMs = 0L
                            clearScrollEvent()
                            Log.d(TAG, "COUNTING entry (provisional): #$globalScrollCount $currentPlatform")
                        }
                    } else if (currentSignature.isNotEmpty() && provisionalEntryActive) {
                        // Reconcile the provisional +1 against the resolved sig.
                        entryCountPending = false
                        provisionalEntryActive = false
                        val initialPageSettle = lastCountedPageIndex < 0 &&
                            currentTime - surfaceEnteredAtMs < 3_000L &&
                            !scrollEventUserDriven
                        if (!initialPageSettle && reelPageIndex != lastCountedPageIndex) {
                            // User already swiped off the entry short before its
                            // metadata resolved — this sig is the NEXT short.
                            // Count it normally; the provisional +1 stays as the
                            // entry short's count.
                            if (performCount(signature = currentSignature, identityKey = reelIdentity(currentSignature), currentTime = currentTime)) {
                                Log.d(TAG, "COUNTING post-provisional swipe: #$globalScrollCount")
                                lastScrollTime = currentTime
                                lastCountedPageIndex = reelPageIndex
                            }
                        } else {
                            val alreadySeen = currentSignature == sigAtLastLeave ||
                                seenVideosMutex.withLock {
                                    seenVideosList.isLikelySameRecentVideo(currentPlatform, currentSignature) ||
                                        seenVideosList.checkSeenState(currentPlatform, currentSignature) != SeenResult.COUNT
                                }
                            if (alreadySeen) {
                                // Re-opened a short already counted this session —
                                // the provisional +1 was wrong; roll it back.
                                scrollRepository.removeLastScroll(currentPlatform)
                                Log.d(TAG, "entry provisional ROLLBACK: signature already seen")
                            } else {
                                // Attribute the provisional count to the real sig
                                // so later swipes/loops dedup against it.
                                seenVideosMutex.withLock {
                                    seenVideosList.add(currentPlatform, currentSignature)
                                    seenVideosList.add(currentPlatform, reelIdentity(currentSignature))
                                }
                                Log.d(TAG, "entry provisional resolved")
                            }
                            lastCountedPageIndex = reelPageIndex
                            clearScrollEvent()
                        }
                        lastConsumedSignature = currentSignature
                        lastScreenSignature = currentSignature
                        return@countScope
                    } else if (currentSignature.isNotEmpty()) {
                        entryCountPending = false
                        if (currentSignature == sigAtLastLeave) {
                            // Returned to the same short they left mid-watch
                            // (app switch / home bounce) — already counted.
                            Log.d(TAG, "no entry count: resumed same short after leave")
                            lastScreenSignature = currentSignature
                            instagramUiInteractionSuppressUntilMs = 0L
                            clearScrollEvent()
                            return@countScope
                        }
                        if (performCount(signature = currentSignature, identityKey = reelIdentity(currentSignature), currentTime = currentTime, bypassTapSuppress = true)) {
                            Log.d(TAG, "COUNTING entry: #$globalScrollCount $currentPlatform page=$reelPageIndex")
                            lastScrollTime = currentTime
                            lastConsumedSignature = currentSignature
                            lastCountedPageIndex = reelPageIndex
                            instagramUiInteractionSuppressUntilMs = 0L
                            clearScrollEvent()
                        } else {
                            Log.d(TAG, "no entry count: already seen")
                        }
                        lastScreenSignature = currentSignature
                        return@countScope
                    }
                }

                if (currentTime < instagramUiInteractionSuppressUntilMs) {
                    // Adopt the drifted signature ONLY when it's still the same
                    // reel (caption expand/hide etc.). Blind adoption also ate a
                    // GENUINE swipe made within 2.5s of a tap — the new reel
                    // became the baseline and was never counted. Keeping the old
                    // baseline for a genuinely different reel lets the path count
                    // it the moment suppression expires.
                    if (currentSignature.isNotEmpty() &&
                        (lastScreenSignature.isEmpty() ||
                            !isLikelyDifferentReel(lastScreenSignature, currentSignature))) {
                        lastScreenSignature = currentSignature
                    }
                    pendingSignature = ""
                    pendingPrevSig = ""
                    clearScrollEvent()
                    return@countScope
                }

                // ── Check/Count pending signature if debounce has passed ──
                val pendingTransitionSettled = !scrollEventQueued ||
                    System.currentTimeMillis() - scrollEventTimestamp >= reelTransitionSettleMs
                val pendingMatches = pendingSignature.isNotEmpty() &&
                    pendingSignature == currentSignature
                val pendingDebouncePassed = System.currentTimeMillis() - lastScrollTime >
                    Constants.SCROLL_DEBOUNCE_MS
                if (pendingMatches && pendingTransitionSettled && pendingDebouncePassed) {
                    val nowTime = System.currentTimeMillis()
                    val pendIdentity = if (pendingPageIndex >= 0) "${currentSignature}#${pendingPageIndex}" else currentSignature
                    val pendPrevIdentity = if (lastCountedPageIndex != Int.MIN_VALUE)
                        "${pendingPrevSig}#${lastCountedPageIndex}" else pendingPrevSig
                    if (performCount(signature = currentSignature, identityKey = pendIdentity, prevIdentity = pendPrevIdentity, currentTime = nowTime)) {
                        Log.d(TAG, "COUNTING delayed pending: scroll #$globalScrollCount on $currentPlatform")
                        lastConsumedSignature = currentSignature
                        lastCountedPageIndex = pendingPageIndex
                    }
                    lastScrollTime = nowTime
                    pendingSignature = ""
                    pendingPrevSig = ""
                    pendingPageIndex = Int.MIN_VALUE
                    clearScrollEvent()
                    // Exit the counting scope (NOT the whole poll) so the pill/shield
                    // still renders. Skipping the normal count path below is what
                    // matters: continuing would set a NEW pending (lastScrollTime was
                    // just updated → isPastDebounce false) and fire a second count next
                    // poll — the old root cause of +2 per reel on Instagram.
                    return@countScope
                }

                // ── Cap-extension grace period ─────────────────────
                // After "Give me X more", skip counting for 5 seconds
                // to let the reel's content finish loading through the
                // shield overlay. Without this, the first non-empty
                // signature (caption loaded) differs from the stale
                // lastScreenSignature and causes phantom counts.
                val inGrace = currentTime < capExtensionGraceMs
                // Capture scroll intent BEFORE the expiry check below clears it.
                // Previously hasRecentScrollEvent was evaluated after clearScrollEvent()
                // could have already fired, making it always false on timeout boundary.
                val scrollEventAge = if (scrollEventQueued) currentTime - scrollEventTimestamp else Long.MAX_VALUE
                val hasRecentScrollEvent = scrollEventQueued &&
                    scrollEventPlatform == currentPlatform &&
                    scrollEventAge <= scrollEventTimeoutMs
                if (!hasRecentScrollEvent && scrollEventQueued) {
                    clearScrollEvent()
                }

                // ── Fast path: count NOW on the highest-confidence swipe ──
                // Counting via the pending/settle path below costs the 180ms
                // transition settle + the two-pass stability wait (~250ms after
                // the swipe). In ONE case that wait buys no accuracy: a genuine
                // strong swipe (a real scroll event, not an inferred/loop
                // scroll) that advanced the REAL pager page (not the synthetic
                // counter) to a genuinely DIFFERENT reel, while no comment panel
                // is open and we're past the between-swipes debounce. A loop,
                // caption tap, comment scroll, or auto-advance can never satisfy
                // all of those at once. The only residual risk — the pass-1
                // signature being a mid-swipe transient — is caught AFTER the
                // fact by the settle-reconcile window (below), which re-attributes
                // the settled signature to this same count instead of adding a
                // second. Everything less certain falls through to the
                // conservative path unchanged.
                val fpPrevSig = lastScreenSignature
                val fpSigIsNewReel = currentSignature.isNotEmpty() &&
                    fpPrevSig.isNotEmpty() && currentSignature != fpPrevSig &&
                    isLikelyDifferentReel(fpPrevSig, currentSignature)
                // Page advanced (real ViewPager index OR the synthetic per-swipe
                // counter — YouTube Shorts uses the synthetic one). Synthetic is
                // safe HERE because the fast path ALSO requires fpSigIsNewReel: a
                // same-author run (IG profile reels / single-channel Shorts, the
                // case the synthetic counter exists for) has an identical sig, so
                // fpSigIsNewReel is false and it correctly falls to the
                // conservative path. Only a genuinely different reel fast-counts.
                val fpPageAdvance = reelPageIndex >= 0 &&
                    lastCountedPageIndex >= 0 && reelPageIndex != lastCountedPageIndex
                // scrollEventStrong (a real TYPE_VIEW_SCROLLED / onGesture, not an
                // inferred content-change) is the proven "genuine swipe" signal
                // this fast path was device-verified on. scrollEventUserDriven
                // (scrollDeltaY>0) is a stronger hint when present but is not set
                // on every surface, so it can't be REQUIRED here or the fast path
                // never fires — the real-page-advance + different-reel gates are
                // what actually guarantee this is a genuinely new reel.
                val fpUserSwipe = hasRecentScrollEvent && scrollEventStrong
                if (!inGrace && !reelPanelOpen && fpUserSwipe && fpPageAdvance &&
                    fpSigIsNewReel &&
                    currentTime - lastScrollTime > Constants.SCROLL_DEBOUNCE_MS
                ) {
                    val eventToCountMs = currentTime - scrollEventTimestamp
                    clearScrollEvent()
                    pendingSignature = ""
                    pendingPrevSig = ""
                    pendingPageIndex = Int.MIN_VALUE
                    val fastIdentity = reelIdentity(currentSignature)
                    val fastPrevIdentity = if (lastCountedPageIndex != Int.MIN_VALUE)
                        "${fpPrevSig}#${lastCountedPageIndex}" else fpPrevSig
                    if (performCount(
                            signature = currentSignature,
                            identityKey = fastIdentity,
                            prevIdentity = fastPrevIdentity,
                            currentTime = currentTime
                        )) {
                        Log.d(TAG, "COUNTING fast: #$globalScrollCount $currentPlatform page=$reelPageIndex +${eventToCountMs}ms")
                        lastScrollTime = currentTime
                        lastConsumedSignature = currentSignature
                        lastCountedPageIndex = reelPageIndex
                        // 1200ms (was 2500): long enough to re-attribute the
                        // pass-1 transient's settle, short enough that the
                        // auto-intent suppression it implies can't strand a
                        // no-scroll-event crossfade to the NEXT reel for long
                        // (the code-review undercount concern on synthetic pages).
                        settleReconcileUntilMs = currentTime + 1_200L
                        settleReconcilePage = reelPageIndex
                        settleReconcilePlatform = currentPlatform
                    }
                    lastScreenSignature = preferRicherBaseline(fpPrevSig, currentSignature)
                    return@countScope
                }

                // A single physical swipe emits a burst of pager events. Wait
                // for a short quiet period so only the final page/signature is
                // consumed; this is separate from the between-swipes debounce.
                if (hasRecentScrollEvent && scrollEventAge < reelTransitionSettleMs) {
                    return@countScope
                }
                
                if (inGrace && currentSignature.isNotEmpty()) {
                    lastScreenSignature = currentSignature
                }

                // Two independent "new reel" signals authorise a count:
                //   (1) the content signature changed to a genuinely different reel
                //       (different author / channel / caption), or
                //   (2) the reel pager advanced to a new PAGE. (2) is the only
                //       reliable signal when consecutive reels share author+caption
                //       — scrolling one person's profile reels, where every reel is
                //       "Reel by <sameAuthor>" with no per-reel id in the tree.
                val prevSig = lastScreenSignature
                val sigChanged = currentSignature.isNotEmpty() && currentSignature != prevSig
                val sigIsNewReel = sigChanged && prevSig.isNotEmpty() &&
                    isLikelyDifferentReel(prevSig, currentSignature)
                if (sigChanged && !sigStable) {
                    // First pass showing a changed signature = possibly a
                    // mid-swipe transient. Touch NOTHING (baseline, pending,
                    // queued intent all stay) and re-evaluate next pass — if
                    // it's a real reel it re-reads identically ~80ms later.
                    return@countScope
                }
                // ── Fast-path settle reconciliation ─────────────────────
                // A stable sig change on the same page the fast count fired
                // on, with no new scroll intent, is the fast-counted swipe's
                // tree finishing its settle (the pass-1 read was a transient).
                // Adopt it as the counted reel's true identity — counting it
                // would be the old +2-per-swipe bug the stability gate fixed.
                // Window stays armed until expiry (no disarm on first hit): a
                // slow tree can settle in steps (transient → partial → final),
                // and every step must re-attribute — if a later step leaked to
                // the auto-intent path it would be the +2 bug again. A genuine
                // NEW reel inside the window always moves the pager page or
                // carries a scroll intent, so it can't be swallowed here.
                if (sigChanged && currentTime < settleReconcileUntilMs &&
                    reelPageIndex == settleReconcilePage &&
                    currentPlatform == settleReconcilePlatform
                ) {
                    clearScrollEvent()
                    seenVideosMutex.withLock {
                        seenVideosList.add(currentPlatform, currentSignature)
                        seenVideosList.add(currentPlatform, reelIdentity(currentSignature))
                    }
                    lastConsumedSignature = currentSignature
                    lastScreenSignature = currentSignature
                    Log.d(TAG, "fast-count settle re-attributed")
                    return@countScope
                }
                // Entry settle: as the reel surface loads, the pager fires a
                // TYPE_VIEW_SCROLLED reporting its INITIAL page. With no counted
                // page yet (baseline -1 from entry) that read as a page advance
                // and +1-counted just OPENING reels. Shortly after entry, while
                // the content is still the entry reel, adopt the reported page
                // as the baseline instead of counting it.
                if (reelPageIndex >= 0 && lastCountedPageIndex < 0 && !sigIsNewReel &&
                    System.currentTimeMillis() - surfaceEnteredAtMs < 3_000L) {
                    lastCountedPageIndex = reelPageIndex
                    clearScrollEvent()
                }
                // Require a real (non-empty) signature even for the page-advance path.
                // An empty signature means detection couldn't read the reel (a transient
                // during comment-close / a fast transition); counting it produced a
                // phantom "sig=" count when only the pager page advanced. sigIsNewReel
                // already requires a non-empty sig, so this just closes the page path.
                val swipedToNewPage = reelPageIndex >= 0 && reelPageIndex != lastCountedPageIndex &&
                    currentSignature.isNotEmpty()

                if (prevSig.isEmpty() && sigChanged) {
                    // Resumed mid-reel with no baseline — adopt it as seen, no count.
                    seenVideosMutex.withLock {
                        seenVideosList.add(currentPlatform, reelIdentity(currentSignature))
                    }
                    lastScreenSignature = currentSignature
                    lastCountedPageIndex = reelPageIndex
                } else if (prevSig.isNotEmpty() && (sigIsNewReel || swipedToNewPage)) {
                    val isPastDebounce = currentTime - lastScrollTime > Constants.SCROLL_DEBOUNCE_MS

                    // Slow-scroll auto-intent: a content change that looks like a new
                    // reel but arrived with no queued scroll event (slow crossfade).
                    // The page-advance path always has a real swipe behind it, so
                    // only the signature path needs this.
                    // YouTube exception: when the channel key is MISSING on either
                    // side, the sig rests on unanchored descs (title not loaded /
                    // popup-only transients) — too unreliable to self-authorize.
                    // A same-channel title change with real keys is a genuine
                    // swipe through one channel's Shorts (profile runs) and must
                    // stay auto-intent eligible: those swipes often arrive with
                    // no usable TYPE_VIEW_SCROLLED, which is why "profile Shorts
                    // don't count" when this was gated on the key being same.
                    // The comment-popup phantom this gate originally targeted is
                    // now excluded structurally in extractYouTubeChannelAndTitle
                    // (title must sit BELOW the channel row).
                    val ytEmptyKeyDrift = currentPlatform == Constants.PLATFORM_YOUTUBE &&
                        (prevSig.split("_", limit = 3).getOrNull(1).isNullOrEmpty() ||
                            currentSignature.split("_", limit = 3).getOrNull(1).isNullOrEmpty())
                    // While a fast count's settle window is armed and no FRESH
                    // scroll intent exists, every no-intent signal here is the
                    // fast-counted swipe's own aftermath — its tree settling
                    // and/or the pager index flapping across the transition
                    // (which defeats the exact-page reconcile match above).
                    // Letting those self-authorize (auto-intent) or count via
                    // the page path would re-create the +2-per-swipe bug. A
                    // genuine next swipe always queues a fresh intent (which
                    // falsifies this flag) — only sub-debounce flicks lose a
                    // count here, exactly as the debounce already enforced.
                    val settleWindowActive = currentTime < settleReconcileUntilMs &&
                        currentPlatform == settleReconcilePlatform &&
                        !hasRecentScrollEvent
                    val autoIntentEligible = sigIsNewReel && !ytEmptyKeyDrift && !settleWindowActive
                    if (!hasRecentScrollEvent && isPastDebounce && autoIntentEligible) {
                        queueScrollEvent(currentPlatform)
                    }
                    val effectiveHasScrollEvent = scrollEventQueued &&
                        scrollEventPlatform == currentPlatform &&
                        (currentTime - scrollEventTimestamp) <= scrollEventTimeoutMs

                    // A page advance is itself proof of a real swipe, so it counts on
                    // its own. The signature path still requires a scroll intent so a
                    // pure metadata change can't count. Caption/comment/profile taps
                    // don't advance the pager page AND fire a TYPE_VIEW_CLICKED that
                    // arms the suppression window, so they can't leak a count here.
                    // Exception: when the page advances but the content signature is
                    // the SAME as the last counted reel, require a scroll event AND
                    // a debounce check. This blocks YouTube Short loop counts (loop
                    // fires a scroll with advancing recycler position but same video)
                    // and Instagram page-index flapping (same content at page N vs N+1
                    // during transition animation causes +2).
                    val sameContentNewPage = swipedToNewPage && !sigIsNewReel &&
                        prevSig.isNotEmpty() && !isLikelyDifferentReel(prevSig, currentSignature)
                    // The same-content-new-page clause requires prevSig ==
                    // currentSignature (a TRUE identical-sig page advance —
                    // profile reels). A caption expand/collapse GROWS/SHRINKS
                    // the sig (author → author_caption) while its scrollable
                    // sheet fires scroll events with fresh page indexes, which
                    // this clause was accepting → +1 on tapping the caption.
                    val identicalContentPageAdvance = sameContentNewPage &&
                        prevSig == currentSignature
                    val pageAdvanceAuthorized = identicalContentPageAdvance &&
                        effectiveHasScrollEvent && scrollEventStrong && scrollEventUserDriven
                    val authorizes = !settleWindowActive && sigStable && (
                        (effectiveHasScrollEvent && sigIsNewReel) ||
                        (swipedToNewPage && !sameContentNewPage) ||
                        (pageAdvanceAuthorized && isPastDebounce)
                    )
                    if (isPastDebounce) {
                        if (effectiveHasScrollEvent) clearScrollEvent()
                        if (authorizes) {
                            val identity = reelIdentity(currentSignature)
                            val prevIdentity = if (lastCountedPageIndex != Int.MIN_VALUE)
                                "${prevSig}#${lastCountedPageIndex}" else prevSig
                            if (performCount(
                                    signature = currentSignature,
                                    identityKey = identity,
                                    prevIdentity = prevIdentity,
                                    currentTime = currentTime,
                                    pageAdvanceAuthorized = pageAdvanceAuthorized
                                )) {
                                Log.d(TAG, "COUNTING: #$globalScrollCount $currentPlatform page=$reelPageIndex")
                                lastScrollTime = currentTime
                                lastConsumedSignature = currentSignature
                                lastCountedPageIndex = reelPageIndex
                            } else {
                                Log.d(TAG, "no count: already seen")
                            }
                        }
                        lastScreenSignature = preferRicherBaseline(prevSig, currentSignature)
                    } else {
                        // Debounce blocking — defer to pending only with a real intent.
                        if (authorizes) {
                            pendingSignature = currentSignature
                            pendingPrevSig = prevSig
                            pendingPageIndex = reelPageIndex
                        }
                        lastScreenSignature = preferRicherBaseline(prevSig, currentSignature)
                    }
                } else if (sigChanged) {
                    // Signature drifted but it's the SAME reel (caption load / expand /
                    // collapse) and the page didn't move → never count, just advance
                    // the baseline.
                    lastScreenSignature = preferRicherBaseline(prevSig, currentSignature)
                }
            } else {
                // Day is locked — mark that we're on a reel surface so the
                // final-state block below knows to show the shield, but don't
                // count or touch lastScreenSignature.
                wasInReelSurface = true
                // Save the current screen as already-seen so that when the
                // day unlocks (cap extension) the reel the user was already
                // watching isn't re-counted as a new scroll.
                val idleSig = reelSurfaceState.signature
                if (idleSig.isNotEmpty()) {
                    lastScreenSignature = idleSig
                    seenVideosMutex.withLock {
                        seenVideosList.add(currentPlatform, idleSig)
                    }
                }
            }
            } // end countScope

            // ── Final state for reel surface: shield if locked, pill otherwise
            if (dayLocked) {
                hideFloatingWidget()
                // Only show the shield after the user has been stable on a
                // tracked app for ON_TRACKED_APP_STABLE_MS. Prevents the
                // 1-frame flash when the user bounces between our app and
                // a tracked app (rootPackage == OWN_PACKAGE branch above
                // also resets onTrackedAppSince, so a bounce is fully
                // absorbed).
                val stableFor = nowMs - onTrackedAppSince
                if (stableFor >= ON_TRACKED_APP_STABLE_MS) {
                    setCapShieldVisible(true)
                } else {
                    Log.d(TAG, "shield: stable-tracked not yet (${stableFor}ms < ${ON_TRACKED_APP_STABLE_MS}ms), not showing")
                }
            } else {
                // Gate the pill on the same short stability window so it doesn't
                // flash during a leave-transition flap (off→on→off). Mid-session
                // onTrackedAppSince is old, so this only delays the pill by a
                // fraction of a second on genuine (re)entry.
                val stableForPill = onTrackedAppSince > 0L &&
                    nowMs - onTrackedAppSince >= ON_TRACKED_APP_PILL_STABLE_MS
                val pillShouldShow = shouldShowPillWithContainer && stableForPill
                if (pillShouldShow) {
                    if (floatingComposeView == null) {
                        Log.d(TAG, "pill: SHOW (isReel=$isReelSurface, panel=$isPanelOpen, recently=$isRecentlyReelSurface)")
                    }
                    showFloatingWidget(currentPlatform)
                } else {
                    if (floatingComposeView != null) {
                        Log.d(TAG, "pill: HIDE (isReel=$isReelSurface, panel=$isPanelOpen, recently=$isRecentlyReelSurface, lastReelTime=${lastReelSurfaceTime})")
                    }
                    hideFloatingWidget()
                }
                setCapShieldVisible(false)
            }

        } else {
            // Not on a reel surface. If wasInReelSurface was true, we're leaving.
            // Only clear if this is a GENUINE leave (not a transient tree reflow
            // during a reel-to-reel transition). Use isPanelOpen + grace window to
            // distinguish: a transient gap between reels is absorbed, but a
            // sustained absence (>2s) is a real leave.
            if (!isPanelOpen && !isRecentlyReelSurface && lastReelSurfaceTime > 0 && wasInReelSurface) {
                val timeSinceLastReel = System.currentTimeMillis() - lastReelSurfaceTime
                // Only clear after the grace window expired — this prevents transient
                // tree reflows during reel-to-reel from resetting state and causing
                // double-counts. The grace IS short enough (2s) that a profile visit
                // (where you tap through to a profile from a reel) clears state before
                // the profile page content loads and triggers a false count.
                if (timeSinceLastReel >= reelSurfaceGraceMs) {
                    Log.d(TAG, "leave reel surface: grace expired (${timeSinceLastReel}ms)")
                    if (lastScreenSignature.isNotEmpty()) sigAtLastLeave = lastScreenSignature
                    wasInReelSurface = false
                    entryCountPending = false
                    lastScreenSignature = ""
                    lastConsumedSignature = ""
                    reelPageIndex = -1
                    lastCountedPageIndex = Int.MIN_VALUE
                    lastSyntheticPageBumpMs = 0L
                    pendingPageIndex = Int.MIN_VALUE
                    pendingSignature = ""
                    pendingPrevSig = ""
                    clearScrollEvent()
                }
            }
            hideFloatingWidget()
            setCapShieldVisible(false)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // SAFETY NET: when tracking is off, the polling engine is not running
        // and is the only thing that can drive the shield. Hide it here so
        // the user can still use the underlying app.
        if (!trackingEnabled) {
            hideFloatingWidget()
            setCapShieldVisible(false)
            return
        }
        if (event == null) return

        val rootNode = rootInActiveWindow
        val eventPackage = event.packageName?.toString()
        val rootPackage = rootNode?.packageName?.toString()

        // Record the foreground signal from the event stream (BrainPal `S`/`T`).
        // Only TYPE_WINDOW_STATE_CHANGED marks a real foreground-window change —
        // recording content/scroll events would let a gesture-nav-pinned tree's
        // background CONTENT_CHANGED events overwrite the true foreground (the
        // launcher after a home swipe). Ignore transient system overlays so a
        // notification / IME flash doesn't masquerade as the foreground app.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            !eventPackage.isNullOrEmpty() &&
            eventPackage != "com.android.systemui" &&
            eventPackage != "android" &&
            eventPackage != OWN_PACKAGE &&
            !eventPackage.contains("inputmethod") &&
            !eventPackage.contains("applocker") &&
            // Credible foreground signals only: a tracked app, a launcher (the
            // home-swipe signal), or the package that actually owns the active
            // window. With the OS allowlist gone, background services (Google
            // app, widget hosts) fire WINDOW_STATE_CHANGED without ever taking
            // the screen — recording those poisoned foreground truth and tore
            // the pill down in the middle of a reel.
            (targetPackages[eventPackage] != null ||
                isLauncherPackage(eventPackage) ||
                eventPackage == rootPackage)) {
            lastEventPackage = eventPackage
            lastEventTime = System.currentTimeMillis()
        }

        // For TYPE_WINDOW_STATE_CHANGED, trust the event's packageName over
        // rootInActiveWindow. After gesture navigation on some devices (Nothing,
        // Pixel), rootInActiveWindow stays pinned to the old app's view tree,
        // but the event correctly reflects the new foreground app.
        val activePackage = if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            eventPackage ?: rootPackage
        } else {
            rootPackage ?: eventPackage
        } ?: return
        val platform = targetPackages[activePackage]

        // Liveness signal: note any event coming from a tracked app. When these
        // stop (app backgrounded via gesture-nav with a pinned root), the gate
        // polls UsageStats aggressively to tear down fast.
        if (platform != null) {
            lastTrackedAppEventTime = System.currentTimeMillis()
            // Pill-entry latency: if the pill isn't up yet (user just entered
            // the app / a reel surface), run a poll pass immediately.
            if (floatingComposeView == null) {
                pokePolling()
            } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                (entryCountPending || platform == Constants.PLATFORM_SNAPCHAT)
            ) {
                // Count-latency: a content change is exactly when a pending
                // YouTube entry Short's title resolves, or a Snapchat Spotlight
                // swipe's new creator text lands — poll NOW instead of up to
                // 300ms later. Rate-limited so Spotlight's chatty overlay
                // updates can't turn the poll loop into a busy spin.
                val nowPoke = System.currentTimeMillis()
                if (nowPoke - lastEventPokeMs >= 100L) {
                    lastEventPokeMs = nowPoke
                    pokePolling()
                }
            }
        }

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
            platform != null &&
            platform != Constants.PLATFORM_SNAPCHAT &&
            isPlatformEnabled(platform)) {
            val clickedSource = try { event.source } catch (e: Exception) { null }
            val clickedViewId = clickedSource?.viewIdResourceName?.lowercase(Locale.ROOT).orEmpty()
            val clickedDescription = clickedSource?.contentDescription?.toString()
                ?.lowercase(Locale.ROOT).orEmpty()
            clickedSource?.recycle()
            val isActiveInstagramReel = platform == Constants.PLATFORM_INSTAGRAM &&
                currentPlatform == Constants.PLATFORM_INSTAGRAM &&
                ScrollDetector.isReelNodeActive(rootInActiveWindow, platform)
            if (isActiveInstagramReel &&
                (clickedViewId.contains("comment") || clickedDescription.contains("comment"))) {
                instagramCommentLatchUntilMs = System.currentTimeMillis() + 1_500L
                if (!reelPanelOpen) pageBeforePanel = reelPageIndex
                reelPanelOpen = true
                pendingSignature = ""
                pendingPrevSig = ""
                pendingPageIndex = Int.MIN_VALUE
                clearScrollEvent()
                pokePolling()
            }
            // A click on any reel surface is a UI action (comment/like/caption/
            // share/audio/follow) — never a scroll. Arm suppression for 2s so
            // the signature change from the opening sheet (caption expand, comment
            // sheet slide, etc.) is not counted on any platform.
            // SNAPCHAT is exempt: it fires TYPE_VIEW_CLICKED for plain swipe
            // touches, so this window was adding a 1-2.5s lag to every Spotlight
            // count (device-verified 2026-07-05). Its tap surfaces are already
            // excluded structurally — reply/comment sheets detect as PANEL_OPEN
            // and profile taps leave the Spotlight surface (NOT_REELS).
            instagramUiInteractionSuppressUntilMs = System.currentTimeMillis() + 2500L
            pendingSignature = ""
            pendingPrevSig = ""
            clearScrollEvent()
        }

        val canHandleReelScroll = platform != null && isPlatformEnabled(platform) &&
            !reelPanelOpen && System.currentTimeMillis() >= instagramUiInteractionSuppressUntilMs
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && canHandleReelScroll &&
            // During the post-tap suppression window, scrolls are sheet/expand
            // animations — they must not bump the page index either (the page
            // bump previously slipped through even though the intent didn't).
            isVerticalReelScroll(event)) {
            lastItemIndex = event.toIndex
            lastScrollY = event.scrollY
            if (isMainReelScrollEvent(event, platform)) {
                // Capture the pager page this swipe settled on. This is the
                // per-reel identity the counter needs when consecutive reels
                // share author+caption (profile reels). toIndex is the page for
                // a ViewPager and the last-visible adapter position for a
                // RecyclerView (YouTube Shorts) — both advance one step per
                // swipe. Ignore -1 (no index reported) so we never pollute the
                // page with a sentinel.
                val toIdx = event.toIndex
                if (toIdx >= 0) {
                    reelPageIndex = toIdx
                    reelPageIsSynthetic = false
                } else {
                    // Many surfaces fire main-reel scroll events with toIndex == -1,
                    // so the real pager page is unavailable. Without a per-reel signal,
                    // a RUN of one author's/channel's reels all share author+caption and
                    // never count — Instagram PROFILE reels and YouTube SINGLE-CHANNEL
                    // shorts both hit this. Synthesize a monotonic page from each genuine
                    // swipe: a real main-reel scroll bumps it, a loop (which fires no such
                    // event) does not — so the existing sameContentNewPage path (which
                    // still requires a real scroll event + debounce) counts each swipe
                    // once and never counts a loop. Varied feeds are unaffected: a
                    // different author/channel already counts via the signature path, and
                    // the page advancing in lock-step never adds a second count (debounce).
                    //
                    // A single fling fires a BURST of scroll events; bump at most once
                    // per debounce window so the synthetic page advances exactly one
                    // step per physical swipe (mirroring a real toIndex), never multiple
                    // times mid-fling (which would double-count).
                    val nowMs = System.currentTimeMillis()
                    if (nowMs - lastSyntheticPageBumpMs > Constants.SCROLL_DEBOUNCE_MS) {
                        reelPageIndex = if (reelPageIndex < 0) 0 else reelPageIndex + 1
                        reelPageIsSynthetic = true
                        lastSyntheticPageBumpMs = nowMs
                    }
                }
                // Queue every validated main-pager event. Some profile viewers
                // report a stale/repeated toIndex; signature + debounce dedup the
                // burst, while settle reconciliation absorbs late same-page events.
                val userDriven = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                    kotlin.math.abs(event.scrollDeltaY) > 0
                queueScrollEvent(platform, activePackage, userDriven = userDriven)
            }
        }

        // ── Slow-scroll ViewPager transition detection ────────────────
        // On slow flings, Instagram's ViewPager2 / YouTube's RecyclerView
        // may not fire TYPE_VIEW_SCROLLED. Instead, the system fires
        // TYPE_WINDOW_CONTENT_CHANGED from the pager container. Detect
        // that and queue a scroll intent so the polling engine can count
        // the reel transition.
        val canInferReelScroll = platform != null && isPlatformEnabled(platform) &&
            !reelPanelOpen && !scrollEventQueued
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            canInferReelScroll &&
            System.currentTimeMillis() - lastScrollTime > 1000L) {
            val source = try { event.source } catch (e: Exception) { null }
            val viewId = source?.viewIdResourceName ?: ""
            val className = source?.className?.toString()?.lowercase(Locale.ROOT) ?: ""
            val isUiInteraction = isInstagramCaptionOrControlSource(viewId, className)
            source?.recycle()
            val isReelContainer = when (platform) {
                Constants.PLATFORM_INSTAGRAM -> listOf(
                    "clips_viewer_view_pager",
                    "reel_viewer_root",
                    "reel_main_container",
                    "reel_viewer_media_container",
                    "reel_viewer_media_layout",
                    "reel_viewer_texture_view",
                    "reel_media_view",
                    "video_view"
                ).any { viewId.contains(it) }
                Constants.PLATFORM_YOUTUBE -> viewId.contains("reel_recycler") ||
                    viewId.contains("reel_player_page") ||
                    viewId.contains("reel_watch_pager")
                else -> false
            } || className.contains("viewpager") || className.contains("recyclerview")
            if (isReelContainer && wasInReelSurface && !isUiInteraction) {
                // Weak intent: inferred from a content change, not a real swipe.
                // It will only be allowed to count if the reel actually changed.
                queueScrollEvent(platform, activePackage, strong = false)
            }
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            // No shield toggling here. The polling engine is the sole driver.
            // Accessibility events only update bookkeeping (currentPlatform,
            // start/stop time tracking, etc.) and the floating pill.

            if (activePackage == "com.android.systemui" ||
                activePackage == "android" ||
                activePackage == OWN_PACKAGE ||
                activePackage.contains("applocker") ||
                activePackage.contains("inputmethod")) {
                // OWN_PACKAGE: adding/removing our own pill/shield overlay fires
                // WINDOW_STATE_CHANGED from us — with the allowlist gone those
                // events arrive here and must not read as "user left the app"
                // (they caused a show→teardown→show flap every ~600ms). Genuinely
                // opening our app is handled by the poll's rootPackage check.
                return
            }

            if (platform == null) {
                if (currentPlatform.isNotEmpty()) {
                    val foregroundPkg = findForegroundPackage()
                    if (foregroundPkg != null && targetPackages[foregroundPkg] != null) {
                        return
                    }
                }
                // With the packageNames allowlist gone, launcher/home events reach
                // this branch — that's the INSTANT teardown on quit. Only trust
                // the event when it's a launcher or the package genuinely owns
                // the active window: background services (Google app, widgets)
                // fire events without taking the screen and must not tear the
                // pill down mid-reel. And only tear down when there's actually
                // state/UI to clear (launcher content changes fire while idle
                // on the home screen).
                val credibleLeave = isLauncherPackage(activePackage) || activePackage == rootPackage
                if (credibleLeave &&
                    (currentPlatform.isNotEmpty() || floatingComposeView != null || capShieldContentVisible)) {
                    // Arm the pinned-root guard BEFORE tearing down. On gesture-nav
                    // the root stays pinned to the just-left app, so the very next
                    // poll re-armed tracking — with the day cap latched that
                    // re-SHOWED the shield over the home screen until UsageStats
                    // caught up (the 2-3s shield-quit linger).
                    if (isLauncherPackage(activePackage)) {
                        val leavingPkg = lastKnownTrackedPackage.takeIf { it.isNotEmpty() }
                            ?: targetPackages.entries.firstOrNull { it.value == currentPlatform }?.key
                        if (leavingPkg != null) pinnedAwayPkg = leavingPkg
                    }
                    forceOffTracked("event: $activePackage not tracked")
                }
            } else {
                val isPlatformEnabled = isPlatformEnabled(platform)
                if (isPlatformEnabled) {
                    if (platform != currentPlatform) {
                        val hasPendingIntentForPlatform = scrollEventQueued
                        if (currentPlatform.isNotEmpty()) {
                            stopTimeTracking()
                        }
                        currentPlatform = platform
                        lastKnownTrackedPackage = activePackage
                        lastKnownTrackedPlatform = platform
                        currentSessionId = java.util.UUID.randomUUID().toString()
                        serviceScope.launch {
                            seenVideosMutex.withLock { seenVideosList.clear() }
                        }
                        lastItemIndex = -1
                        lastScrollY = -1
                        if (!hasPendingIntentForPlatform) {
                            clearScrollEvent()
                        }
                        if (!hasPendingIntentForPlatform) {
                            // Treat the new app entry as "not yet in a reel surface"
                            // so the polling engine re-evaluates cleanly.
                            wasInReelSurface = false
                            lastScreenSignature = ""
                            lastConsumedSignature = ""
                            shieldShownThisSurface = false
                        }
                    }
                } else {
                    hideFloatingWidget()
                }
            }
        }

        // Click events only arm the short UI-interaction suppression above.
    }

    override fun onGesture(gestureId: Int): Boolean {
        if (!trackingEnabled) return super.onGesture(gestureId)
        val isVerticalSwipe = gestureId == GESTURE_SWIPE_UP || gestureId == GESTURE_SWIPE_DOWN
        if (!isVerticalSwipe) return super.onGesture(gestureId)

        val packageName = findForegroundPackage()
            ?: rootInActiveWindow?.packageName?.toString()
            ?: lastKnownTrackedPackage.takeIf { it.isNotEmpty() }
        val platform = packageName?.let { targetPackages[it] }
            ?: currentPlatform.takeIf { it.isNotEmpty() }
            ?: lastKnownTrackedPlatform.takeIf { it.isNotEmpty() }

        if (platform != null && !reelPanelOpen &&
            System.currentTimeMillis() >= instagramUiInteractionSuppressUntilMs) {
            queueScrollEvent(platform, packageName, userDriven = true)
        }
        return super.onGesture(gestureId)
    }

    private fun isPlatformEnabled(platform: String): Boolean {
        return when (platform) {
            Constants.PLATFORM_INSTAGRAM -> trackInstagram
            Constants.PLATFORM_YOUTUBE -> trackYoutube
            Constants.PLATFORM_FACEBOOK -> trackFacebook
            Constants.PLATFORM_SNAPCHAT -> trackSnapchat
            Constants.PLATFORM_LINKEDIN -> trackLinkedin
            Constants.PLATFORM_TIKTOK -> trackTiktok
            else -> false
        }
    }

    private fun showFloatingWidget(platform: String) {
        // Always perform view creation on the main thread (Compose + WindowManager
        // require it). Posts are serialized, so the null-guard below is race-free.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showFloatingWidget(platform) }
            return
        }
        if (floatingComposeView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.x = 0
        // Push below the notch/status bar in DP-scaled pixels: a raw 120px was
        // ~57dp on low-density phones but only ~27dp on 1440p flagships, where
        // the pill collided with the camera cutout. Use the real status-bar
        // height when the OS reports it, else a 48dp fallback.
        val statusBarPx = resources.getIdentifier("status_bar_height", "dimen", "android")
            .takeIf { it > 0 }?.let { resources.getDimensionPixelSize(it) } ?: 0
        val fallbackPx = (48 * resources.displayMetrics.density).toInt()
        params.y = maxOf(statusBarPx + (8 * resources.displayMetrics.density).toInt(), fallbackPx)
        savedPillX = params.x
        savedPillY = params.y
        floatingParams = params

        val fView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(this@ScrollAccessibilityService)
            setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)

            setContent {
                val themeName by userPreferences.appTheme.collectAsState(initial = "DARK")
                val effectiveTheme = themeName
                ScrollNumbTheme(darkTheme = true, themeName = effectiveTheme) {
                    val count by globalScrollCountFlow.collectAsState()
                    val isAwarenessPromptVisible by isAwarenessPromptVisibleFlow.collectAsState()
                    var limit by remember { mutableStateOf(dailyLimit) }

                    LaunchedEffect(dailyLimit) {
                        limit = dailyLimit
                    }

                    // Bouncy scale-in animation
                    var pillVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { pillVisible = true }
                    val pillScale by animateFloatAsState(
                        targetValue = if (pillVisible) 1f else 0f,
                        animationSpec = spring(
                            dampingRatio = 0.5f,
                            stiffness = 300f
                        ),
                        label = "pillBounce"
                    )

                    // ── Milestone celebration: every 10 reels the pill pops ──
                    val milestonePulse = remember { androidx.compose.animation.core.Animatable(1f) }
                    var milestoneFlash by remember { mutableStateOf(false) }
                    LaunchedEffect(count) {
                        if (count > 0 && count % 10 == 0) {
                            milestoneFlash = true
                            milestonePulse.animateTo(
                                1.22f,
                                spring(dampingRatio = 0.35f, stiffness = 900f)
                            )
                            milestonePulse.animateTo(
                                1f,
                                spring(dampingRatio = 0.45f, stiffness = 280f)
                            )
                            milestoneFlash = false
                        }
                    }
                    val flashBorder by animateFloatAsState(
                        targetValue = if (milestoneFlash) 1f else 0f,
                        animationSpec = tween(220),
                        label = "milestoneFlash"
                    )

                    val progress = if (limit > 0) (count.toFloat() / limit).coerceIn(0f, 1f) else 0f
                    // The pill floats OVER OTHER APPS on its own dark surface, so
                    // its colors are FIXED brand values, not theme-palette slots.
                    // In the light/paper themes Saffron is a deep sage and Cream a
                    // dark olive — palette slots made the digits dark-on-dark
                    // (invisible) the moment the overlay faded mid-scroll.
                    val backgroundColor = androidx.compose.ui.graphics.Color(0xE6121212)
                    val pillGold = androidx.compose.ui.graphics.Color(0xFFFFD642)
                    val saffronOverlayColor = pillGold.copy(alpha = 1f - progress)
                    // Digit color SNAPS ink→cream at 0.68 — never crossfades. A
                    // crossfade must pass through mid-GRAY, and the saffron
                    // overlay passes through mid-AMBER near half progress; the
                    // two luminances met around ~46% (e.g. 23/50) and the count
                    // all but vanished (device screenshot 2026-07-12). Ink stays
                    // ≥~4.9:1 on the blend up to 0.68, cream is ≥~3.3:1 from
                    // 0.68 on and climbs — no trough at any progress. The count
                    // only changes between discrete scrolls, so a snap never
                    // visibly "flickers" mid-animation.
                    val digitsCream = progress >= 0.68f
                    val contentColor = if (digitsCream)
                        androidx.compose.ui.graphics.Color(0xFFF5F0E8)
                    else
                        androidx.compose.ui.graphics.Color(0xFF14110C)
                    val shadowColor = if (digitsCream)
                        androidx.compose.ui.graphics.Color(0x66000000)
                    else
                        androidx.compose.ui.graphics.Color.Transparent
                    val baseBorder = androidx.compose.ui.graphics.lerp(
                        androidx.compose.ui.graphics.Color.Transparent,
                        androidx.compose.ui.graphics.Color(0x33FFFFFF),
                        progress
                    )
                    // Milestone flash overrides the border with bright saffron
                    val borderColor = androidx.compose.ui.graphics.lerp(
                        baseBorder,
                        pillGold,
                        flashBorder
                    )

                    key(isAwarenessPromptVisible) {
                        if (isAwarenessPromptVisible) {
                            AwarenessNudgeCard(
                                reelCount = count,
                                onDismiss = { setAwarenessPopupVisible(false) },
                                onTakeBreak = {
                                    setAwarenessPopupVisible(false)
                                    navigateToRelaxationFromOverlay()
                                },
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .pointerInput(Unit) {
                                        // Accumulate drag in floats — truncating each frame's
                                        // delta to Int drops sub-pixel movement and makes slow
                                        // drags visibly stutter.
                                        var accX = 0f
                                        var accY = 0f
                                        detectDragGestures(
                                            onDragStart = {
                                                accX = params.x.toFloat()
                                                accY = params.y.toFloat()
                                            }
                                        ) { change, dragAmount ->
                                            change.consume()
                                            accX += dragAmount.x
                                            accY += dragAmount.y
                                            params.x = accX.toInt()
                                            params.y = accY.toInt()
                                            savedPillX = params.x
                                            savedPillY = params.y
                                            try {
                                                windowManager.updateViewLayout(this@apply, params)
                                            } catch (e: Exception) {
                                            }
                                        }
                                    }
                                    .graphicsLayer {
                                        scaleX = pillScale * milestonePulse.value
                                        scaleY = pillScale * milestonePulse.value
                                    }
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(backgroundColor)
                                    .background(saffronOverlayColor)
                                    .border(if (milestoneFlash) 2.dp else 1.dp, borderColor, RoundedCornerShape(999.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val context = LocalContext.current
                                    val avatarResId = remember(count, limit) {
                                        AvatarHelper.getScrollStatusAvatar(context, progress)
                                    }
                                    // Avatar wrapped in a thin progress ring — fills
                                    // sage → coral as the user approaches the cap. Fixed
                                    // brand values (see pill color note above).
                                    val ringColor = androidx.compose.ui.graphics.lerp(
                                        androidx.compose.ui.graphics.Color(0xFF5BC0A0),
                                        androidx.compose.ui.graphics.Color(0xFFFF6B4A),
                                        progress
                                    )
                                    Box(contentAlignment = Alignment.Center) {
                                        androidx.compose.foundation.Canvas(
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            val stroke = 2.5.dp.toPx()
                                            drawArc(
                                                color = androidx.compose.ui.graphics.Color(0x26FFFFFF),
                                                startAngle = -90f,
                                                sweepAngle = 360f,
                                                useCenter = false,
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                            )
                                            drawArc(
                                                color = ringColor,
                                                startAngle = -90f,
                                                sweepAngle = 360f * progress,
                                                useCenter = false,
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                            )
                                        }
                                        Image(
                                            painter = com.scrollnumb.app.core.designsystem.components.avatarPainter(id = avatarResId),
                                            contentDescription = "Avatar Icon",
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                // Zoom into the FACE: the avatar art is a
                                                // head-and-shoulders bust, so at 30dp the
                                                // face (and its emotion) was too small to
                                                // read. Scale up inside the circle clip and
                                                // shift down so the head fills the circle.
                                                .graphicsLayer {
                                                    scaleX = 1.4f
                                                    scaleY = 1.4f
                                                    translationY = size.height * 0.18f
                                                },
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    com.scrollnumb.app.core.designsystem.components.AnimatedOdometer(
                                        count = count,
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = contentColor,
                                            letterSpacing = (-0.2).sp,
                                            shadow = androidx.compose.ui.graphics.Shadow(
                                                color = shadowColor,
                                                offset = androidx.compose.ui.geometry.Offset(0f, 1f),
                                                blurRadius = 3f
                                            )
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        try {
            windowManager.addView(fView, params)
            floatingComposeView = fView
        } catch (e: Exception) {
            Log.e(TAG, "addView(floatingWidget) failed", e)
        }
    }

    private fun hideFloatingWidget() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideFloatingWidget() }
            return
        }
        isAwarenessPromptVisibleFlow.value = false
        floatingComposeView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            floatingComposeView = null
        }
    }

    private fun setAwarenessPopupVisible(visible: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { setAwarenessPopupVisible(visible) }
            return
        }
        if (isAwarenessPromptVisibleFlow.value == visible) return
        val params = floatingParams ?: return
        val view = floatingComposeView ?: return
        try {
            if (visible) {
                params.gravity = Gravity.CENTER
                params.x = 0
                params.y = 0
            } else {
                params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                params.x = savedPillX
                params.y = savedPillY
            }
            windowManager.updateViewLayout(view, params)
            isAwarenessPromptVisibleFlow.value = visible
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update layout for awareness popup: ${e.message}")
        }
    }

    private fun checkAndTriggerAwarenessNudge(count: Int, limit: Int) {
        if (count < 3 || count >= limit) return
        val now = System.currentTimeMillis()
        // Minimum 5 minutes between awareness checks
        if (now - lastAwarenessPromptTimeMs < 5 * 60 * 1000L) return
        if (isAwarenessPromptVisibleFlow.value) return

        val halfLimit = maxOf(3, limit / 2)
        val threeQuarterLimit = maxOf(4, (limit * 0.75f).toInt())

        val isMilestone = count == halfLimit || 
                          count == threeQuarterLimit || 
                          (count >= 15 && count % 15 == 0)

        if (isMilestone && count != lastAwarenessPromptCount) {
            lastAwarenessPromptCount = count
            lastAwarenessPromptTimeMs = now
            setAwarenessPopupVisible(true)
        }
    }

    private fun navigateToRelaxationFromOverlay() {
        val intent = Intent(this, com.scrollnumb.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "relaxation")
        }
        startActivity(intent)
    }

    private fun triggerBlockerOverlay() {
        val intent = Intent(this, com.scrollnumb.app.service.BlockerOverlayService::class.java)
        startService(intent)
    }

    /**
     * Force the shield closed and reset the current platform. Called when
     * the polling engine determines the user has left the tracked app.
     */
    private fun forceOffTracked(reason: String) {
        Log.d(TAG, "gate: $reason, forcing off tracked")
        if (lastScreenSignature.isNotEmpty()) sigAtLastLeave = lastScreenSignature
        currentPlatform = ""
        lastKnownTrackedPackage = ""
        lastKnownTrackedPlatform = ""
        lastReelSurfaceTime = 0L
        wasInReelSurface = false
        lastScreenSignature = ""
        prevPassSignature = ""
        reelPageIndex = -1
        lastCountedPageIndex = Int.MIN_VALUE
        lastSyntheticPageBumpMs = 0L
        reelPanelOpen = false
        pageBeforePanel = Int.MIN_VALUE
        instagramCommentLatchUntilMs = 0L
        pendingPageIndex = Int.MIN_VALUE
        clearScrollEvent()
        stopTimeTracking()
        hideFloatingWidget()
        setCapShieldVisible(false)
        // Reset the gate timers. Without this, offTrackedAppSince stayed armed
        // and forceOffTracked re-fired on EVERY poll (logcat showed it firing
        // ~3x/sec for 18s straight). Clearing onTrackedAppSince also makes a
        // genuine re-entry wait the full ON_TRACKED_APP_STABLE_MS before the
        // shield re-shows, so it no longer blinks back the instant the user
        // bounces in and out of the app.
        offTrackedAppSince = 0L
        onTrackedAppSince = 0L
        launcherForegroundSince = 0L
    }

    /**
     * Cross-check with UsageStatsManager whether the given package is still
     * the foreground app. On gesture-nav devices, rootInActiveWindow stays
     * pinned to the old app — this reveals the real activity lifecycle.
     *
     * Returns false if the target package was paused/stopped after its last
     * resume, or if a non-launcher, non-tracked app resumed after it.
     * Launcher RESUMED events alone are ignored because on gesture navigation
     * the launcher can appear as RESUMED while the tracked app is still running.
     */
    private fun isTrackedAppConfirmedByUsageStats(packageName: String): Boolean {
        try {
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 30_000
            val events = usageStatsManager.queryEvents(startTime, endTime) ?: return true
            val event = android.app.usage.UsageEvents.Event()
            var lastResumedForTarget = false
            var targetCurrentlyResumed: Boolean? = null
            var otherAppResumedAfterTargetResumed = false
            // Timestamp of the decisive NEGATIVE evidence (target paused /
            // another app resumed). If the target fired accessibility events
            // AFTER that moment, the UsageStats picture is stale or poisoned
            // (cold-start ingest lag; background Google-app RESUMED records) —
            // it false-tore the Spotlight pill down while the user watched.
            var negativeEvidenceTs = 0L
            val knownLaunchers = setOf("com.nothing.launcher", "com.google.android.apps.nexuslauncher", "com.android.launcher3")
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val eventPackage = event.packageName ?: continue
                if (eventPackage == packageName) {
                    if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED ||
                        event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        lastResumedForTarget = true
                        targetCurrentlyResumed = true
                    } else if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED ||
                        event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND ||
                        event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED) {
                        targetCurrentlyResumed = false
                        negativeEvidenceTs = event.timeStamp
                    }
                } else if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED &&
                    !eventPackage.startsWith("com.android.") &&
                    eventPackage != "android" &&
                    eventPackage != OWN_PACKAGE &&
                    !knownLaunchers.contains(eventPackage) &&
                    !eventPackage.contains("applocker") &&
                    targetPackages[eventPackage] == null) {
                    // Another non-tracked, non-launcher app resumed
                    if (lastResumedForTarget) {
                        otherAppResumedAfterTargetResumed = true
                        negativeEvidenceTs = event.timeStamp
                    }
                }
            }
            // Event-truth override: our own accessibility events from the
            // tracked app NEWER than the negative UsageStats evidence prove the
            // app is alive in front of the user — the UsageStats verdict is
            // stale (ingest lag) or poisoned (background RESUMED records).
            val negative = targetCurrentlyResumed == false || otherAppResumedAfterTargetResumed
            if (negative) {
                return lastTrackedAppEventTime > negativeEvidenceTs
            }
            // If we never saw target RESUMED in window, assume still foreground
            // (could be window too short or events dropped)
            if (!lastResumedForTarget) {
                return true
            }
            // Target's last event was RESUMED = still foreground
            return true
        } catch (e: Exception) {
            Log.d(TAG, "usageStats cross-check failed: $e")
        }
        return true
    }

    /**
     * Iterate the current window list to find the foreground app's package
     * name. Skips system UI and input method windows. Returns null if no
     * app with a known package is found (e.g. the launcher has no
     * accessibility tree on some devices).
     */
    private fun findForegroundPackage(): String? {
        // BrainPal-style: trust the most recent accessibility event's package
        // first. On gesture-nav the windows list below stays pinned to the
        // just-left app's tree, so a fresh event package (e.g. the launcher
        // after a home swipe) is the only reliable foreground signal. The
        // event package is already filtered of systemUI/IME/own-app noise.
        val evtPkg = lastEventPackage
        if (evtPkg != null && System.currentTimeMillis() - lastEventTime < EVENT_PACKAGE_FRESH_MS) {
            return evtPkg
        }
        for (window in windows) {
            // Only the ACTIVE window (the one the user is currently
            // interacting with / has input focus) counts as foreground. A
            // paused app's window lingers in the list but is NOT active —
            // returning it made the shield believe the user was still in the
            // tracked app after they pressed home, so it never tore down.
            if (!window.isActive) continue
            val root = window.root ?: continue
            val pkgName = root.packageName?.toString()?.takeIf { it.isNotEmpty() }
            if (pkgName == null) {
                root.recycle()
                continue
            }
            root.recycle()
            if (pkgName == "com.android.systemui" ||
                pkgName == "android" ||
                pkgName == OWN_PACKAGE ||
                pkgName.contains("inputmethod") ||
                pkgName.contains("applocker")) {
                continue
            }
            return pkgName
        }
        return null
    }

    /**
     * Show or hide the cap shield overlay. The polling engine is the SOLE
     * caller — accessibility events MUST NOT toggle the shield directly
     * (they used to, and that's what caused the visible blink). The
     * underlying service is now idempotent so calling this every 150ms
     * with the same value is cheap.
     */
    private fun setCapShieldVisible(visible: Boolean) {
        if (!visible && !capShieldContentVisible) return
        val overlayService = com.scrollnumb.app.service.BlockerOverlayService.instance
        if (visible && capShieldContentVisible && overlayService != null) return

        Log.d(TAG, "setCapShieldVisible($visible) dayLocked=$dayLocked instance=${overlayService != null}")
        if (visible) {
            if (overlayService == null) {
                capShieldContentVisible = false
                triggerBlockerOverlay()
                return
            }
            capShieldContentVisible = true
            overlayService.setShieldContentVisible(true)
        } else {
            capShieldContentVisible = false
            overlayService?.setShieldContentVisible(false)
        }
    }

    /**
     * Clear [dayLocked] and hide the shield. Does NOT reset tracking
     * state ([wasInReelSurface], [lastScreenSignature]) — the day-locked
     * pass already maintains them at the current reel's values, so no
     * phantom first-entry count fires on the same poll pass.
     * Call this everywhere [dayLocked] transitions from true → false.
     */
    private fun clearDayLock() {
        dayLocked = false
        // Drop any accumulated "Give me 10 more" extensions. Without this they
        // leaked past the unlock — most importantly across a new day (the
        // date-change path calls clearDayLock but never reset capExtension), so
        // yesterday's extensions silently raised today's limit. The cap-extension
        // apply path clears the lock by setting dayLocked=false directly (not via
        // clearDayLock), so an in-progress extension is preserved.
        capExtension = 0
        setCapShieldVisible(false)
    }

    /**
     * A reel pager only advances on a VERTICAL swipe. Resting a thumb on a
     * Short's left/right edge, dragging the scrubber, or a horizontal comment/
     * related carousel all fire TYPE_VIEW_SCROLLED too — and those were being
     * counted (and, via the synthetic page bump, re-counted in a loop while held)
     * on YouTube. When the source reports pixel deltas (API 28+), reject any
     * scroll whose horizontal movement dominates. Sources that don't report
     * deltas (dx==dy, including the UNDEFINED/0 case) are treated as vertical so
     * index-based pagers (which report movement via from/toIndex, not pixels)
     * keep counting exactly as before.
     */
    private fun isVerticalReelScroll(event: AccessibilityEvent): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val dx = kotlin.math.abs(event.scrollDeltaX)
            val dy = kotlin.math.abs(event.scrollDeltaY)
            if (dx > dy) return false
        }
        return true
    }

    private fun isMainReelScrollEvent(event: AccessibilityEvent, platform: String): Boolean {
        // NOTE: the old windows-based PANEL_OPEN pre-check was removed — the
        // service never requested flagRetrieveInteractiveWindows, so getWindows()
        // was always empty and the check silently never ran. Sheets/panels are
        // now rejected structurally below (partial-height containers), which
        // also covers obfuscated-id platforms the id checks can't.
        val className = event.className?.toString() ?: ""
        val source = try { event.source } catch (e: Exception) { null }
        val viewId = source?.viewIdResourceName ?: ""
        val sourceBounds = android.graphics.Rect()
        source?.getBoundsInScreen(sourceBounds)
        source?.recycle()

        val lowerClass = className.lowercase(Locale.ROOT)

        // 1. Known main reel scroll container IDs — immediate TRUE
        if (viewId.isNotEmpty()) {
            if (platform == Constants.PLATFORM_YOUTUBE && (
                    viewId.contains("reel_recycler") ||
                    viewId.contains("reel_player_page") ||
                    viewId.contains("reel_watch_pager")
                )) return true
            if (platform == Constants.PLATFORM_INSTAGRAM && listOf(
                    "clips_viewer_view_pager",
                    "reel_viewer_root",
                    "reel_main_container",
                    "reel_viewer_media_container",
                    "reel_viewer_media_layout",
                    "reel_viewer_texture_view",
                    "reel_media_view",
                    "video_view"
                ).any { viewId.contains(it) }) {
                return true
            }
            if (platform == Constants.PLATFORM_INSTAGRAM && listOf(
                    "profile_picture", "avatar", "caption", "like_button", "comment"
                ).any { viewId.contains(it) }) {
                return false
            }
        }

        // 1b. Structural sheet rejection: real reel pagers are (near) full-screen;
        //     comment/caption/share SHEETS are partial-height. A sheet's scrolls
        //     bumped the pager index with an identical content signature, which
        //     authorized a "same content, new page" count → +1 when tapping or
        //     closing the caption. Height gating (instead of the previous blanket
        //     IG RecyclerView reject) keeps Instagram PROFILE reels countable —
        //     their generic full-screen pager recycler passes. IG recyclers get a
        //     stricter 0.9 bar (its fully-dragged sheets get tall); everything
        //     else uses 0.75.
        // YouTube's highlighted-comment popup is an id-less plain ViewGroup a
        // fraction of the screen tall; its comment-rotate animation fires
        // TYPE_VIEW_SCROLLED, which was accepted as swipe proof (default-allow
        // path) and authorized a phantom +1 mid-watch. Real YT swipes come
        // from the full-screen reel_recycler/pager, so height-gating plain
        // ViewGroups on YouTube is safe.
        val isContainerClass = lowerClass.contains("viewpager") ||
            lowerClass.contains("recyclerview") ||
            lowerClass.contains("pager") ||
            (platform == Constants.PLATFORM_YOUTUBE && lowerClass.contains("viewgroup"))
        if (isContainerClass && sourceBounds.height() > 0) {
            val screenH = resources.displayMetrics.heightPixels
            val minFraction = if (platform == Constants.PLATFORM_INSTAGRAM &&
                lowerClass.contains("recyclerview")) 0.9f else 0.75f
            if (sourceBounds.height() < screenH * minFraction) {
                return false
            }
        }

        // 2. Known NON-main-container classes that trigger scroll events
        //    but should NOT be counted as user reel scrolls.
        //    NOTE: "scrollview" and "nestedscrollview" are excluded precisely —
        //    do NOT add a bare "scroll" deny here because RecyclerView / ViewPager
        //    class names also contain "scroll" and would be silently blocked.
        if (lowerClass.contains("seekbar") ||
            lowerClass.contains("progressbar") ||
            lowerClass == "android.widget.scrollview" ||
            lowerClass.contains("nestedscrollview") ||
            lowerClass.contains("edittext")
        ) {
            return false
        }

        // 3. Reject if scrolling is coming from ad overlays or other non-core UI.
        //    On a real reel swipe the event originates from a ViewPager/RecyclerView,
        //    not a plain TextView or Button.
        if (lowerClass.contains("textview") || lowerClass.contains("button")) {
            return false
        }

        // 4. Known container-like classes — treat as a real reel scroll.
        if (lowerClass.contains("viewpager") ||
            lowerClass.contains("recyclerview") ||
            lowerClass.contains("pager")
        ) {
            return true
        }

        // 5. Default: allow. Handles OEM devices with obfuscated class names where
        //    the real swipe container has no recognisable substring.
        return true
    }

    /**
     * Heuristic to determine if two signatures represent genuinely different
     * reels rather than metadata drift on the same reel (e.g. view count
     * incrementing, timestamp updating, caption expanding/collapsing).
     *
     * Signatures are platform-prefixed (ig_, ch_, tt_, fb_, li_, snapchat_).
     * If the structural prefix (platform + author/channel) differs, it's
     * almost certainly a different reel. If only the trailing portion
     * (description/caption hash) changed, check if the change is large
     * enough to indicate new content rather than a counter update.
     */
    /**
     * Choose which signature to remember as the baseline after a no-count (or
     * count) pass. When the new signature is the SAME reel but has LOST its
     * caption — comments opened (caption hidden) or the caption hasn't loaded
     * yet — we keep the richer previous signature. That way a caption that
     * loads a moment later is compared against the real previous reel, so a
     * genuine swipe to a *same-author* reel is still recognised and counted
     * (instead of being swallowed by the transient caption-empty state). A real
     * reel change always advances normally.
     */
    /**
     * Page-aware dedup identity for a reel. Pins same-author reels apart by their
     * pager page (so scrolling one person's profile reels counts each), while a
     * loop-back to a prior page collapses to an already-seen identity. Falls back
     * to the bare content signature when no pager index has been seen.
     */
    private fun reelIdentity(signature: String): String =
        if (reelPageIndex >= 0) "${signature}#${reelPageIndex}" else signature

    private fun preferRicherBaseline(prev: String, current: String): String {
        if (isLikelyDifferentReel(prev, current)) return current
        val curRest = current.split("_", limit = 3).getOrElse(2) { "" }
        val prevRest = prev.split("_", limit = 3).getOrElse(2) { "" }
        return if (curRest.isEmpty() && prevRest.isNotEmpty()) prev else current
    }

    private fun isLikelyDifferentReel(prevSig: String, newSig: String): Boolean {
        if (prevSig == newSig) return false
        if (prevSig.isEmpty() || newSig.isEmpty()) return true

        // Signatures are platform-prefixed "<prefix>_<key>_<rest>" where <key> is
        // the author/channel and <rest> is the caption/title. (Snapchat uses
        // "snapchat_spotlight_<content>", so its <key> is the constant
        // "spotlight" and the discriminator is <rest> — handled uniformly below.)
        val prevParts = prevSig.split("_", limit = 3)
        val newParts = newSig.split("_", limit = 3)

        // Different platform prefix → definitely a different reel.
        if (prevParts.firstOrNull() != newParts.firstOrNull()) return true

        // Different author/channel key (segment 2) → different reel.
        val prevKey = prevParts.getOrElse(1) { "" }
        val newKey = newParts.getOrElse(1) { "" }

        // Strip digits so view-count / timestamp drift never reads as a new reel.
        val prevRest = prevParts.getOrElse(2) { "" }.replace(Regex("\\d+"), "")
        val newRest = newParts.getOrElse(2) { "" }.replace(Regex("\\d+"), "")

        if (prevKey != newKey) {
            // Keys differ — but Instagram falls back to placeholder keys
            // ("caption" when the author hasn't been extracted, "media" when only
            // the media content-desc is available). The key flips on the SAME
            // reel the instant the real author username loads (caption_X →
            // author_X), which was registering as a brand-new reel and
            // phantom-counting +1. When either side is a placeholder, ignore the
            // key flip and decide on the trailing caption/title alone.
            // "" is YouTube's placeholder: the channel desc loads a beat after
            // the title, so the sig goes ch__<title> → ch_<handle>_<title> on
            // the SAME short (was phantom-counting the just-opened short late).
            val placeholderKeys = setOf("caption", "media", "")
            if (prevKey !in placeholderKeys && newKey !in placeholderKeys) return true
            if (prevRest == newRest) return false
            if (prevRest.isEmpty() || newRest.isEmpty()) return false
            if (prevRest.startsWith(newRest) || newRest.startsWith(prevRest)) return false
            return true
        }

        // Same prefix AND same author/channel — only the trailing caption/title
        // changed. Decide whether that's a genuinely new reel or just the SAME
        // reel whose caption was hidden / expanded / collapsed.

        // Identical trailing segment → same reel.
        if (prevRest == newRest) return false
        // One side has no caption → comments opened (caption hidden behind the
        // sheet) or the caption hasn't loaded yet → treat as the SAME reel.
        // This is the core fix for "tap comment = +1".
        if (prevRest.isEmpty() || newRest.isEmpty()) return false
        // One caption is a prefix of the other → "… more" expand / collapse on
        // the SAME reel (expansion only appends words; the leading words never
        // change). This is the core fix for "tap caption = +1".
        if (prevRest.startsWith(newRest) || newRest.startsWith(prevRest)) return false

        // Same author/channel but a genuinely different caption/title → a new
        // reel (e.g. swiping between two reels from the same account, or two
        // different Shorts from the same channel).
        return true
    }

    private fun isInstagramCaptionOrControlSource(viewId: String, className: String): Boolean {
        val id = viewId.lowercase(Locale.ROOT)
        val cls = className.lowercase(Locale.ROOT)
        val ignoredIds = listOf(
            "caption", "comment", "like", "play", "pause", "mute", "unmute",
            "music", "audio", "profile", "username", "snackbar", "more"
        )
        return ignoredIds.any { id.contains(it) } ||
            cls.contains("textview") ||
            cls.contains("imageview") ||
            cls.contains("button") ||
            cls.contains("edittext")
    }

    private var lastHapticTime = 0L

    private fun triggerHaptic(state: BrainState) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastHapticTime < 250L) return
        lastHapticTime = currentTime

        serviceScope.launch {
            val hapticsOn = userPreferences.hapticEnabled.first()
            if (!hapticsOn) return@launch

            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Single, gentle pulses that scale with severity. Amplitude
                // capped well below max (255) so the buzz is light, per request.
                when (state) {
                    BrainState.FRESH, BrainState.HEALTHY -> VibrationEffect.createOneShot(15, 60)
                    BrainState.MILD -> VibrationEffect.createOneShot(20, 80)
                    BrainState.WARNING -> VibrationEffect.createOneShot(28, 105)
                    BrainState.CRITICAL -> VibrationEffect.createOneShot(38, 135)
                    BrainState.NUMB -> VibrationEffect.createOneShot(50, 160)
                }
            } else {
                when (state) {
                    BrainState.FRESH, BrainState.HEALTHY -> VibrationEffect.createOneShot(35, 100)
                    BrainState.MILD -> VibrationEffect.createOneShot(50, 150)
                    BrainState.WARNING -> VibrationEffect.createOneShot(85, 200)
                    BrainState.CRITICAL -> VibrationEffect.createOneShot(140, 240)
                    BrainState.NUMB -> VibrationEffect.createOneShot(220, 255)
                }
            }

            vibrator.vibrate(effect)
        }
    }

    private fun startTimeTracking(platform: String) {
        if (!shieldEnabled) return
        val now = System.currentTimeMillis()
        activeSessionStartTime = now
        lastSavedSessionTime = now
        timeTrackingJob?.cancel()
        timeTrackingJob = serviceScope.launch(Dispatchers.Main) {
            while (true) {
                delay(10_000L) // Check every 10 seconds
                val tickNow = System.currentTimeMillis()

                // ── Date-change guard ────────────────────────────────────
                // If midnight passed since the last tick, snap the in-memory
                // scroll count to today's DB value so the shield never fires
                // because of yesterday's leftover.
                if (dateKeyFormat.format(Date()) != lastCountedDate) {
                    refreshCountForDateChange()
                }

                // Persist the *delta* since the last save (never the full
                // session) so we don't double-count when stopTimeTracking
                // also flushes the tail of the session.
                if (tickNow - lastSavedSessionTime >= 30_000L &&
                    currentPlatform.isNotEmpty() &&
                    activeSessionStartTime > 0
                ) {
                    val plat = currentPlatform
                    val from = lastSavedSessionTime
                    lastSavedSessionTime = tickNow
                    serviceScope.launch {
                        scrollRepository.recordSession(plat, from, tickNow)
                    }
                }
            }
        }
    }

    private fun stopTimeTracking() {
        timeTrackingJob?.cancel()
        timeTrackingJob = null
        if (activeSessionStartTime > 0 && currentPlatform.isNotEmpty()) {
            val endTime = System.currentTimeMillis()
            val plat = currentPlatform
            val from = lastSavedSessionTime.coerceAtLeast(activeSessionStartTime)
            if (endTime > from) {
                serviceScope.launch {
                    scrollRepository.recordSession(plat, from, endTime)
                }
            }
        }
        activeSessionStartTime = 0L
        lastSavedSessionTime = 0L
    }

    override fun onInterrupt() {
        isRunning = false
        instance = null
        stopTimeTracking()
        hideFloatingWidget()
        setCapShieldVisible(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        instance = null
        powerSaveReceiver?.let { runCatching { unregisterReceiver(it) } }
        powerSaveReceiver = null
        awarenessDebugReceiver?.let { runCatching { unregisterReceiver(it) } }
        awarenessDebugReceiver = null
        stopTimeTracking()
        hideFloatingWidget()
        setCapShieldVisible(false)
        lifecycleOwner.stop()
        mViewModelStore.clear()
        consumerJob?.cancel()
        consumerJob = null
        pollingJob?.cancel()
        pollingJob = null
        serviceScope.cancel()
    }
}
