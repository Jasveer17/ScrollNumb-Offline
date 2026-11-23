package com.scrollnumb.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollnumb.app.core.datastore.UserPreferences
import com.scrollnumb.app.core.util.AvatarHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import javax.inject.Inject

data class EditProfileUiState(
    val displayName: String = "",
    val username: String = "",
    val bio: String = "",
    val avatar: String = "",
    val email: String = "",
    val dob: String = "", // ISO yyyy-MM-dd
    val isLoading: Boolean = true,
    val isSaving: Boolean = false
)

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState = _uiState.asStateFlow()

    private val avatarChoices = listOf("🧠", "🧘", "🐙", "🦊", "🐝", "🌱", "🎯", "🔥", "🐢", "🐳", "🦉", "🌙")

    init {
        viewModelScope.launch {
            val name = userPreferences.profileName.first()
            val username = userPreferences.profileUsername.first()
            val bio = userPreferences.profileBio.first()
            val avatar = userPreferences.profileAvatar.first()
            val email = userPreferences.profileEmail.first()
            val dob = userPreferences.profileDob.first()
            val cleanAvatar = when {
                avatar.isBlank() -> ""
                avatar.startsWith("content://") || avatar.startsWith("file://") -> avatar
                // Keep a cloud photo URL (Storage/Google) — dropping it here made
                // a later Save silently wipe the user's uploaded photo.
                com.scrollnumb.app.core.util.AvatarHelper.isTrustedAvatarUrl(avatar) -> avatar
                avatar.length > 4 -> ""
                else -> avatar
            }
            _uiState.value = EditProfileUiState(
                displayName = name,
                username = username,
                bio = bio,
                avatar = cleanAvatar,
                email = email,
                dob = dob,
                isLoading = false
            )
        }
    }

    fun avatarChoices(): List<String> = avatarChoices

    fun onNameChange(value: String) {
        _uiState.value = _uiState.value.copy(displayName = value)
    }

    fun onUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(username = value)
    }

    fun onBioChange(value: String) {
        _uiState.value = _uiState.value.copy(bio = value)
    }

    fun onAvatarChange(value: String) {
        _uiState.value = _uiState.value.copy(avatar = value)
    }

    fun onPhotoPicked(uri: String) {
        _uiState.value = _uiState.value.copy(avatar = uri)
    }

    fun onDobChange(iso: String) {
        _uiState.value = _uiState.value.copy(dob = iso)
    }

    fun resetPhoto() {
        _uiState.value = _uiState.value.copy(avatar = "")
    }

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value)
    }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (state.isSaving) return
        _uiState.value = state.copy(isSaving = true)
        viewModelScope.launch {
            userPreferences.setProfileName(state.displayName.trim().ifEmpty { "Player 1" })
            userPreferences.setProfileUsername(
                state.username.trim().ifEmpty { "@player1" }.let { v ->
                    if (v.startsWith("@")) v else "@$v"
                }
            )
            userPreferences.setProfileBio(state.bio.trim())
            userPreferences.setProfileAvatar(state.avatar)
            userPreferences.setProfileEmail(state.email.trim())
            userPreferences.setProfileDob(state.dob.trim())

            _uiState.value = _uiState.value.copy(isSaving = false)
            onSaved()
        }
    }
}
