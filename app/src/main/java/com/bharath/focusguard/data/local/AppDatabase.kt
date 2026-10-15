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
    entities = [MonitoredApp::class, DailyUsage::class, SessionState::class, NotionTask::class,
        BlockWindow::class, WindowLog::class, DailySummary::class, AppDaily::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun dailyUsageDao(): DailyUsageDao
    abstract fun sessionStateDao(): SessionStateDao
    abstract fun notionTaskDao(): NotionTaskDao
    abstract fun blockWindowDao(): BlockWindowDao
    abstract fun windowLogDao(): WindowLogDao
    abstract fun dailySummaryDao(): DailySummaryDao
    abstract fun appDailyDao(): AppDailyDao

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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS block_windows (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, daysMask INTEGER NOT NULL, startMinute INTEGER NOT NULL, endMinute INTEGER NOT NULL, enabled INTEGER NOT NULL, oneOffUntilEpochMs INTEGER)")
                db.execSQL("CREATE TABLE IF NOT EXISTS window_log (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, windowId INTEGER NOT NULL, dateEpochDay INTEGER NOT NULL, endedEarly INTEGER NOT NULL, breaksUsed INTEGER NOT NULL, breakUntilEpochMs INTEGER)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS daily_summary (dateEpochDay INTEGER NOT NULL PRIMARY KEY, budgetMinutes INTEGER NOT NULL, usedMinutes INTEGER NOT NULL, emergencyPassUsed INTEGER NOT NULL, windowsCompleted INTEGER NOT NULL, windowsEndedEarly INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS app_daily (dateEpochDay INTEGER NOT NULL, packageName TEXT NOT NULL, minutes INTEGER NOT NULL, PRIMARY KEY(dateEpochDay, packageName))")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focusguard.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build().also { INSTANCE = it }
            }
    }
}
