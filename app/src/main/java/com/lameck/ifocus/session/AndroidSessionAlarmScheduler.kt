package com.lameck.ifocus.session

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class AndroidSessionAlarmScheduler(
    private val context: Context
) : SessionAlarmScheduler {

    override fun schedule(triggerAtElapsedRealtimeMs: Long, taskTitle: String?) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return
        }

        val pendingIntent = buildPendingIntent(taskTitle)

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtElapsedRealtimeMs,
                pendingIntent
            )
        } catch (_: SecurityException) {
            // Gracefully skip scheduling when exact alarms are not allowed.
        }
    }

    override fun cancel() {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = buildPendingIntent(taskTitle = null)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun buildPendingIntent(taskTitle: String?): PendingIntent {
        val intent = Intent(context, SessionAlarmReceiver::class.java)
            .setAction(SessionAlarmContract.ACTION_SESSION_COMPLETED)
            .putExtra(SessionAlarmContract.EXTRA_TASK_TITLE, taskTitle)

        return PendingIntent.getBroadcast(
            context,
            SessionAlarmContract.REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}


