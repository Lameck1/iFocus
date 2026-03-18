package com.lameck.ifocus.session.usecase

import android.os.SystemClock
import com.lameck.ifocus.data.FocusRepository
import com.lameck.ifocus.session.SessionAlarmScheduler
import com.lameck.ifocus.session.SessionForegroundController

class ResumeSessionUseCase(
    private val repository: FocusRepository,
    private val alarmScheduler: SessionAlarmScheduler,
    private val foregroundController: SessionForegroundController,
    private val elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() }
) {

    suspend operator fun invoke(): SessionActionResult {
        val activeSession = repository.loadActiveSession() ?: return SessionActionResult.NoChange
        if (!activeSession.isPaused) return SessionActionResult.NoChange

        val remainingSec = activeSession.pausedRemainingSecs ?: return SessionActionResult.NoChange
        val nowMs = elapsedRealtimeProvider()
        val completionMs = nowMs + remainingSec * ONE_SECOND_MS

        val resumed = activeSession.copy(
            isPaused = false,
            pausedAtMs = null,
            pausedRemainingSecs = null,
            startedAtMs = nowMs,
            scheduledCompletionMs = completionMs
        )

        repository.upsertActiveSession(resumed)
        alarmScheduler.schedule(completionMs, resolveTaskTitle(activeSession.taskId))

        foregroundController.showRunning(
            taskTitle = resolveTaskTitle(activeSession.taskId),
            modeName = activeSession.mode.name,
            remainingSeconds = remainingSec
        )

        return SessionActionResult.Updated(activeSession = resumed)
    }

    private suspend fun resolveTaskTitle(taskId: String): String? {
        return repository.loadTasks().firstOrNull { it.id == taskId }?.title
    }

    private companion object {
        const val ONE_SECOND_MS = 1_000L
    }
}

