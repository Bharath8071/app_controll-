package com.bharath.focusguard.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bharath.focusguard.data.local.dao.*
import com.bharath.focusguard.data.local.entities.*

@Database(
    entities = [MonitoredApp::class, DailyUsage::class, SessionState::class, NotionTask::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun dailyUsageDao(): DailyUsageDao
    abstract fun sessionStateDao(): SessionStateDao
    abstract fun notionTaskDao(): NotionTaskDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /**
         * BUG-003 fix: Replace fallbackToDestructiveMigration() with explicit migrations.
         * Each migration is additive (new tables / columns) so user data is always preserved.
         * fallbackToDestructiveMigration() silently wiped ALL user data on any schema bump.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 2 added the session_state table
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS session_state (
                        packageName TEXT NOT NULL PRIMARY KEY,
                        sessionStartTimeMillis INTEGER NOT NULL,
                        sessionLengthMinutes INTEGER NOT NULL,
                        sessionExpiresAtMillis INTEGER NOT NULL,
                        lastResumedAtMillis INTEGER NOT NULL,
                        isActive INTEGER NOT NULL DEFAULT 1
                    )"""
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 3 added the notion_tasks table
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS notion_tasks (
                        notionPageId TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        isChecked INTEGER NOT NULL DEFAULT 0,
                        lastSyncedAt INTEGER NOT NULL,
                        priorityRank INTEGER NOT NULL DEFAULT 4
                    )"""
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 4 added extendUsedToday column to daily_usage
                db.execSQL(
                    "ALTER TABLE daily_usage ADD COLUMN extendUsedToday INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focusguard.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { INSTANCE = it }
            }
    }
}
