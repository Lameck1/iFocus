package com.lameck.ifocus.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lameck.ifocus.data.FocusRepositoryProvider
import kotlinx.coroutines.runBlocking

class SessionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SessionAlarmContract.ACTION_SESSION_COMPLETED) return

        // Reconcile persisted active session to avoid stale running state after process death.
        val shouldNotify = runBlocking {
            val repository = FocusRepositoryProvider.get(context.applicationContext)
            val activeSession = repository.loadActiveSession() ?: return@runBlocking false
            if (activeSession.isPaused) return@runBlocking false
            repository.clearActiveSession()
            true
        }
        if (!shouldNotify) return

        context.stopService(Intent(context, FocusSessionForegroundService::class.java))

        val taskTitle = intent.getStringExtra(SessionAlarmContract.EXTRA_TASK_TITLE)
        SessionNotificationPublisher(context).showSessionCompleted(taskTitle)
    }
}

