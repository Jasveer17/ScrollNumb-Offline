package com.scrollnumb.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollnumb.app.core.datastore.UserPreferences
import com.scrollnumb.app.data.repository.ScrollRepository
import com.scrollnumb.app.data.repository.StreakRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/** Max "undo last count" corrections per day (anti-cheat for the cap/streak). */
const val MAX_UNDOS_PER_DAY = 3

data class DashboardUiState(
    val globalScrollCount: Int = 0,
    val dailyLimit: Int = 50,
    val dailyTimeLimitMins: Int = 30,
    val blockDurationMins: Int = 5,
    val shieldEnabled: Boolean = true,
    val instagramCount: Int = 0,
    val youtubeCount: Int = 0,
    val facebookCount: Int = 0,
    val snapchatCount: Int = 0,
    val linkedinCount: Int = 0,
    val tiktokCount: Int = 0,
    val totalMinutesToday: Int = 0,
    val totalSecondsToday: Int = 0,
    val decompressionTasksToday: Int = 0,
    val streakCount: Int = 0,
    val longestStreak: Int = 0,
    val freezeTokens: Int = 0,
    val undosLeft: Int = MAX_UNDOS_PER_DAY,
    val isTrackingEnabled: Boolean = true,
    val isServiceRunning: Boolean = false,
    val blockStatusText: String = "",
    val isRefreshing: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val scrollRepository: ScrollRepository,
    private val streakRepository: StreakRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState = _uiState.asStateFlow()

    /**
     * Grant a Touch-Some-Grass session's XP (shares the daily breathing budget)
     * and return the XP actually banked — 0 once the daily cap is hit, so callers
     * can show the user the truth instead of a flat nominal amount.
     */
    suspend fun awardGrassXp(kind: String): Int {
        val before = userPreferences.focusXp.first()
        userPreferences.addBreatheMinutes(1)
        if (kind == "dark") userPreferences.markDarkRoomDoneToday()
        else userPreferences.markBreatheDoneToday()
        com.scrollnumb.app.core.util.XpLedger.recompute(
            userPreferences,
            streakDays = _uiState.value.streakCount,
            reelsToday = _uiState.value.globalScrollCount,
            dailyLimit = _uiState.value.dailyLimit
        )
        return (userPreferences.focusXp.first() - before).coerceAtLeast(0)
    }

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            userPreferences.trackingEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(
                    isTrackingEnabled = enabled,
                    isServiceRunning = com.scrollnumb.app.service.ScrollAccessibilityService.isRunning
                )
            }
        }

        viewModelScope.launch {
            userPreferences.dailyLimit.collect { limit ->
                _uiState.value = _uiState.value.copy(dailyLimit = limit)
            }
        }

        viewModelScope.launch {
            userPreferences.dailyTimeLimitMins.collect { timeLimit ->
                _uiState.value = _uiState.value.copy(dailyTimeLimitMins = timeLimit)
            }
        }

        viewModelScope.launch {
            userPreferences.blockDurationMins.collect { mins ->
                _uiState.value = _uiState.value.copy(blockDurationMins = mins)
            }
        }

        viewModelScope.launch {
            userPreferences.decompressionTasksToday.collect { count ->
                _uiState.value = _uiState.value.copy(decompressionTasksToday = count)
            }
        }

        viewModelScope.launch {
            userPreferences.shieldEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(shieldEnabled = enabled)
            }
        }

        viewModelScope.launch {
            scrollRepository.getTodayStatsFlow().collect { stats ->
                if (stats != null) {
                    _uiState.value = _uiState.value.copy(
                        globalScrollCount = stats.totalScrolls,
                        instagramCount = stats.instagramScrolls,
                        youtubeCount = stats.youtubeScrolls,
                        facebookCount = stats.facebookScrolls,
                        snapchatCount = stats.snapchatScrolls,
                        linkedinCount = stats.linkedinScrolls,
                        tiktokCount = stats.tiktokScrolls,
                        totalMinutesToday = stats.totalScrollTimeMinutes,
                        totalSecondsToday = stats.totalScrollTimeSeconds
                    )
                }
            }
        }

        viewModelScope.launch {
            streakRepository.getStreakStatus().collect { streak ->
                _uiState.value = _uiState.value.copy(
                    streakCount = streak?.currentStreak ?: 0,
                    longestStreak = streak?.longestStreak ?: 0,
                    freezeTokens = streak?.freezeTokens ?: 0
                )
            }
        }

        viewModelScope.launch {
            userPreferences.undosUsedToday.collect { used ->
                _uiState.value = _uiState.value.copy(
                    undosLeft = (MAX_UNDOS_PER_DAY - used).coerceAtLeast(0)
                )
            }
        }

        viewModelScope.launch {
            scrollRepository.scrollUpdates.collect { newCount ->
                _uiState.value = _uiState.value.copy(globalScrollCount = newCount)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            delay(500)
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    fun toggleTracking() {
        viewModelScope.launch {
            userPreferences.setTrackingEnabled(!_uiState.value.isTrackingEnabled)
        }
    }

    fun toggleShield() {
        viewModelScope.launch {
            userPreferences.setShieldEnabled(!_uiState.value.shieldEnabled)
        }
    }

    fun setDailyLimit(limit: Int) {
        viewModelScope.launch { userPreferences.setDailyLimit(limit) }
    }

    fun setDailyTimeLimitMins(limit: Int) {
        viewModelScope.launch { userPreferences.setDailyTimeLimitMins(limit) }
    }

    fun setBlockDurationMins(mins: Int) {
        viewModelScope.launch { userPreferences.setBlockDurationMins(mins) }
    }

    /** Block reels for the rest of today (until 23:59:59.999). */
    fun blockRestOfDay() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        com.scrollnumb.app.service.ScrollAccessibilityService.manualBlockExpiryMs = cal.timeInMillis
        com.scrollnumb.app.service.ScrollAccessibilityService.manualBlockMessage =
            "Blocked until midnight"
        com.scrollnumb.app.service.ScrollAccessibilityService.manualBlockMessageVersion++
        viewModelScope.launch { userPreferences.setManualBlock(cal.timeInMillis, "Blocked until midnight") }
        _uiState.value = _uiState.value.copy(blockStatusText = "Blocked until midnight")
    }

    /** Block reels for 7 days from now. */
    fun blockSevenDays() {
        com.scrollnumb.app.service.ScrollAccessibilityService.manualBlockExpiryMs =
            System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000
        com.scrollnumb.app.service.ScrollAccessibilityService.manualBlockMessage =
            "Blocked for 7 days"
        com.scrollnumb.app.service.ScrollAccessibilityService.manualBlockMessageVersion++
        viewModelScope.launch {
            userPreferences.setManualBlock(
                System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000,
                "Blocked for 7 days"
            )
        }
        _uiState.value = _uiState.value.copy(blockStatusText = "Blocked for 7 days")
    }

    /** Remove any manual block. */
    fun unblock() {
        com.scrollnumb.app.service.ScrollAccessibilityService.manualBlockExpiryMs = 0L
        com.scrollnumb.app.service.ScrollAccessibilityService.manualBlockMessage = ""
        com.scrollnumb.app.service.ScrollAccessibilityService.manualBlockMessageVersion++
        viewModelScope.launch { userPreferences.clearManualBlock() }
        _uiState.value = _uiState.value.copy(blockStatusText = "")
    }

    /**
     * Undo the most recent (often accidental) scroll count. Capped at
     * [MAX_UNDOS_PER_DAY] so it can't be spammed to dodge the cap/shield or
     * cheat the streak. (The day's limitWasExceeded flag is latched separately,
     * so an undo can never un-break a streak either.)
     */
    fun recoverFocus() {
        viewModelScope.launch {
            if (_uiState.value.globalScrollCount <= 0) return@launch
            if (!userPreferences.tryConsumeUndo(MAX_UNDOS_PER_DAY)) return@launch
            val newTotal = scrollRepository.removeLastScroll("")
            if (newTotal != null) {
                // Re-read the whole row so the Top-apps tiles drop with the
                // total (the hero count had a direct update path; the per-app
                // counts only had the Room flow, which left them stale on some
                // devices).
                val stats = scrollRepository.getTodayStats()
                _uiState.value = _uiState.value.copy(
                    globalScrollCount = newTotal,
                    instagramCount = stats?.instagramScrolls ?: _uiState.value.instagramCount,
                    youtubeCount = stats?.youtubeScrolls ?: _uiState.value.youtubeCount,
                    facebookCount = stats?.facebookScrolls ?: _uiState.value.facebookCount,
                    snapchatCount = stats?.snapchatScrolls ?: _uiState.value.snapchatCount,
                    linkedinCount = stats?.linkedinScrolls ?: _uiState.value.linkedinCount,
                    tiktokCount = stats?.tiktokScrolls ?: _uiState.value.tiktokCount
                )
            }
        }
    }
}
