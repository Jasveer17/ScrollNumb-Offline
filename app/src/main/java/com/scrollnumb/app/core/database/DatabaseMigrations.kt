package com.scrollnumb.app.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Versioned Room migrations. v3 is the exported baseline (see app/schemas).
 *
 * RULE: any change to an @Entity must bump ScrollNumbDatabase.version AND add a
 * Migration here. Never reintroduce fallbackToDestructiveMigration() — it wipes
 * users' local history silently. Example for the next bump:
 *
 *   val MIGRATION_3_4 = object : Migration(3, 4) {
 *       override fun migrate(db: SupportSQLiteDatabase) {
 *           db.execSQL("ALTER TABLE daily_stats ADD COLUMN newCol INTEGER NOT NULL DEFAULT 0")
 *       }
 *   }
 *
 * then add it to ALL below.
 */
object DatabaseMigrations {
    val ALL: Array<Migration> = arrayOf(
        // No migrations yet — v3 is the baseline.
    )
}
