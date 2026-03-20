package com.lameck.ifocus.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lameck.ifocus.data.FocusRepositoryProvider
import kotlinx.coroutines.runBlocking

class SessionControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val appContext = context.applicationContext

        runBlocking {
            val coordinator = SessionControlCoordinator(
                repository = FocusRepositoryProvider.get(appContext),
                alarmScheduler = AndroidSessionAlarmScheduler(appContext),
                foregroundController = AndroidSessionForegroundController(appContext)
            )

            when (action) {
                FocusSessionForegroundService.ACTION_CONTROL_PAUSE -> coordinator.pauseActiveSession()
                FocusSessionForegroundService.ACTION_CONTROL_RESUME -> coordinator.resumePausedSession()
                FocusSessionForegroundService.ACTION_CONTROL_STOP -> coordinator.stopSession()
                FocusSessionForegroundService.ACTION_CONTROL_START_FOCUS -> coordinator.startFocusSession()
            }
        }
    }
}

