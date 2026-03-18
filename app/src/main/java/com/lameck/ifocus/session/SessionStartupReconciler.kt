package com.lameck.ifocus.session

import android.os.SystemClock
import com.lameck.ifocus.data.FocusRepository
import com.lameck.ifocus.ui.ActiveSession

/**
 * Reconciles persisted active-session state when app/process starts from a non-UI entrypoint.
 */
class SessionStartupReconciler(
    private val repository: FocusRepository,
    private val sessionAlarmScheduler: SessionAlarmScheduler,
    private val elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() }
) {

    suspend fun reconcileAndReschedule() {
        val activeSession = repository.loadActiveSession() ?: return
        val status = activeSession.toSessionStatus()

        if (status == SessionStatus.PAUSED_FOCUS || status == SessionStatus.PAUSED_BREAK) {
            // Paused sessions should never have a completion alarm.
            sessionAlarmScheduler.cancel()
            return
        }

        if (isExpired(activeSession)) {
            repository.clearActiveSession()
            sessionAlarmScheduler.cancel()
            return
        }

        val taskTitle = repository.loadTasks().firstOrNull { it.id == activeSession.taskId }?.title
        sessionAlarmScheduler.schedule(activeSession.scheduledCompletionMs, taskTitle)
    }

    private fun isExpired(activeSession: ActiveSession): Boolean {
        return activeSession.scheduledCompletionMs <= elapsedRealtimeProvider()
    }
}


