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
        SessionRecordEntity::class,
        InterruptionCountEntity::class,
        ActiveSessionEntity::class,
        AppSettingsEntity::class
    ],
    version = 5,
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
    }
}

