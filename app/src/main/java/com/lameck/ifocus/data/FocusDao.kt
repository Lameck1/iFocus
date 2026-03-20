package com.lameck.ifocus.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_tasks")
    suspend fun getTasks(): List<FocusTaskEntity>

    @Query("SELECT * FROM session_records ORDER BY createdAtEpochMs DESC LIMIT :limit")
    suspend fun getSessionHistory(limit: Int): List<SessionRecordEntity>

    @Query("SELECT * FROM interruption_counts")
    suspend fun getInterruptionCounts(): List<InterruptionCountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: FocusTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(tasks: List<FocusTaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(record: SessionRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInterruptionCounts(counts: List<InterruptionCountEntity>)

    @Query("DELETE FROM interruption_counts")
    suspend fun clearInterruptionCounts()

    @Query("SELECT * FROM active_session WHERE id = 'singleton' LIMIT 1")
    suspend fun getActiveSession(): ActiveSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActiveSession(session: ActiveSessionEntity)

    @Query("DELETE FROM active_session WHERE id = 'singleton'")
    suspend fun clearActiveSession()

    @Query("SELECT * FROM app_settings WHERE id = 'singleton' LIMIT 1")
    suspend fun getAppSettings(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppSettings(settings: AppSettingsEntity)

    @Query("SELECT * FROM focus_projects")
    suspend fun getProjects(): List<FocusProjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: FocusProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProjects(projects: List<FocusProjectEntity>)

    @Query("DELETE FROM focus_projects WHERE name = :name")
    suspend fun deleteProject(name: String)
}

