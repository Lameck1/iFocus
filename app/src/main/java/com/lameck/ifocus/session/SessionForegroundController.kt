package com.lameck.ifocus.session

interface SessionForegroundController {
    fun showRunning(taskTitle: String?, modeName: String, remainingSeconds: Int)
    fun showPaused(taskTitle: String?, modeName: String, remainingSeconds: Int)
    fun stop()

    object NoOp : SessionForegroundController {
        override fun showRunning(taskTitle: String?, modeName: String, remainingSeconds: Int) = Unit
        override fun showPaused(taskTitle: String?, modeName: String, remainingSeconds: Int) = Unit
        override fun stop() = Unit
    }
}

