package com.lameck.ifocus.session

interface SessionAlarmScheduler {
    fun schedule(triggerAtElapsedRealtimeMs: Long, taskTitle: String?)
    fun cancel()

    object NoOp : SessionAlarmScheduler {
        override fun schedule(triggerAtElapsedRealtimeMs: Long, taskTitle: String?) = Unit
        override fun cancel() = Unit
    }
}

object SessionAlarmContract {
    const val ACTION_SESSION_COMPLETED = "com.lameck.ifocus.action.SESSION_COMPLETED"
    const val EXTRA_TASK_TITLE = "extra_task_title"
    const val REQUEST_CODE = 4101
}

