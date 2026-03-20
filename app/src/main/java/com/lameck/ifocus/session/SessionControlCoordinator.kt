package com.lameck.ifocus.session

import android.os.SystemClock
import com.lameck.ifocus.data.FocusRepository
import com.lameck.ifocus.ui.ActiveSession
import com.lameck.ifocus.ui.TimerMode
import com.lameck.ifocus.session.usecase.PauseSessionUseCase
import com.lameck.ifocus.session.usecase.ResumeSessionUseCase
import com.lameck.ifocus.session.usecase.StopSessionUseCase

/**
 * Executes session controls from notification actions even when UI is not in memory.
 */
class SessionControlCoordinator(
    private val repository: FocusRepository,
    private val alarmScheduler: SessionAlarmScheduler,
    private val foregroundController: SessionForegroundController,
    private val elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() }
) {

    private val pauseSessionUseCase by lazy {
        PauseSessionUseCase(
            repository = repository,
            alarmScheduler = alarmScheduler,
            foregroundController = foregroundController,
            elapsedRealtimeProvider = elapsedRealtimeProvider
        )
    }

    private val resumeSessionUseCase by lazy {
        ResumeSessionUseCase(
            repository = repository,
            alarmScheduler = alarmScheduler,
            foregroundController = foregroundController,
            elapsedRealtimeProvider = elapsedRealtimeProvider
        )
    }

    private val stopSessionUseCase by lazy {
        StopSessionUseCase(
            repository = repository,
            alarmScheduler = alarmScheduler,
            foregroundController = foregroundController
        )
    }

    suspend fun pauseActiveSession() {
        pauseSessionUseCase()
    }

    suspend fun resumePausedSession() {
        resumeSessionUseCase()
    }

    suspend fun stopSession() {
        stopSessionUseCase()
    }

    suspend fun startFocusSession(durationMinutes: Int = TimerMode.FOCUS.durationMinutes) {
        val existing = repository.loadActiveSession()
        if (existing != null) {
            if (existing.isPaused) {
                resumeSessionUseCase()
            }
            return
        }

        val task = repository.loadTasks().firstOrNull { !it.isArchived } ?: return
        val nowMs = elapsedRealtimeProvider()
        val focusDurationSeconds = durationMinutes.coerceIn(1, 180) * 60
        val completionMs = nowMs + (focusDurationSeconds * 1_000L)
        val session = ActiveSession(
            taskId = task.id,
            mode = TimerMode.FOCUS,
            startedAtMs = nowMs,
            scheduledCompletionMs = completionMs,
            isPaused = false
        )

        repository.upsertActiveSession(session)
        alarmScheduler.schedule(completionMs, task.title)
        foregroundController.showRunning(task.title, TimerMode.FOCUS.name, focusDurationSeconds)
    }

    suspend fun startDeepWorkSession() {
        startFocusSession(durationMinutes = 50)
    }
}


