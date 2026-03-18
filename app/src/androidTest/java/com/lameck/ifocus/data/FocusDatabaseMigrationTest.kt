package com.lameck.ifocus.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusDatabaseMigrationTest {

    private val dbName = "focus_migration_test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FocusDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2_createsInterruptionTable_andKeepsExistingData() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS focus_tasks (
                    id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    estimateMinutes INTEGER NOT NULL,
                    priority TEXT NOT NULL,
                    status TEXT NOT NULL,
                    todayMinutesFocused INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS session_records (
                    id TEXT NOT NULL,
                    taskTitle TEXT NOT NULL,
                    focusedMinutes INTEGER NOT NULL,
                    outcome TEXT NOT NULL,
                    createdAtEpochMs INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO focus_tasks (id, title, estimateMinutes, priority, status, todayMinutesFocused)
                VALUES ('task-1', 'Migration task', 50, 'P1', 'IN_PROGRESS', 20)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO session_records (id, taskTitle, focusedMinutes, outcome, createdAtEpochMs)
                VALUES ('session-1', 'Migration task', 50, 'DONE', 1000)
                """.trimIndent()
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(dbName, 2, true, FocusDatabase.MIGRATION_1_2)

        assertEquals(1, count(migratedDb, "SELECT COUNT(*) FROM focus_tasks WHERE id = 'task-1'"))
        assertEquals(1, count(migratedDb, "SELECT COUNT(*) FROM session_records WHERE id = 'session-1'"))
        assertEquals(0, count(migratedDb, "SELECT COUNT(*) FROM interruption_counts"))

        migratedDb.close()
    }

    @Test
    fun migrate2To3_createsActiveSessionTable_andKeepsExistingData() {
        helper.createDatabase(dbName, 2).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS focus_tasks (
                    id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    estimateMinutes INTEGER NOT NULL,
                    priority TEXT NOT NULL,
                    status TEXT NOT NULL,
                    todayMinutesFocused INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS session_records (
                    id TEXT NOT NULL,
                    taskTitle TEXT NOT NULL,
                    focusedMinutes INTEGER NOT NULL,
                    outcome TEXT NOT NULL,
                    createdAtEpochMs INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS interruption_counts (
                    reason TEXT NOT NULL,
                    count INTEGER NOT NULL,
                    PRIMARY KEY(reason)
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO focus_tasks (id, title, estimateMinutes, priority, status, todayMinutesFocused)
                VALUES ('task-2', 'Migration v2 task', 25, 'P2', 'PLANNED', 0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO session_records (id, taskTitle, focusedMinutes, outcome, createdAtEpochMs)
                VALUES ('session-2', 'Migration v2 task', 25, 'DONE', 2000)
                """.trimIndent()
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(dbName, 3, true, FocusDatabase.MIGRATION_2_3)

        assertEquals(1, count(migratedDb, "SELECT COUNT(*) FROM focus_tasks WHERE id = 'task-2'"))
        assertEquals(1, count(migratedDb, "SELECT COUNT(*) FROM session_records WHERE id = 'session-2'"))
        assertEquals(0, count(migratedDb, "SELECT COUNT(*) FROM active_session"))

        migratedDb.close()
    }

    @Test
    fun migrate3To4_createsAppSettingsTable_andKeepsExistingData() {
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS focus_tasks (
                    id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    estimateMinutes INTEGER NOT NULL,
                    priority TEXT NOT NULL,
                    status TEXT NOT NULL,
                    todayMinutesFocused INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS session_records (
                    id TEXT NOT NULL,
                    taskTitle TEXT NOT NULL,
                    focusedMinutes INTEGER NOT NULL,
                    outcome TEXT NOT NULL,
                    createdAtEpochMs INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS interruption_counts (
                    reason TEXT NOT NULL,
                    count INTEGER NOT NULL,
                    PRIMARY KEY(reason)
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS active_session (
                    id TEXT NOT NULL,
                    taskId TEXT NOT NULL,
                    mode TEXT NOT NULL,
                    startedAtMs INTEGER NOT NULL,
                    scheduledCompletionMs INTEGER NOT NULL,
                    isPaused INTEGER NOT NULL,
                    pausedAtMs INTEGER NOT NULL,
                    pausedRemainingSecs INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO focus_tasks (id, title, estimateMinutes, priority, status, todayMinutesFocused)
                VALUES ('task-3', 'Migration v3 task', 50, 'P1', 'IN_PROGRESS', 15)
                """.trimIndent()
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(dbName, 4, true, FocusDatabase.MIGRATION_3_4)

        assertEquals(1, count(migratedDb, "SELECT COUNT(*) FROM focus_tasks WHERE id = 'task-3'"))
        assertEquals(1, count(migratedDb, "SELECT COUNT(*) FROM app_settings WHERE id = 'singleton'"))

        migratedDb.close()
    }

    @Test
    fun migrate4To5_addsTaskMetadataColumns_andKeepsExistingData() {
        helper.createDatabase(dbName, 4).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS focus_tasks (
                    id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    estimateMinutes INTEGER NOT NULL,
                    priority TEXT NOT NULL,
                    status TEXT NOT NULL,
                    todayMinutesFocused INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO focus_tasks (id, title, estimateMinutes, priority, status, todayMinutesFocused)
                VALUES ('task-4', 'Migration v4 task', 45, 'P2', 'PLANNED', 5)
                """.trimIndent()
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(dbName, 5, true, FocusDatabase.MIGRATION_4_5)

        assertEquals(1, count(migratedDb, "SELECT COUNT(*) FROM focus_tasks WHERE id = 'task-4'"))
        assertEquals(1, count(migratedDb, "SELECT COUNT(*) FROM focus_tasks WHERE notes = ''"))
        assertEquals(1, count(migratedDb, "SELECT COUNT(*) FROM focus_tasks WHERE isArchived = 0"))

        migratedDb.close()
    }

    @Test
    fun migrate1To5_runsFullChain_andBackfillsTaskMetadata() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS focus_tasks (
                    id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    estimateMinutes INTEGER NOT NULL,
                    priority TEXT NOT NULL,
                    status TEXT NOT NULL,
                    todayMinutesFocused INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS session_records (
                    id TEXT NOT NULL,
                    taskTitle TEXT NOT NULL,
                    focusedMinutes INTEGER NOT NULL,
                    outcome TEXT NOT NULL,
                    createdAtEpochMs INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO focus_tasks (id, title, estimateMinutes, priority, status, todayMinutesFocused)
                VALUES ('task-chain', 'Migration chain task', 60, 'P1', 'IN_PROGRESS', 10)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO session_records (id, taskTitle, focusedMinutes, outcome, createdAtEpochMs)
                VALUES ('session-chain', 'Migration chain task', 25, 'DONE', 3000)
                """.trimIndent()
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            dbName,
            5,
            true,
            FocusDatabase.MIGRATION_1_2,
            FocusDatabase.MIGRATION_2_3,
            FocusDatabase.MIGRATION_3_4,
            FocusDatabase.MIGRATION_4_5
        )

        assertEquals(1, count(migratedDb, "SELECT COUNT(*) FROM focus_tasks WHERE id = 'task-chain'"))
        assertEquals(1, count(migratedDb, "SELECT COUNT(*) FROM session_records WHERE id = 'session-chain'"))
        assertEquals(
            1,
            count(
                migratedDb,
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'interruption_counts'"
            )
        )
        assertEquals(
            1,
            count(
                migratedDb,
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'active_session'"
            )
        )
        assertEquals(1, count(migratedDb, "SELECT COUNT(*) FROM app_settings WHERE id = 'singleton'"))
        assertEquals(1, count(migratedDb, "SELECT COUNT(*) FROM focus_tasks WHERE notes = ''"))
        assertEquals(1, count(migratedDb, "SELECT COUNT(*) FROM focus_tasks WHERE isArchived = 0"))

        val updatedAtEpochMs =
            migratedDb.query("SELECT updatedAtEpochMs FROM focus_tasks WHERE id = 'task-chain'").use {
                it.moveToFirst()
                it.getLong(0)
            }
        assertTrue(updatedAtEpochMs > 0L)

        migratedDb.close()
    }

    private fun count(db: SupportSQLiteDatabase, query: String): Int {
        db.query(query).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

}

