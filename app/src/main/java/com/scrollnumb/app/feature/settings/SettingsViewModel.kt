package com.scrollnumb.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollnumb.app.core.datastore.UserPreferences

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val appTheme: String = "SYSTEM",
    val languageCode: String = "en",
    val isLoggedIn: Boolean = false,
    val leaderboardOptIn: Boolean = true,
    val analyticsEnabled: Boolean = true,
    val awarenessChecksEnabled: Boolean = true,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,

) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferences.appTheme.collect { theme ->
                _uiState.value = _uiState.value.copy(appTheme = theme)
            }
        }
        viewModelScope.launch {
            userPreferences.languageCode.collect { code ->
                _uiState.value = _uiState.value.copy(languageCode = code)
            }
        }
        viewModelScope.launch {
            userPreferences.loggedInUsername.collect { username ->
                _uiState.value = _uiState.value.copy(isLoggedIn = username != null)
            }
        }
        viewModelScope.launch {
            userPreferences.leaderboardOptIn.collect { optIn ->
                _uiState.value = _uiState.value.copy(leaderboardOptIn = optIn)
            }
        }
        viewModelScope.launch {
            userPreferences.analyticsEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(analyticsEnabled = enabled)
            }
        }
        viewModelScope.launch {
            userPreferences.awarenessChecksEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(awarenessChecksEnabled = enabled)
            }
        }
    }

    fun setLeaderboardOptIn(optIn: Boolean) = viewModelScope.launch {
        userPreferences.setLeaderboardOptIn(optIn)
    }

    fun setAnalyticsEnabled(enabled: Boolean) = viewModelScope.launch {
        userPreferences.setAnalyticsEnabled(enabled)
    }

    fun setAwarenessChecksEnabled(enabled: Boolean) = viewModelScope.launch {
        userPreferences.setAwarenessChecksEnabled(enabled)
    }

    fun cycleTheme() = viewModelScope.launch {
        val newTheme = when (_uiState.value.appTheme) {
            "SYSTEM" -> "DARK"
            "DARK" -> "LIGHT"
            "LIGHT" -> "KAWAII"
            "KAWAII" -> "DONGHUA"
            else -> "SYSTEM"
        }
        userPreferences.setAppTheme(newTheme)
    }

    fun setTheme(theme: String) = viewModelScope.launch {
        userPreferences.setAppTheme(theme)
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            kotlinx.coroutines.delay(500)
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }
}
