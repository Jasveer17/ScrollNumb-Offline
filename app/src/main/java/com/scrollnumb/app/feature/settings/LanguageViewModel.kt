package com.scrollnumb.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollnumb.app.core.datastore.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

    val languageCode: StateFlow<String> = userPreferences.languageCode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "en")

    suspend fun setLanguage(code: String) {
        userPreferences.setLanguageCode(code)
    }
}
