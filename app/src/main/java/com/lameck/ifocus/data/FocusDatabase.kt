package com.lameck.ifocus.data

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FocusTaskEntity::class,
        FocusProjectEntity::class,
        SessionRecordEntity::class,
        InterruptionCountEntity::class,
        ActiveSessionEntity::class,
        AppSettingsEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class FocusDatabase : RoomDatabase() {
    abstract fun focusDao(): FocusDao

    companion object {
        @Volatile
        private var INSTANCE: FocusDatabase? = null

        fun getInstance(context: Context): FocusDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FocusDatabase::class.java,
                    "focus_db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7
                    )
                    .build().also { db ->
                    INSTANCE = db
                }
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS interruption_counts (
                        reason TEXT NOT NULL,
                        count INTEGER NOT NULL,
                        PRIMARY KEY(reason)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS active_session (
                        id TEXT NOT NULL,
                        taskId TEXT NOT NULL,
                        mode TEXT NOT NULL,
                        startedAtMs INTEGER NOT NULL,
                        scheduledCompletionMs INTEGER NOT NULL,
                        isPaused INTEGER NOT NULL DEFAULT 0,
                        pausedAtMs INTEGER NOT NULL DEFAULT 0,
                        pausedRemainingSecs INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_settings (
                        id TEXT NOT NULL,
                        themePreference TEXT NOT NULL DEFAULT 'SYSTEM',
                        autoStartBreak INTEGER NOT NULL DEFAULT 0,
                        autoStartFocus INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO app_settings (id, themePreference, autoStartBreak, autoStartFocus)
                    VALUES ('singleton', 'SYSTEM', 0, 0)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE focus_tasks ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE focus_tasks ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE focus_tasks ADD COLUMN updatedAtEpochMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE focus_tasks
                    SET updatedAtEpochMs = CAST(strftime('%s','now') AS INTEGER) * 1000
                    WHERE updatedAtEpochMs = 0
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE focus_tasks ADD COLUMN projectName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE session_records ADD COLUMN taskId TEXT")
                db.execSQL("ALTER TABLE session_records ADD COLUMN projectName TEXT")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN dailyGoalMinutes INTEGER NOT NULL DEFAULT 120")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS focus_projects (
                        name TEXT NOT NULL,
                        isArchived INTEGER NOT NULL DEFAULT 0,
                        updatedAtEpochMs INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(name)
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE focus_tasks ADD COLUMN plannedDateEpochDay INTEGER")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN calendarSafePlanningEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN hasCompletedOnboarding INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO focus_projects(name, isArchived, updatedAtEpochMs)
                    SELECT DISTINCT projectName, 0, CAST(strftime('%s','now') AS INTEGER) * 1000
                    FROM focus_tasks
                    WHERE projectName IS NOT NULL AND projectName != ''
                    """.trimIndent()
                )
            }
        }
    }
}

