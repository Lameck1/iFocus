package com.lameck.ifocus.session

import com.lameck.ifocus.ui.ActiveSession
import com.lameck.ifocus.ui.TimerMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionDomainTest {

    @Test
    fun `toTimerModeOrDefault falls back to focus for unknown values`() {
        assertEquals(TimerMode.FOCUS, "NOT_A_MODE".toTimerModeOrDefault())
    }

    @Test
    fun `running short break maps to running break status`() {
        val status = ActiveSession(
            taskId = "task-1",
            mode = TimerMode.SHORT_BREAK,
            startedAtMs = 1_000L,
            scheduledCompletionMs = 2_000L
        ).toSessionStatus()

        assertEquals(SessionStatus.RUNNING_BREAK, status)
    }

    @Test
    fun `paused focus maps to paused focus status`() {
        val status = ActiveSession(
            taskId = "task-1",
            mode = TimerMode.FOCUS,
            startedAtMs = 1_000L,
            scheduledCompletionMs = 2_000L,
            isPaused = true,
            pausedAtMs = 1_500L,
            pausedRemainingSecs = 600
        ).toSessionStatus()

        assertEquals(SessionStatus.PAUSED_FOCUS, status)
    }
}

