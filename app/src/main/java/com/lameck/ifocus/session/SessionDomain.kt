package com.lameck.ifocus.session

import com.lameck.ifocus.ui.ActiveSession
import com.lameck.ifocus.ui.TimerMode

enum class SessionType {
    FOCUS,
    BREAK
}

enum class SessionStatus {
    IDLE,
    RUNNING_FOCUS,
    PAUSED_FOCUS,
    RUNNING_BREAK,
    PAUSED_BREAK,
    COMPLETED,
    CANCELLED
}

fun SessionStatus.isRunning(): Boolean {
    return this == SessionStatus.RUNNING_FOCUS || this == SessionStatus.RUNNING_BREAK
}

fun SessionStatus.isPaused(): Boolean {
    return this == SessionStatus.PAUSED_FOCUS || this == SessionStatus.PAUSED_BREAK
}

fun SessionStatus.isTerminal(): Boolean {
    return this == SessionStatus.COMPLETED || this == SessionStatus.CANCELLED
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

