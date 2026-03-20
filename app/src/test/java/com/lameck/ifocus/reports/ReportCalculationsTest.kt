package com.lameck.ifocus.reports

import com.lameck.ifocus.ui.SessionOutcome
import com.lameck.ifocus.ui.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportCalculationsTest {

    @Test
    fun `computeSummary counts totals and interruptions`() {
        val summary = computeSummary(
            listOf(
                record("1", "Task A", 25, SessionOutcome.DONE, now = 1_000L),
                record("2", "Task A", 15, SessionOutcome.PARTIAL, now = 2_000L)
            )
        )

        assertEquals(40, summary.totalMinutes)
        assertEquals(1, summary.completedSessions)
        assertEquals(1, summary.interruptedSessions)
        assertEquals(25, summary.averageUninterruptedFocusMinutes)
    }

    @Test
    fun `recordsForToday keeps only sessions after day start`() {
        val now = 1_000_000_000L
        val dayStart = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val result = recordsForToday(
            listOf(
                record("old", "Task", 10, SessionOutcome.DONE, dayStart - 1),
                record("new", "Task", 20, SessionOutcome.DONE, dayStart + 1)
            ),
            nowEpochMs = now
        )

        assertEquals(1, result.size)
        assertEquals("new", result.first().id)
    }

    @Test
    fun `breakdownByTask aggregates and sorts descending`() {
        val breakdown = breakdownByTask(
            listOf(
                record("1", "Task B", 10, SessionOutcome.DONE, 1_000L),
                record("2", "Task A", 30, SessionOutcome.DONE, 1_100L),
                record("3", "Task B", 15, SessionOutcome.DONE, 1_200L)
            )
        )

        assertEquals(2, breakdown.size)
        assertEquals("Task A", breakdown[0].first)
        assertEquals(30, breakdown[0].second)
        assertTrue(breakdown[0].second >= breakdown[1].second)
    }

    @Test
    fun `breakdownByProject aggregates project totals`() {
        val breakdown = breakdownByProject(
            listOf(
                record("1", "Task A", 20, SessionOutcome.DONE, 1_000L, projectName = "Client A"),
                record("2", "Task B", 15, SessionOutcome.DONE, 1_100L, projectName = "Client A"),
                record("3", "Task C", 25, SessionOutcome.DONE, 1_200L, projectName = "Internal")
            )
        )

        assertEquals("Client A", breakdown.first().first)
        assertEquals(35, breakdown.first().second)
    }

    @Test
    fun `streak calculations respect daily goal`() {
        val day = 24L * 60L * 60L * 1000L
        val now = 10 * day
        val records = listOf(
            record("1", "Task", 60, SessionOutcome.DONE, now - 2 * day),
            record("2", "Task", 65, SessionOutcome.DONE, now - day),
            record("3", "Task", 70, SessionOutcome.DONE, now),
            record("4", "Task", 20, SessionOutcome.PARTIAL, now)
        )

        assertEquals(3, currentStreakDays(records, dailyGoalMinutes = 50, nowEpochMs = now))
        assertEquals(3, bestStreakDays(records, dailyGoalMinutes = 50))
    }

    private fun record(
        id: String,
        title: String,
        minutes: Int,
        outcome: SessionOutcome,
        now: Long,
        projectName: String? = null
    ): SessionRecord {
        return SessionRecord(
            id = id,
            taskTitle = title,
            projectName = projectName,
            focusedMinutes = minutes,
            outcome = outcome,
            createdAtEpochMs = now
        )
    }
}


