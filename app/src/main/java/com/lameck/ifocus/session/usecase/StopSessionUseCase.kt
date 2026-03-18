package com.lameck.ifocus.session.usecase

import com.lameck.ifocus.data.FocusRepository
import com.lameck.ifocus.session.SessionAlarmScheduler
import com.lameck.ifocus.session.SessionForegroundController

class StopSessionUseCase(
    private val repository: FocusRepository,
    private val alarmScheduler: SessionAlarmScheduler,
    private val foregroundController: SessionForegroundController
) {

    suspend operator fun invoke(): SessionActionResult {
        repository.clearActiveSession()
        alarmScheduler.cancel()
        foregroundController.stop()
        return SessionActionResult.Updated(activeSession = null)
    }
}


