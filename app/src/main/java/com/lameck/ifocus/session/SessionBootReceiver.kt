package com.lameck.ifocus.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lameck.ifocus.data.FocusRepositoryProvider
import kotlinx.coroutines.runBlocking

class SessionBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        runBlocking {
            val appContext = context.applicationContext
            val repository = FocusRepositoryProvider.get(appContext)
            val scheduler = AndroidSessionAlarmScheduler(appContext)
            val foregroundController = AndroidSessionForegroundController(appContext)
            val startupReconciler = SessionStartupReconciler(repository, scheduler)
            SessionBootRecoveryPolicy(
                repository = repository,
                sessionAlarmScheduler = scheduler,
                sessionForegroundController = foregroundController,
                reconcileStartup = { startupReconciler.reconcileAndReschedule() }
            ).handle(action)
        }
    }
}

