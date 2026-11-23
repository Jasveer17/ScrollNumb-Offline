package com.scrollnumb.app.core.database.dao

import androidx.room.*
import com.scrollnumb.app.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScrollEventDao {
    @Insert
    suspend fun insertEvent(event: ScrollEventEntity)

    @Query("SELECT COUNT(*) FROM scroll_events WHERE date(timestamp/1000, 'unixepoch', 'localtime') = :date")
    fun getEventCountForDate(date: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM scroll_events WHERE date(timestamp/1000, 'unixepoch', 'localtime') = :date AND platform = :platform")
    fun getEventCountForDateAndPlatform(date: String, platform: String): Flow<Int>

    @Query("SELECT * FROM scroll_events WHERE date(timestamp/1000, 'unixepoch', 'localtime') = :date ORDER BY timestamp DESC")
    suspend fun getEventsForDate(date: String): List<ScrollEventEntity>

    @Query("SELECT * FROM scroll_events WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp")
    suspend fun getEventsInTimeRange(startTime: Long, endTime: Long): List<ScrollEventEntity>

    @Query("DELETE FROM scroll_events WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)

    @Query("DELETE FROM scroll_events WHERE id = (SELECT id FROM scroll_events WHERE platform = :platform ORDER BY timestamp DESC LIMIT 1)")
    suspend fun deleteLatestEvent(platform: String)

    @Query("SELECT platform FROM scroll_events WHERE date(timestamp/1000, 'unixepoch', 'localtime') = :date ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestEventPlatformForDate(date: String): String?
}

@Dao
interface DailyStatsDao {
    @Upsert
    suspend fun insertOrUpdate(stats: DailyStatsEntity)

    @Query("SELECT * FROM daily_stats WHERE date = :date")
    suspend fun getStatsForDate(date: String): DailyStatsEntity?

    @Query("SELECT * FROM daily_stats WHERE date = :date")
    fun getStatsForDateFlow(date: String): Flow<DailyStatsEntity?>

    @Query("SELECT * FROM daily_stats WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getStatsForDateRange(startDate: String, endDate: String): List<DailyStatsEntity>

    @Query("SELECT * FROM daily_stats ORDER BY date DESC LIMIT :limit")
    suspend fun getLatestStats(limit: Int = 30): List<DailyStatsEntity>

    @Query("SELECT COALESCE(SUM(totalScrolls), 0) FROM daily_stats")
    fun getTotalScrollsFlow(): Flow<Int>

    @Query("SELECT COALESCE(SUM(totalScrolls), 0) FROM daily_stats")
    suspend fun getTotalScrollsSync(): Int

    @Query("SELECT COALESCE(SUM(totalScrolls), 0) FROM daily_stats WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getScrollsInRange(startDate: String, endDate: String): Int

    @Query("SELECT * FROM daily_stats")
    suspend fun getAllStats(): List<DailyStatsEntity>
}

@Dao
interface StreakDao {
    @Query("SELECT * FROM streak_status WHERE id = 1")
    fun getStreak(): Flow<StreakEntity?>

    @Query("SELECT * FROM streak_status WHERE id = 1")
    suspend fun getStreakSync(): StreakEntity?

    @Upsert
    suspend fun insertOrUpdate(streak: StreakEntity)
}

@Dao
interface AIInsightDao {
    @Insert
    suspend fun insert(insight: AIInsightEntity)

    @Query("SELECT * FROM ai_insights WHERE date = :date ORDER BY id DESC")
    suspend fun getInsightsForDate(date: String): List<AIInsightEntity>

    @Query("SELECT * FROM ai_insights ORDER BY id DESC LIMIT :limit")
    fun getLatestInsights(limit: Int = 5): Flow<List<AIInsightEntity>>

    @Query("DELETE FROM ai_insights WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)
}

@Dao
interface MoodEntryDao {
    @Insert
    suspend fun insert(entry: MoodEntryEntity)

    @Query("SELECT * FROM mood_entries WHERE date(timestamp/1000, 'unixepoch', 'localtime') = :date")
    suspend fun getEntriesForDate(date: String): List<MoodEntryEntity>

    @Query("SELECT AVG(moodScore) FROM mood_entries WHERE type = :type")
    suspend fun getAverageMoodByType(type: String): Float?

    @Query("SELECT * FROM mood_entries ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEntries(limit: Int = 50): List<MoodEntryEntity>
}

@Dao
interface PlayerProfileDao {
    @Query("SELECT * FROM player_profile WHERE id = 1")
    fun getProfile(): Flow<PlayerProfileEntity?>

    @Query("SELECT * FROM player_profile WHERE id = 1")
    suspend fun getProfileSync(): PlayerProfileEntity?

    @Upsert
    suspend fun insertOrUpdate(profile: PlayerProfileEntity)
}

@Dao
interface AppSessionDao {
    @Insert
    suspend fun insertSession(session: AppSessionEntity)

    @Query("SELECT * FROM app_sessions WHERE date = :date")
    suspend fun getSessionsForDate(date: String): List<AppSessionEntity>

    @Query("SELECT SUM(totalTimeMs) FROM app_sessions WHERE date = :date")
    fun getTotalTimeForDateFlow(date: String): Flow<Long?>
}
