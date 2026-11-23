package com.scrollnumb.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scroll_events")
data class ScrollEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val platform: String,
    val sessionId: String,
    val globalCountAtEvent: Int
)

@Entity(tableName = "app_sessions")
data class AppSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val platform: String,
    val startTime: Long,
    val endTime: Long,
    val totalTimeMs: Long
)

@Entity(tableName = "daily_stats")
data class DailyStatsEntity(
    @PrimaryKey val date: String,
    val totalScrolls: Int = 0,
    val instagramScrolls: Int = 0,
    val youtubeScrolls: Int = 0,
    val facebookScrolls: Int = 0,
    val snapchatScrolls: Int = 0,
    val tiktokScrolls: Int = 0,
    val linkedinScrolls: Int = 0,
    val sessionCount: Int = 0,
    val totalScrollTimeMinutes: Int = 0,
    val totalScrollTimeSeconds: Int = 0,
    val limitWasExceeded: Boolean = false,
    val strikeEarned: Boolean = false,
    val strikeCleared: Boolean = false
)

@Entity(tableName = "streak_status")
data class StreakEntity(
    @PrimaryKey val id: Int = 1,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val strikesThisWeek: Int = 0,
    val freezeTokens: Int = 0,
    val lastUpdatedDate: String = ""
)

@Entity(tableName = "ai_insights")
data class AIInsightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val type: String,
    val title: String,
    val message: String,
    val severity: String
)

@Entity(tableName = "mood_entries")
data class MoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val moodScore: Int,
    val type: String,
    val linkedSessionId: String? = null
)

@Entity(tableName = "player_profile")
data class PlayerProfileEntity(
    @PrimaryKey val id: Int = 1,
    val totalXP: Int = 0,
    val currentLevel: Int = 1,
    val currentTitle: String = "Scroll Worm",
    val badgesEarned: String = "[]",
    val themeUnlocked: String = "[]"
)
