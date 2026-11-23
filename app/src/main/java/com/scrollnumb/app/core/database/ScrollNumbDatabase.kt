package com.scrollnumb.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.scrollnumb.app.core.database.dao.*
import com.scrollnumb.app.core.database.entity.*

@Database(
    entities = [
        ScrollEventEntity::class,
        DailyStatsEntity::class,
        StreakEntity::class,
        AIInsightEntity::class,
        MoodEntryEntity::class,
        PlayerProfileEntity::class,
        AppSessionEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class ScrollNumbDatabase : RoomDatabase() {
    abstract fun scrollEventDao(): ScrollEventDao
    abstract fun dailyStatsDao(): DailyStatsDao
    abstract fun streakDao(): StreakDao
    abstract fun aiInsightDao(): AIInsightDao
    abstract fun moodEntryDao(): MoodEntryDao
    abstract fun playerProfileDao(): PlayerProfileDao
    abstract fun appSessionDao(): AppSessionDao
}
