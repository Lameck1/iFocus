package com.lameck.ifocus.session

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lameck.ifocus.MainActivity
import com.lameck.ifocus.R
import java.util.Locale

class FocusSessionForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_RUNNING,
            ACTION_SHOW_PAUSED -> {
                val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE)
                val modeName = intent.getStringExtra(EXTRA_MODE_NAME) ?: "FOCUS"
                val remainingSeconds = intent.getIntExtra(EXTRA_REMAINING_SECONDS, 0).coerceAtLeast(0)
                val paused = intent.action == ACTION_SHOW_PAUSED
                val notification = buildNotification(taskTitle, modeName, remainingSeconds, paused)
                startForeground(NOTIFICATION_ID, notification)
            }

            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(
        taskTitle: String?,
        modeName: String,
        remainingSeconds: Int,
        paused: Boolean
    ): android.app.Notification {
        val isBreakMode = modeName == "SHORT_BREAK" || modeName == "LONG_BREAK"
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(
            if (paused) {
                getString(
                    R.string.session_service_paused,
                    modeName.lowercase(Locale.getDefault()),
                    formatDuration(remainingSeconds)
                )
            } else {
                getString(
                    R.string.session_service_running,
                    modeName.lowercase(Locale.getDefault()),
                    formatDuration(remainingSeconds)
                )
            }
        )
        .setStyle(
            NotificationCompat.BigTextStyle().bigText(
                if (taskTitle.isNullOrBlank()) {
                    getString(R.string.current_focus_block)
                } else {
                    taskTitle
                }
            )
        )
        .addAction(
            0,
            if (paused) getString(R.string.session_action_resume) else getString(R.string.session_action_pause),
            controlPendingIntent(
                if (paused) SessionControlActions.RESUME else SessionControlActions.PAUSE,
                REQUEST_CODE_CONTROL
            )
        )
        .addAction(
            0,
            getString(R.string.session_action_stop),
            controlPendingIntent(SessionControlActions.STOP, REQUEST_CODE_STOP)
        )
        .setContentIntent(mainActivityPendingIntent())
        .setOngoing(!paused)
        .setOnlyAlertOnce(true)
        if (isBreakMode) {
            builder.addAction(
                0,
                getString(R.string.session_action_skip_break),
                controlPendingIntent(SessionControlActions.SKIP_BREAK, REQUEST_CODE_SKIP_BREAK)
            )
        }
        return builder.build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.session_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.session_service_channel_description)
            }
        )
    }

    private fun mainActivityPendingIntent(): PendingIntent {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN_APP,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun controlPendingIntent(action: String, requestCode: Int): PendingIntent {
        val controlIntent = Intent(this, SessionControlReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            this,
            requestCode,
            controlIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun formatDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }

    companion object {
        const val ACTION_SHOW_RUNNING = "com.lameck.ifocus.action.SERVICE_SHOW_RUNNING"
        const val ACTION_SHOW_PAUSED = "com.lameck.ifocus.action.SERVICE_SHOW_PAUSED"
        const val ACTION_STOP = "com.lameck.ifocus.action.SERVICE_STOP"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_MODE_NAME = "extra_mode_name"
        const val EXTRA_REMAINING_SECONDS = "extra_remaining_seconds"

        const val CHANNEL_ID = "ifocus_session_foreground"
        const val NOTIFICATION_ID = 4201
        private const val REQUEST_CODE_OPEN_APP = 4202
        private const val REQUEST_CODE_STOP = 4203
        private const val REQUEST_CODE_CONTROL = 4204
        private const val REQUEST_CODE_SKIP_BREAK = 4205
    }
}



