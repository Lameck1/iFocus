package com.lameck.ifocus.session

import com.lameck.ifocus.data.FocusRepository
import com.lameck.ifocus.ui.ActiveSession
import com.lameck.ifocus.ui.FocusTask
import com.lameck.ifocus.ui.InterruptionReason
import com.lameck.ifocus.ui.SessionRecord
import com.lameck.ifocus.ui.TaskPriority
import com.lameck.ifocus.ui.TaskStatus
import com.lameck.ifocus.ui.TimerMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionStartupReconcilerTest {

    @Test
    fun `running non-expired session is rescheduled`() = runTest {
        val scheduler = FakeSessionAlarmScheduler()
        val repository = FakeRepository(
            tasks = mutableListOf(
                FocusTask(
                    id = "task-1",
                    title = "Draft proposal",
                    estimateMinutes = 25,
                    priority = TaskPriority.P1,
                    status = TaskStatus.IN_PROGRESS
                )
            ),
            activeSession = ActiveSession(
                taskId = "task-1",
                mode = TimerMode.FOCUS,
                startedAtMs = 1_000L,
                scheduledCompletionMs = 15_000L,
                isPaused = false
            )
        )

        val reconciler = SessionStartupReconciler(
            repository = repository,
            sessionAlarmScheduler = scheduler,
            elapsedRealtimeProvider = { 5_000L }
        )

        reconciler.reconcileAndReschedule()

        assertEquals(1, scheduler.scheduleCount)
        assertEquals(15_000L, scheduler.lastTriggerAtMs)
        assertEquals("Draft proposal", scheduler.lastTaskTitle)
        assertEquals("task-1", repository.activeSession?.taskId)
    }

    @Test
    fun `paused session cancels alarms and keeps persisted state`() = runTest {
        val scheduler = FakeSessionAlarmScheduler()
        val paused = ActiveSession(
            taskId = "task-1",
            mode = TimerMode.FOCUS,
            startedAtMs = 1_000L,
            scheduledCompletionMs = 10_000L,
            isPaused = true,
            pausedAtMs = 2_000L,
            pausedRemainingSecs = 300
        )
        val repository = FakeRepository(activeSession = paused)

        val reconciler = SessionStartupReconciler(
            repository = repository,
            sessionAlarmScheduler = scheduler,
            elapsedRealtimeProvider = { 5_000L }
        )

        reconciler.reconcileAndReschedule()

        assertEquals(0, scheduler.scheduleCount)
        assertEquals(1, scheduler.cancelCount)
        assertEquals(paused, repository.activeSession)
    }

    @Test
    fun `expired running session is cleared and alarm canceled`() = runTest {
        val scheduler = FakeSessionAlarmScheduler()
        val repository = FakeRepository(
            activeSession = ActiveSession(
                taskId = "task-1",
                mode = TimerMode.FOCUS,
                startedAtMs = 1_000L,
                scheduledCompletionMs = 2_000L,
                isPaused = false
            )
        )

        val reconciler = SessionStartupReconciler(
            repository = repository,
            sessionAlarmScheduler = scheduler,
            elapsedRealtimeProvider = { 5_000L }
        )

        reconciler.reconcileAndReschedule()

        assertEquals(0, scheduler.scheduleCount)
        assertEquals(1, scheduler.cancelCount)
        assertNull(repository.activeSession)
    }

    private class FakeSessionAlarmScheduler : SessionAlarmScheduler {
        var scheduleCount = 0
        var cancelCount = 0
        var lastTriggerAtMs: Long? = null
        var lastTaskTitle: String? = null

        override fun schedule(triggerAtElapsedRealtimeMs: Long, taskTitle: String?) {
            scheduleCount += 1
            lastTriggerAtMs = triggerAtElapsedRealtimeMs
            lastTaskTitle = taskTitle
        }

        override fun cancel() {
            cancelCount += 1
        }
    }

    private class FakeRepository(
        private val tasks: MutableList<FocusTask> = mutableListOf(),
        var activeSession: ActiveSession? = null
    ) : FocusRepository {
        override suspend fun loadTasks(): List<FocusTask> = tasks.toList()

        override suspend fun loadSessionHistory(limit: Int): List<SessionRecord> = emptyList()

        override suspend fun loadInterruptionCounts(): Map<InterruptionReason, Int> = emptyMap()

        override suspend fun upsertTask(task: FocusTask) = Unit

        override suspend fun upsertTasks(tasks: List<FocusTask>) = Unit

        override suspend fun insertSession(record: SessionRecord) = Unit

        override suspend fun upsertInterruptionCounts(counts: Map<InterruptionReason, Int>) = Unit

        override suspend fun loadActiveSession(): ActiveSession? = activeSession

        override suspend fun upsertActiveSession(session: ActiveSession) {
            activeSession = session
        }

        override suspend fun clearActiveSession() {
            activeSession = null
        }
    }
}



