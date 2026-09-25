package com.bharath.focusguard.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bharath.focusguard.data.local.dao.*
import com.bharath.focusguard.data.local.entities.*

@Database(
    entities = [MonitoredApp::class, DailyUsage::class, SessionState::class, NotionTask::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun dailyUsageDao(): DailyUsageDao
    abstract fun sessionStateDao(): SessionStateDao
    abstract fun notionTaskDao(): NotionTaskDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focusguard.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
