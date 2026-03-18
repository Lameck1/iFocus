package com.lameck.ifocus.session

import android.os.SystemClock
import com.lameck.ifocus.data.ActiveSessionEntity
import com.lameck.ifocus.data.FocusDao

/**
 * Recovers an active session after app process death or restart.
 * Recomputes remaining time from persisted timestamps to ensure accuracy.
 */
class SessionRecoveryManager(private val dao: FocusDao) {

    /**
     * Recover session on app startup. Returns null if no active session or session expired.
     */
    suspend fun recoverSessionOnStartup(): ActiveSessionEntity? {
        val persisted = dao.getActiveSession() ?: return null

        // Recompute remaining time from persisted timestamps
        val nowMs = SystemClock.elapsedRealtime()
        val remainingMs = (persisted.scheduledCompletionMs - nowMs).coerceAtLeast(0L)

        // If session already completed, clear it and return null
        if (remainingMs <= 0) {
            dao.clearActiveSession()
            return null
        }

        // Session still valid; update scheduled completion to account for elapsed time
        // (In case the timer needs to be resumed from where it was paused)
        return if (persisted.isPaused) {
            // Keep paused state as-is
            persisted
        } else {
            // Reschedule completion for the remaining time
            persisted.copy(
                scheduledCompletionMs = nowMs + remainingMs
            )
        }
    }

    /**
     * Compute remaining seconds from an active session.
     */
    fun computeRemainingSecs(session: ActiveSessionEntity): Int {
        val nowMs = SystemClock.elapsedRealtime()
        val remainingMs = if (session.isPaused) {
            // Use paused remaining time
            session.pausedRemainingSecs.toLong() * 1000
        } else {
            // Compute from scheduled completion
            (session.scheduledCompletionMs - nowMs).coerceAtLeast(0L)
        }
        return (remainingMs / 1000).toInt()
    }
}

