package com.lameck.ifocus.data

import com.lameck.ifocus.ui.FocusTask
import com.lameck.ifocus.ui.InterruptionReason
import com.lameck.ifocus.ui.ActiveSession
import com.lameck.ifocus.ui.AppSettings
import com.lameck.ifocus.ui.SessionRecord

interface FocusRepository {
    suspend fun loadTasks(): List<FocusTask>
    suspend fun loadSessionHistory(limit: Int = 50): List<SessionRecord>
    suspend fun loadInterruptionCounts(): Map<InterruptionReason, Int>
    suspend fun upsertTask(task: FocusTask)
    suspend fun upsertTasks(tasks: List<FocusTask>)
    suspend fun insertSession(record: SessionRecord)
    suspend fun upsertInterruptionCounts(counts: Map<InterruptionReason, Int>)
    suspend fun loadActiveSession(): ActiveSession?
    suspend fun upsertActiveSession(session: ActiveSession)
    suspend fun clearActiveSession()

    suspend fun loadAppSettings(): AppSettings = AppSettings()

    suspend fun upsertAppSettings(settings: AppSettings) = Unit
}

