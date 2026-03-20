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
                SessionControlActions.PAUSE -> coordinator.pauseActiveSession()
                SessionControlActions.RESUME -> coordinator.resumePausedSession()
                SessionControlActions.STOP -> coordinator.stopSession()
                SessionControlActions.START_FOCUS -> coordinator.startFocusSession()
                SessionControlActions.SKIP_BREAK -> coordinator.skipBreak()
            }
        }
    }
}

