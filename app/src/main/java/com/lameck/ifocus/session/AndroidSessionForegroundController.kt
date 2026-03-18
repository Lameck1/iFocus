package com.lameck.ifocus.session

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AndroidSessionForegroundController(
    private val context: Context
) : SessionForegroundController {

    override fun showRunning(taskTitle: String?, modeName: String, remainingSeconds: Int) {
        startService(FocusSessionForegroundService.ACTION_SHOW_RUNNING, taskTitle, modeName, remainingSeconds)
    }

    override fun showPaused(taskTitle: String?, modeName: String, remainingSeconds: Int) {
        startService(FocusSessionForegroundService.ACTION_SHOW_PAUSED, taskTitle, modeName, remainingSeconds)
    }

    override fun stop() {
        context.stopService(Intent(context, FocusSessionForegroundService::class.java))
    }

    private fun startService(action: String, taskTitle: String?, modeName: String, remainingSeconds: Int) {
        val intent = Intent(context, FocusSessionForegroundService::class.java)
            .setAction(action)
            .putExtra(FocusSessionForegroundService.EXTRA_TASK_TITLE, taskTitle)
            .putExtra(FocusSessionForegroundService.EXTRA_MODE_NAME, modeName)
            .putExtra(FocusSessionForegroundService.EXTRA_REMAINING_SECONDS, remainingSeconds)
        ContextCompat.startForegroundService(context, intent)
    }
}


