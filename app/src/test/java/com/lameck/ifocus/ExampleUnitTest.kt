package com.lameck.ifocus

import com.lameck.ifocus.ui.TimerMode
import com.lameck.ifocus.ui.TimerUiState
import org.junit.Test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

class TimerModelUnitTest {
    @Test
    fun `timer modes expose expected durations in minutes`() {
        assertEquals(25, TimerMode.FOCUS.durationMinutes)
        assertEquals(5, TimerMode.SHORT_BREAK.durationMinutes)
        assertEquals(15, TimerMode.LONG_BREAK.durationMinutes)
    }

    @Test
    fun `default ui state is consistent`() {
        val state = TimerUiState()

        assertEquals(TimerMode.FOCUS, state.currentMode)
        assertEquals(state.totalSeconds, state.remainingSeconds)
        assertFalse(state.isRunning)
    }
}