package com.scrollnumb.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollnumb.app.core.datastore.UserPreferences
import com.scrollnumb.app.core.util.XpFormula
import com.scrollnumb.app.data.repository.ScrollRepository
import com.scrollnumb.app.data.repository.StreakRepository

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val isLoggedIn: Boolean = false,
    val displayName: String = "",
    val username: String = "",
    val bio: String = "",
    val avatar: String = "🧠",
    val email: String = "",
    val joinedOn: String = "",
    val reelsToday: Int = 0,
    val dailyAvg: Int = 0,
    val totalReels: Int = 0,
    val streakDays: Int = 0,
    val totalXp: Int = 0,
    val weeklyXp: Int = 0,
    val level: Int = 0,
    val levelProgress: Float = 0f,
    val leagueTier: String = "Bronze",
    val xpBreakdown: XpFormula.Breakdown = XpFormula.Breakdown(0, 0, 0, 0),
    val dailyLimit: Int = 1,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val streakRepository: StreakRepository,
    private val scrollRepository: ScrollRepository,

) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(
        userPreferences.loggedInUsername,
        userPreferences.profileName,
        userPreferences.profileUsername,
        userPreferences.profileBio,
        userPreferences.profileAvatar,
        userPreferences.profileEmail,
        userPreferences.profileJoined,
        userPreferences.dailyLimit,
        userPreferences.focusXp,
        userPreferences.mathFocusMinutesToday,
        userPreferences.breatheMinutesToday,
        userPreferences.weeklyXp,
        userPreferences.leagueTier
    ) { values ->
        fun cleanAvatar(v: String): String {
            if (v.isBlank()) return "🧠"
            if (v.startsWith("content://") || v.startsWith("file://")) return v
            // A cloud photo URL (Storage/Google) is a valid avatar — without this
            // a restored photo rendered as the default brain emoji.
            if (com.scrollnumb.app.core.util.AvatarHelper.isTrustedAvatarUrl(v)) return v
            return if (v.length > 4) "🧠" else v
        }
        val avatarRaw = values[4] as String
        ProfilePrefSnapshot(
            isLoggedIn = values[0] != null,
            displayName = values[1] as String,
            username = values[2] as String,
            bio = values[3] as String,
            avatar = cleanAvatar(avatarRaw),
            email = values[5] as String,
            joinedOn = values[6] as String,
            dailyLimit = (values[7] as Int).coerceAtLeast(1),
            focusXp = values[8] as Int,
            mathFocusMinutes = values[9] as Int,
            breatheMinutes = values[10] as Int,
            weeklyXp = values[11] as Int,
            leagueTier = values[12] as String
        )
    }.let { prefsFlow ->
        combine(
            prefsFlow,
            streakRepository.getStreakStatus(),
            scrollRepository.getTodayStatsFlow(),
            scrollRepository.getTotalScrollsFlow(),
            kotlinx.coroutines.flow.flow { emit(scrollRepository.getRecentStats(30)) }
        ) { prefs, streak, stats, totalReels, recentStats ->
            val streakDays = streak?.currentStreak ?: 0
            val reelsToday = stats?.totalScrolls ?: 0
            val dailyAvg = if (recentStats.isNotEmpty()) recentStats.map { it.totalScrolls }.average().toInt() else 0
            val raw = XpFormula.breakdown(
                streakDays = streakDays,
                mathFocusMinutes = prefs.mathFocusMinutes,
                breatheMinutes = prefs.breatheMinutes,
                reelsToday = reelsToday,
                dailyLimit = prefs.dailyLimit
            )
            // Restraint is finalized after rollover, so never preview today's
            // provisional value as earned XP.
            val breakdown = raw.copy(scrollLessXp = 0)
            ProfileUiState(
                isLoggedIn = prefs.isLoggedIn,
                displayName = prefs.displayName,
                username = prefs.username,
                bio = prefs.bio,
                avatar = prefs.avatar,
                email = prefs.email,
                joinedOn = prefs.joinedOn,
                reelsToday = reelsToday,
                dailyAvg = dailyAvg,
                totalReels = totalReels,
                streakDays = streakDays,
                totalXp = prefs.focusXp,
                weeklyXp = prefs.weeklyXp,
                level = com.scrollnumb.app.core.util.LeagueSystem.levelFor(prefs.focusXp),
                levelProgress = com.scrollnumb.app.core.util.LeagueSystem.levelProgress(prefs.focusXp),
                leagueTier = prefs.leagueTier,
                xpBreakdown = breakdown,
                dailyLimit = prefs.dailyLimit
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileUiState()
    )


}

private data class ProfilePrefSnapshot(
    val isLoggedIn: Boolean,
    val displayName: String,
    val username: String,
    val bio: String,
    val avatar: String,
    val email: String,
    val joinedOn: String,
    val dailyLimit: Int,
    val focusXp: Int,
    val mathFocusMinutes: Int,
    val breatheMinutes: Int,
    val weeklyXp: Int,
    val leagueTier: String
)
