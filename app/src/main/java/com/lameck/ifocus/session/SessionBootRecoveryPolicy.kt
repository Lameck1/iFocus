package com.lameck.ifocus.session

import android.content.Intent
import com.lameck.ifocus.data.FocusRepository

/**
 * Applies boot-time recovery rules for persisted session state.
 */
class SessionBootRecoveryPolicy(
    private val repository: FocusRepository,
    private val sessionAlarmScheduler: SessionAlarmScheduler,
    private val sessionForegroundController: SessionForegroundController,
    private val reconcileStartup: suspend () -> Unit
) {

    suspend fun handle(action: String) {
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                // elapsedRealtime-based timestamps are invalid after reboot, so clear persisted state.
                repository.clearActiveSession()
                sessionAlarmScheduler.cancel()
                sessionForegroundController.stop()
            }

            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                reconcileStartup()
            }
        }
    }
}


