package com.lameck.ifocus.session.usecase

import android.os.SystemClock
import com.lameck.ifocus.data.FocusRepository
import com.lameck.ifocus.session.SessionAlarmScheduler
import com.lameck.ifocus.session.SessionForegroundController

class PauseSessionUseCase(
    private val repository: FocusRepository,
    private val alarmScheduler: SessionAlarmScheduler,
    private val foregroundController: SessionForegroundController,
    private val elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() }
) {

    suspend operator fun invoke(): SessionActionResult {
        val activeSession = repository.loadActiveSession() ?: return SessionActionResult.NoChange
        if (activeSession.isPaused) return SessionActionResult.NoChange

        val nowMs = elapsedRealtimeProvider()
        val remainingSec = toRemainingSeconds(activeSession.scheduledCompletionMs, nowMs)
        if (remainingSec <= 0) {
            repository.clearActiveSession()
            alarmScheduler.cancel()
            foregroundController.stop()
            return SessionActionResult.Updated(activeSession = null)
        }

        val paused = activeSession.copy(
            isPaused = true,
            pausedAtMs = nowMs,
            pausedRemainingSecs = remainingSec
        )
        repository.upsertActiveSession(paused)
        alarmScheduler.cancel()

        foregroundController.showPaused(
            taskTitle = resolveTaskTitle(activeSession.taskId),
            modeName = activeSession.mode.name,
            remainingSeconds = remainingSec
        )
        return SessionActionResult.Updated(activeSession = paused)
    }

    private suspend fun resolveTaskTitle(taskId: String): String? {
        return repository.loadTasks().firstOrNull { it.id == taskId }?.title
    }

    private fun toRemainingSeconds(completionMs: Long, nowMs: Long): Int {
        val remainingMs = (completionMs - nowMs).coerceAtLeast(0L)
        return ((remainingMs + ONE_SECOND_MS - 1) / ONE_SECOND_MS).toInt()
    }

    private companion object {
        const val ONE_SECOND_MS = 1_000L
    }
}

