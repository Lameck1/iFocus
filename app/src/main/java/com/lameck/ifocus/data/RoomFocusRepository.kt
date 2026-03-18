package com.lameck.ifocus.data

import com.lameck.ifocus.ui.FocusTask
import com.lameck.ifocus.ui.InterruptionReason
import com.lameck.ifocus.ui.ActiveSession
import com.lameck.ifocus.ui.AppSettings
import com.lameck.ifocus.ui.SessionRecord
import com.lameck.ifocus.ui.ThemePreference
import com.lameck.ifocus.session.toTimerModeOrDefault

class RoomFocusRepository(
    private val dao: FocusDao
) : FocusRepository {
    override suspend fun loadTasks(): List<FocusTask> = dao.getTasks().map { it.toDomain() }

    override suspend fun loadSessionHistory(limit: Int): List<SessionRecord> {
        return dao.getSessionHistory(limit).map { it.toDomain() }.asReversed()
    }

    override suspend fun loadInterruptionCounts(): Map<InterruptionReason, Int> {
        return dao.getInterruptionCounts().associate { it.toDomain() }
    }

    override suspend fun upsertTask(task: FocusTask) {
        dao.upsertTask(task.toEntity())
    }

    override suspend fun upsertTasks(tasks: List<FocusTask>) {
        dao.upsertTasks(tasks.map { it.toEntity() })
    }

    override suspend fun insertSession(record: SessionRecord) {
        dao.insertSession(record.toEntity())
    }

    override suspend fun upsertInterruptionCounts(counts: Map<InterruptionReason, Int>) {
        dao.clearInterruptionCounts()
        dao.upsertInterruptionCounts(
            counts.map { (reason, count) ->
                InterruptionCountEntity(reason = reason.name, count = count)
            }
        )
    }

    override suspend fun loadActiveSession(): ActiveSession? {
        return dao.getActiveSession()?.toDomain()
    }

    override suspend fun upsertActiveSession(session: ActiveSession) {
        dao.upsertActiveSession(session.toEntity())
    }

    override suspend fun clearActiveSession() {
        dao.clearActiveSession()
    }

    override suspend fun loadAppSettings(): AppSettings {
        return dao.getAppSettings()?.toDomain() ?: AppSettings()
    }

    override suspend fun upsertAppSettings(settings: AppSettings) {
        dao.upsertAppSettings(settings.toEntity())
    }
}

private fun FocusTaskEntity.toDomain(): FocusTask = FocusTask(
    id = id,
    title = title,
    estimateMinutes = estimateMinutes,
    priority = enumValueOf(priority),
    status = enumValueOf(status),
    todayMinutesFocused = todayMinutesFocused,
    notes = notes,
    isArchived = isArchived,
    updatedAtEpochMs = updatedAtEpochMs
)

private fun SessionRecordEntity.toDomain(): SessionRecord = SessionRecord(
    id = id,
    taskTitle = taskTitle,
    focusedMinutes = focusedMinutes,
    outcome = enumValueOf(outcome),
    createdAtEpochMs = createdAtEpochMs
)

private fun FocusTask.toEntity(): FocusTaskEntity = FocusTaskEntity(
    id = id,
    title = title,
    estimateMinutes = estimateMinutes,
    priority = priority.name,
    status = status.name,
    todayMinutesFocused = todayMinutesFocused,
    notes = notes,
    isArchived = isArchived,
    updatedAtEpochMs = updatedAtEpochMs
)

private fun SessionRecord.toEntity(): SessionRecordEntity = SessionRecordEntity(
    id = id,
    taskTitle = taskTitle,
    focusedMinutes = focusedMinutes,
    outcome = outcome.name,
    createdAtEpochMs = createdAtEpochMs
)

private fun ActiveSessionEntity.toDomain(): ActiveSession = ActiveSession(
    id = id,
    taskId = taskId,
    mode = mode.toTimerModeOrDefault(),
    startedAtMs = startedAtMs,
    scheduledCompletionMs = scheduledCompletionMs,
    isPaused = isPaused,
    pausedAtMs = pausedAtMs.takeIf { isPaused },
    pausedRemainingSecs = pausedRemainingSecs.takeIf { isPaused }
)

private fun ActiveSession.toEntity(): ActiveSessionEntity = ActiveSessionEntity(
    id = id,
    taskId = taskId,
    mode = mode.name,
    startedAtMs = startedAtMs,
    scheduledCompletionMs = scheduledCompletionMs,
    isPaused = isPaused,
    pausedAtMs = pausedAtMs ?: 0L,
    pausedRemainingSecs = pausedRemainingSecs ?: 0
)

private fun AppSettingsEntity.toDomain(): AppSettings = AppSettings(
    themePreference = ThemePreference.entries.firstOrNull { it.name == themePreference } ?: ThemePreference.SYSTEM,
    autoStartBreak = autoStartBreak,
    autoStartFocus = autoStartFocus
)

private fun AppSettings.toEntity(): AppSettingsEntity = AppSettingsEntity(
    id = "singleton",
    themePreference = themePreference.name,
    autoStartBreak = autoStartBreak,
    autoStartFocus = autoStartFocus
)

private fun InterruptionCountEntity.toDomain(): Pair<InterruptionReason, Int> {
    return enumValueOf<InterruptionReason>(reason) to count
}

