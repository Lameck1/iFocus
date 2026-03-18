package com.lameck.ifocus.session

import android.os.SystemClock
import com.lameck.ifocus.data.FocusRepository
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
}


