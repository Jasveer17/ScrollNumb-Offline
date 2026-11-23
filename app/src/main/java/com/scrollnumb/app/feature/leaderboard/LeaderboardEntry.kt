package com.scrollnumb.app.feature.leaderboard

data class LeaderboardEntry(
    val id: String,
    val name: String,
    val username: String,
    val avatar: String,
    val xp: Int,          // weekly XP (competition)
    val level: Int = 0,
    val leagueTier: String = "Bronze",
    val gender: String = "Male",
    val weeklyScrolls: Int = 0,
    val rankScore: Int = 0,
    val todayScrolls: Int = 0,
    val todayMeasured: Boolean = false,
    val todayLimitExceeded: Boolean = false,
    val competitionEligible: Boolean = true,
    val competitionStatus: String = "active",
    val competitionRejoinWeekId: String = "",
    val isMe: Boolean = false
)

internal fun displayedLeaderboardRank(entries: List<LeaderboardEntry>): Int {
    val displayedIndex = entries.indexOfFirst { it.isMe }
    return if (displayedIndex >= 0) displayedIndex + 1 else 0
}
