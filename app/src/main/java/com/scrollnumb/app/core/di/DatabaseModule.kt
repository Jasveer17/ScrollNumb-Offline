package com.scrollnumb.app.core.di

import android.content.Context
import androidx.room.Room
import com.scrollnumb.app.core.database.DatabaseMigrations
import com.scrollnumb.app.core.database.ScrollNumbDatabase
import com.scrollnumb.app.core.database.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ScrollNumbDatabase {
        return Room.databaseBuilder(
            context,
            ScrollNumbDatabase::class.java,
            "scrollnumb_db"
        )
        // No more blanket destructive migration — that silently wiped users'
        // local history on every schema change. From v3 onward, each version
        // bump MUST ship a real Migration in DatabaseMigrations.ALL or the app
        // fails loudly at build/runtime instead of deleting data.
        .addMigrations(*DatabaseMigrations.ALL)
        // Legacy pre-baseline builds (v1/v2, schemas never exported, so not
        // reconstructable) are the one unavoidable exception — and cloud
        // restore now backfills daily stats for them too.
        .fallbackToDestructiveMigrationFrom(1, 2)
        // A downgrade only happens on dev devices running an older build; never
        // in production from the Play Store. Safe to wipe rather than crash.
        .fallbackToDestructiveMigrationOnDowngrade()
        .build()
    }

    @Provides fun provideScrollEventDao(db: ScrollNumbDatabase): ScrollEventDao = db.scrollEventDao()
    @Provides fun provideDailyStatsDao(db: ScrollNumbDatabase): DailyStatsDao = db.dailyStatsDao()
    @Provides fun provideStreakDao(db: ScrollNumbDatabase): StreakDao = db.streakDao()
    @Provides fun provideAIInsightDao(db: ScrollNumbDatabase): AIInsightDao = db.aiInsightDao()
    @Provides fun provideMoodEntryDao(db: ScrollNumbDatabase): MoodEntryDao = db.moodEntryDao()
    @Provides fun providePlayerProfileDao(db: ScrollNumbDatabase): PlayerProfileDao = db.playerProfileDao()
    @Provides fun provideAppSessionDao(db: ScrollNumbDatabase): AppSessionDao = db.appSessionDao()
}
