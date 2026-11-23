package com.scrollnumb.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import com.scrollnumb.app.core.designsystem.components.avatarPainter as painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.scrollnumb.app.R
import com.scrollnumb.app.core.designsystem.components.HolographicText
import com.scrollnumb.app.core.designsystem.components.MicrocopyChip
import com.scrollnumb.app.core.designsystem.components.NeonText
import com.scrollnumb.app.core.designsystem.components.NumbnessPill
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbCorners
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbTheme
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbType
import com.scrollnumb.app.core.datastore.UserPreferences
import com.scrollnumb.app.core.util.AvatarHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class BlockerOverlayService : Service(), ViewModelStoreOwner {

    companion object {
        private const val TAG = "ScrollNumb"
        var isOverlayShowing = false
            private set
        var instance: BlockerOverlayService? = null

        fun isNapActive(): Boolean {
            return instance?.gatekeeperState?.value == "locked"
        }
    }

    @Inject
    lateinit var userPreferences: UserPreferences


    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private var floatingParams: WindowManager.LayoutParams? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var timedPassRunnable: Runnable? = null

    private val gatekeeperState = mutableStateOf("gatekeeper")
    private val lockTimeLeft = mutableIntStateOf(0)
    private var timerJob: Job? = null

    private val shieldContentVisible = mutableStateOf(false)
    val isShieldContentVisible: Boolean
        get() = shieldContentVisible.value
    // Tracks the last visible value applied to the WindowManager so we don't
    // hammer updateViewLayout() on every 150ms polling pass.
    @Volatile private var lastAppliedVisible: Boolean = false
    private var lastKnownBlockMsgVersion: Long = 0L

    // Shield page state machine:
    //   LOCKED       — normal timer countdown (default)
    //   COMPLETED    — timer hit 0, showing "What now?" options
    //   LOCKED_FOR_DAY — user chose "Lock for the day"
    private enum class ShieldPage { LOCKED, COMPLETED, LOCKED_FOR_DAY }
    private val shieldPage = mutableStateOf(ShieldPage.LOCKED)

    // When true, the startLockTimer coroutine will NOT re-initialise from
    // prefs on the next tick where remaining <= 0 (the timer actually
    // expired instead of just being reset). Cleared by resetLockTimer()
    // or extendCap() so the next block starts with a fresh duration.
    private var timerExpired = false

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

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        savedStateRegistryOwner.init()
        lifecycleOwner.start()
        shieldPage.value = ShieldPage.LOCKED
        timerExpired = false
        lockTimeLeft.intValue = 0
        Log.d(TAG, "BlockerOverlayService.onCreate instance=$this lockTimeLeft=${lockTimeLeft.intValue}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val actionDismiss = intent?.getBooleanExtra("dismiss", false) ?: false
        Log.d(TAG, "BlockerOverlayService.onStartCommand actionDismiss=$actionDismiss")
        if (actionDismiss) {
            Log.d(TAG, "dismiss action — hiding shield content (service stays alive)")
            // Hide the shield content (pass-through) but keep the
            // service alive. Same rationale as exitReels: timer state
            // must persist across hide/show cycles, and the polling
            // engine owns shield visibility.
            mainHandler.post {
                setShieldContentVisible(false)
            }
            return START_NOT_STICKY
        }

        timedPassRunnable?.let { mainHandler.removeCallbacks(it) }

        // Do NOT pre-show the overlay here. The polling engine is the sole
        // driver of shield visibility and will call setShieldContentVisible()
        // when (and only when) the user is on a Reels/Shorts surface with the
        // day cap hit. Pre-showing caused the view to flash on at service
        // start, then hide again 150ms later.
        return START_STICKY
    }

    private fun showOverlay() {
        if (composeView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Start as fully transparent and pass-through. The polling engine
            // flips the touchable flag when the shield content is shown.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        floatingParams = params

        // A TYPE_ACCESSIBILITY_OVERLAY window can only be added by the
        // accessibility-service context — adding it from this plain Service
        // fails silently ("View not attached"), so the shield never rendered.
        // Attach through the live accessibility service and build the
        // ComposeView with that context. updateViewLayout/removeView below
        // reuse this same windowManager so they stay consistent.
        val axService = ScrollAccessibilityService.instance
        val overlayContext: Context = axService ?: this
        windowManager = overlayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        composeView = ComposeView(overlayContext).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(this@BlockerOverlayService)
            setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)

            setContent {
                val themeName by userPreferences.appTheme.collectAsState(initial = "DARK")
                val effectiveTheme = themeName
                ScrollNumbTheme(darkTheme = true, themeName = effectiveTheme) {
                    // Animated entrance (fade + subtle scale) — the hard Compose
                    // cut read as a jarring pop. Exit stays near-instant so quit
                    // teardown never feels delayed. Hidden = no content = the
                    // TRANSLUCENT window is fully transparent and pass-through.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = shieldContentVisible.value,
                        enter = androidx.compose.animation.fadeIn(
                            androidx.compose.animation.core.tween(durationMillis = 260)
                        ) + androidx.compose.animation.scaleIn(
                            initialScale = 0.97f,
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 320)
                        ),
                        exit = androidx.compose.animation.fadeOut(
                            androidx.compose.animation.core.tween(durationMillis = 110)
                        )
                    ) {
                        ShieldContent(
                            lockTimeLeft = lockTimeLeft,
                            onExitReels = { exitReels() },
                            onEarnXp = { navigateToRelaxation() },
                            onExtend = { extendCap() },
                            onLockForDay = { lockForDay() }
                        )
                    }
                }
            }
        }

        try {
            windowManager.addView(composeView, params)
            isOverlayShowing = true
        } catch (e: Exception) {
            Log.e(TAG, "addView(composeView) failed", e)
        }
    }

    @Composable
    private fun ShieldContent(
        lockTimeLeft: androidx.compose.runtime.MutableIntState,
        onExitReels: () -> Unit,
        onEarnXp: () -> Unit,
        onExtend: () -> Unit,
        onLockForDay: () -> Unit
    ) {
        val page by shieldPage

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            ScrollNumbColors.Ink,
                            ScrollNumbColors.InkRaised
                        )
                    )
                )
        ) {
            // Top bar with a prominent back/home button so the
            // user can leave Shorts/Reels and go watch a long
            // video on YouTube home.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ScrollNumbColors.InkElevated)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = onExitReels
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = ScrollNumbColors.Cream,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ScrollNumbColors.InkElevated)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = onExitReels
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.Home,
                        contentDescription = "Home",
                        tint = ScrollNumbColors.Cream,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            when (page) {
                ShieldPage.LOCKED -> LockedContent(
                    lockTimeLeft = lockTimeLeft,
                    onExitReels = onExitReels,
                    onEarnXp = onEarnXp
                )
                ShieldPage.COMPLETED -> CompletedContent(
                    onExtend = onExtend,
                    onLockForDay = onLockForDay
                )
                ShieldPage.LOCKED_FOR_DAY -> LockedForDayContent()
            }
        }
    }

    @Composable
    private fun LockedContent(
        lockTimeLeft: androidx.compose.runtime.MutableIntState,
        onExitReels: () -> Unit,
        onEarnXp: () -> Unit
    ) {
        val timeLeft by lockTimeLeft

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Image(
                painter = painterResource(id = AvatarHelper.getShieldAvatar(androidx.compose.ui.platform.LocalContext.current)),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(180.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            MicrocopyChip(
                text = stringResource(R.string.blocker_reels_locked).uppercase()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.blocker_take_nap),
                style = ScrollNumbType.BrandHero,
                color = ScrollNumbColors.Cream,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.blocker_subtitle),
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                color = ScrollNumbColors.CreamDim,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            val minutes = timeLeft / 60
            val seconds = timeLeft % 60
            val timeStr = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NeonText(
                    text = timeStr,
                    style = ScrollNumbType.NumeralLarge,
                    color = ScrollNumbColors.Saffron
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.blocker_unlock_in).uppercase(),
                    style = ScrollNumbType.Microcopy,
                    color = ScrollNumbColors.CreamMuted
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Rotating daily thought — small, grounding, changes each day.
            val quotes = remember {
                listOf(
                    "\"The feed is infinite. Your time is not.\"",
                    "\"Boredom is where ideas are born.\"",
                    "\"You weren't tired. You were overstimulated.\"",
                    "\"Attention is the rarest kind of generosity.\"",
                    "\"The reel will still be there. This moment won't.\"",
                    "\"Stillness is a skill. You're practising it now.\"",
                    "\"Your brain is not a slot machine.\""
                )
            }
            // Fresh quote each time the shield appears, not just once per day.
            val quote = remember { quotes.random() }
            Text(
                text = quote,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = ScrollNumbColors.CreamMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            NumbnessPill(
                label = stringResource(R.string.blocker_exit_reels),
                onClick = onExitReels,
                inverse = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.blocker_earn_xp),
                style = ScrollNumbType.Microcopy,
                color = ScrollNumbColors.Saffron,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(ScrollNumbCorners.pill))
                    .background(ScrollNumbColors.Saffron.copy(alpha = 0.12f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = onEarnXp
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }

    @Composable
    private fun CompletedContent(
        onExtend: () -> Unit,
        onLockForDay: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "TIME'S UP",
                style = ScrollNumbType.Microcopy,
                color = ScrollNumbColors.CreamDim,
                letterSpacing = androidx.compose.ui.unit.TextUnit(4f, androidx.compose.ui.unit.TextUnitType.Sp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            HolographicText(
                text = "Your break is over.\nWhat now?",
                style = ScrollNumbType.BrandHero.copy(textAlign = TextAlign.Center)
            )

            Spacer(modifier = Modifier.height(40.dp))

            NumbnessPill(
                label = "Give me 10 more",
                onClick = onExtend,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            NumbnessPill(
                label = "Lock me for the day",
                onClick = onLockForDay,
                inverse = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            NumbnessPill(
                label = stringResource(R.string.blocker_exit_reels),
                onClick = { exitReels() },
                inverse = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    @Composable
    private fun LockedForDayContent() {
        val count = ScrollAccessibilityService.currentScrollCount
        val blockMsg = if (count >= 200) {
            "Daily limit reached"
        } else {
            ScrollAccessibilityService.manualBlockMessage
        }
        // Trigger recomposition when the block message changes by reading
        // a state that we update alongside manualBlockMessage. Without this,
        // the Compose cache keeps the old content even after message updates.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Image(
                painter = painterResource(id = AvatarHelper.getShieldAvatar(androidx.compose.ui.platform.LocalContext.current)),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(180.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            MicrocopyChip(
                text = "BLOCKED"
            )

            Spacer(modifier = Modifier.height(20.dp))

            HolographicText(
                text = blockMsg.ifEmpty { "See you tomorrow" },
                style = ScrollNumbType.BrandHero.copy(textAlign = TextAlign.Center)
            )

            Spacer(modifier = Modifier.height(12.dp))

            val description = when {
                count >= 200 -> "You have reached the absolute daily limit of 200 reels. Come back tomorrow!"
                blockMsg.contains("7 days") -> "You'll be unblocked in 7 days. Get some rest."
                blockMsg.contains("midnight") -> "Your scroll limit resets at midnight. Get some rest and come back fresh."
                else -> "Take a break and come back when you're ready."
            }
            Text(
                text = description,
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                color = ScrollNumbColors.CreamDim,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            NumbnessPill(
                label = stringResource(R.string.blocker_exit_reels),
                onClick = { exitReels() },
                inverse = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    private fun exitReels() {
        Log.d(TAG, "exitReels called — sending user to home screen (service stays alive)")
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        // Hide the shield content (pass-through) but DO NOT remove the
        // overlay view and DO NOT stopSelf. The service must stay alive
        // so:
        //   1. The lock timer keeps its place (no fresh start when the
        //      user re-enters a tracked app).
        //   2. The polling engine can re-show the shield instantly if the
        //      day is still locked and the user swipes back into Reels.
        //   3. removeOverlay() is skipped so the view stays attached and
        //      the next show is flash-free.
        mainHandler.post {
            setShieldContentVisible(false)
        }
    }

    private fun extendCap() {
        Log.d(TAG, "extendCap called — granting 10 more scrolls")
        timerJob?.cancel()
        timerJob = null
        timerExpired = false
        lockTimeLeft.intValue = 0
        shieldPage.value = ShieldPage.LOCKED
        com.scrollnumb.app.service.ScrollAccessibilityService.pendingCapExtension = 10
        com.scrollnumb.app.service.ScrollAccessibilityService.passEndTime = 0L
        mainHandler.post {
            setShieldContentVisible(false)
        }
        mainHandler.postDelayed({
            if (shieldContentVisible.value) {
                startLockTimer()
            }
        }, 200)
    }

    private fun lockForDay() {
        Log.d(TAG, "lockForDay called — shield stays until midnight")
        // Set the REAL persistent until-midnight lock (same mechanism the in-app
        // "Blocked until midnight" uses). Without this the screen only relied on
        // the volatile cap-state (count >= dailyLimit), which the undo/recover
        // flow could clear — so a single undone scroll bypassed "lock for the day".
        // This block is count-independent and overrides the cap auto-unlatch
        // (ScrollAccessibilityService un-latch skips when manualBlockExpiryMs is
        // active). It only gates SHORT-VIDEO surfaces — the shield is shown solely
        // when the user is on a reel/short surface — so the rest of each app (home,
        // DMs, long-form video, etc.) stays usable.
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        ScrollAccessibilityService.manualBlockExpiryMs = cal.timeInMillis
        ScrollAccessibilityService.manualBlockMessage = "Blocked until midnight"
        ScrollAccessibilityService.manualBlockMessageVersion++
        // Persist so a reboot / service kill restores the lock instead of dropping
        // back to the bypassable cap state.
        serviceScope.launch {
            userPreferences.setManualBlock(cal.timeInMillis, "Blocked until midnight")
        }

        // Kill the timer so it can't trigger a COMPLETED transition.
        timerJob?.cancel()
        timerJob = null
        timerExpired = true
        lockTimeLeft.intValue = 0
        shieldPage.value = ShieldPage.LOCKED_FOR_DAY
    }

    private fun navigateToRelaxation() {
        mainHandler.post { setShieldContentVisible(false) }
        val intent = Intent(this, com.scrollnumb.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "relaxation")
        }
        startActivity(intent)
    }

    private fun removeOverlay() {
        composeView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            composeView = null
        }
        isOverlayShowing = false
        shieldContentVisible.value = false
        lastAppliedVisible = false
    }

    /**
     * Show or hide the shield content without removing the underlying
     * WindowManager view. Thread-safe: all WindowManager work is marshalled
     * to the main thread. Idempotent: only one updateViewLayout() per
     * visibility transition.
     */
    fun setShieldContentVisible(visible: Boolean) {
        // Always update Compose state immediately — it's cheap and the
        // recomposition will coalesce repeated identical writes.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyShieldContentVisible(visible)
        } else {
            mainHandler.post { applyShieldContentVisible(visible) }
        }
    }

    private fun applyShieldContentVisible(visible: Boolean) {
        // Update Compose state first so the UI is always in sync.
        shieldContentVisible.value = visible

        if (!isOverlayShowing) {
            if (!visible) {
                lastAppliedVisible = false
                stopLockTimer()
                return
            }
            showOverlay()
        }

        // Idempotent: if we already applied this state, skip the expensive
        // WindowManager call. Without this the polling engine (7x/sec) would
        // thrash updateViewLayout() and the shield would visibly blink.
        // Exception: if manualBlockMessageVersion changed (user switched from
        // "Rest of the day" to "7 days" while shield was already showing),
        // we must rebuild the Compose content even though visible is unchanged.
        val msgVersionChanged = lastKnownBlockMsgVersion != ScrollAccessibilityService.manualBlockMessageVersion
        if (visible == lastAppliedVisible && composeView != null && !msgVersionChanged) return
        if (msgVersionChanged) {
            lastKnownBlockMsgVersion = ScrollAccessibilityService.manualBlockMessageVersion
            Log.d(TAG, "applyShieldContentVisible: block message changed, forcing content rebuild")
        }
        Log.d(TAG, "applyShieldContentVisible visible=$visible (was=$lastAppliedVisible) lockTimeLeft=${lockTimeLeft.intValue}")
        
        lastAppliedVisible = visible

        val params = floatingParams ?: return

        // Rebuild flags from scratch for clarity (no precedence surprises).
        val baseFlags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        params.flags = if (visible) {
            // Touchable so the user can tap back/home, NOT focusable so we
            // don't steal the back button from the system.
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        } else {
            // Pass-through: invisible to touches and to focus.
            baseFlags or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        try {
            composeView?.let { windowManager.updateViewLayout(it, params) }
        } catch (e: Exception) {
            Log.e(TAG, "updateViewLayout failed", e)
        }

        // Start/stop the countdown timer for the shield.
        // If a manual block is active (set from settings UI), skip the
        // timer entirely and show the LOCKED_FOR_DAY screen instead.
        // This prevents the "Give me 10 more" bypass from appearing.
        if (visible) {
            val mbExpiry = ScrollAccessibilityService.manualBlockExpiryMs
            val count = ScrollAccessibilityService.currentScrollCount
            if ((mbExpiry > 0 && System.currentTimeMillis() < mbExpiry) || count >= 200) {
                shieldPage.value = ShieldPage.LOCKED_FOR_DAY
            } else {
                startLockTimer()
            }
        } else {
            stopLockTimer()
        }
    }

    private fun startLockTimer() {
        if (timerJob?.isActive == true) return
        // Safety: if a manual block is active, bail out — the caller
        // should have handled this in applyShieldContentVisible, but
        // guard against any other code path that calls this directly.
        val mbExpiry = ScrollAccessibilityService.manualBlockExpiryMs
        val count = ScrollAccessibilityService.currentScrollCount
        if ((mbExpiry > 0 && System.currentTimeMillis() < mbExpiry) || count >= 200) {
            shieldPage.value = ShieldPage.LOCKED_FOR_DAY
            return
        }
        Log.d(TAG, "startLockTimer starting coroutine lockTimeLeft=${lockTimeLeft.intValue}")
        // Single long-running timer coroutine. It runs for the lifetime of
        // the service and counts down regardless of shield visibility.
        // The block timer represents a wall-clock lock — it should keep
        // ticking whether the shield is visible or not, so the user can't
        // extend their block by briefly hiding the shield.
        timerJob = serviceScope.launch {
            var remaining = lockTimeLeft.intValue
            // Tracks whether the timer has ever been given a non-zero
            // value (from prefs or resetLockTimer). Without this, the
            // check `lockTimeLeft.intValue == 0 && !timerExpired` would
            // re-fire every time remaining reaches 0 (because we write
            // remaining → lockTimeLeft.intValue each tick).
            var initialized = remaining > 0
            while (isActive) {
                delay(1000L)
                if (remaining <= 0) {
                    if (!initialized) {
                        // First tick after a reset — read the user's
                        // preferred block duration from prefs.
                        val mins = userPreferences.blockDurationMins.first()
                        Log.d(TAG, "timer init from prefs mins=$mins")
                        remaining = mins * 60
                        initialized = true
                    } else if (!timerExpired) {
                        // Timer just counted down to 0. Switch to the
                        // options screen so the user can extend or
                        // lock for the day.
                        timerExpired = true
                        lockTimeLeft.intValue = 0
                        shieldPage.value = ShieldPage.COMPLETED
                        continue
                    } else {
                        // Already expired, stay at 00:00 (shield remains
                        // visible via the day lock in the polling engine).
                        lockTimeLeft.intValue = 0
                        continue
                    }
                } else {
                    remaining -= 1
                }
                lockTimeLeft.intValue = remaining
            }
        }
    }

    private fun stopLockTimer() {
        // Intentionally empty: the timer coroutine runs continuously and
        // checks shieldContentVisible.value to pause/resume. This allows
        // the timer to maintain state across brief hide/show cycles without
        // losing its place. The coroutine is only cancelled in onDestroy().
    }

    /**
     * Reset the lock timer to a fresh block duration. Called by the
     * accessibility service when the day unlocks (new day, cap raised,
     * etc.) so the next shield show starts counting from full duration.
     */
    fun resetLockTimer() {
        Log.d(TAG, "resetLockTimer called lockTimeLeft=${lockTimeLeft.intValue}")
        mainHandler.post {
            timerJob?.cancel()
            timerJob = null
            lockTimeLeft.intValue = 0
            timerExpired = false
            shieldPage.value = ShieldPage.LOCKED
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "BlockerOverlayService.onDestroy lockTimeLeft=${lockTimeLeft.intValue}")
        super.onDestroy()
        instance = null
        timedPassRunnable?.let { mainHandler.removeCallbacks(it) }
        timerJob?.cancel()
        timerJob = null
        removeOverlay()
        lifecycleOwner.stop()
        mViewModelStore.clear()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
