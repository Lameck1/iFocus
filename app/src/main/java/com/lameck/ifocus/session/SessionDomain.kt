package com.lameck.ifocus.session

import com.lameck.ifocus.ui.ActiveSession
import com.lameck.ifocus.ui.TimerMode

enum class SessionType {
    FOCUS,
    BREAK
}

enum class SessionStatus {
    RUNNING_FOCUS,
    PAUSED_FOCUS,
    RUNNING_BREAK,
    PAUSED_BREAK
}

fun TimerMode.toSessionType(): SessionType = when (this) {
    TimerMode.FOCUS -> SessionType.FOCUS
    TimerMode.SHORT_BREAK,
    TimerMode.LONG_BREAK -> SessionType.BREAK
}

fun ActiveSession.toSessionStatus(): SessionStatus {
    val type = mode.toSessionType()
    return when {
        isPaused && type == SessionType.FOCUS -> SessionStatus.PAUSED_FOCUS
        isPaused && type == SessionType.BREAK -> SessionStatus.PAUSED_BREAK
        !isPaused && type == SessionType.FOCUS -> SessionStatus.RUNNING_FOCUS
        else -> SessionStatus.RUNNING_BREAK
    }
}

fun String.toTimerModeOrDefault(): TimerMode {
    return TimerMode.entries.firstOrNull { it.name == this } ?: TimerMode.FOCUS
}

