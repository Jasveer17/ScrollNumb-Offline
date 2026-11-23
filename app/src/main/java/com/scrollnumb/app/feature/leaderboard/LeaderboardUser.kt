package com.scrollnumb.app.feature.leaderboard

data class LeaderboardUser(
    val userId: String = "",
    val displayName: String = "Anonymous",
    val username: String = "@anonymous",
    val avatar: String = "",
    // xp == weeklyXp (competition layer used for ranking).
    val xp: Int = 0,
    val weeklyXp: Int = 0,
    val totalXp: Int = 0,
    val level: Int = 0,
    val leagueTier: String = "Bronze",
    val gender: String = "Male",
    // Competition week this row's xp belongs to — used to drop stale rows
    // (e.g. legacy uids with no Users doc that weeklyReset never touches).
    val weekId: String = "",
    val weeklyScrolls: Int = 0,
    val rankScore: Int = 0,
    // Reels scrolled on scrollDate (competition day, IST). A stale date is
    // unknown, not a measured zero-scroll day.
    val todayScrolls: Int = 0,
    val scrollDate: String = "",
    val todayMeasured: Boolean = false,
    val todayLimitExceeded: Boolean = false,
    val competitionEligible: Boolean = true,
    val competitionStatus: String = "active",
    val competitionRejoinWeekId: String = ""
)
