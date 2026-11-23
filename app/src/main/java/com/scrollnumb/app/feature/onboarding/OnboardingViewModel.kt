package com.scrollnumb.app.feature.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollnumb.app.core.datastore.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val currentPage: Int = 0,
    val dailyLimit: Int = 50,
    val blockDurationMins: Int = 20
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState = _uiState.asStateFlow()

    // "Draw your line" deliberately starts at the fixed defaults (50 reels /
    // 20 min) — quiz answers no longer personalise them.

    fun setPage(page: Int) {
        _uiState.value = _uiState.value.copy(currentPage = page)
    }

    fun setDailyLimit(limit: Int) {
        _uiState.value = _uiState.value.copy(dailyLimit = limit)
    }

    fun setBlockDurationMins(mins: Int) {
        _uiState.value = _uiState.value.copy(blockDurationMins = mins)
    }

    fun completeOnboarding(onComplete: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            userPreferences.setOnboardingCompleted(true)
            userPreferences.setDailyLimit(state.dailyLimit)
            userPreferences.setBlockDurationMins(state.blockDurationMins)
            // Mark the user as no longer new so SplashActivity routes them
            // straight to the app on next launch instead of back through
            // Welcome → Quiz → onboarding. Previously IS_NEW_USER was only
            // cleared on login (UserAuthRepository), so a user who finished
            // onboarding but skipped login saw the whole flow every launch.
            context.getSharedPreferences("scroll_numb_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("IS_NEW_USER", false)
                // Onboarding finished — clear the resume-page bookmark and arm
                // the one-time dashboard tour (only fresh-onboarded users see it).
                .putInt("ONBOARD_PAGE", 0)
                .putBoolean("APP_TOUR_PENDING", true).apply()
            onComplete()
        }
    }
}
