package com.lameck.ifocus.session.usecase

import com.lameck.ifocus.data.FocusRepository
import com.lameck.ifocus.session.SessionAlarmScheduler
import com.lameck.ifocus.session.SessionForegroundController
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
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionUseCasesTest {

    @Test
    fun `pause use case pauses active running session`() = runTest {
        val repository = FakeRepository(
            tasks = mutableListOf(sampleTask()),
            activeSession = ActiveSession(
                taskId = TASK_ID,
                mode = TimerMode.FOCUS,
                startedAtMs = 1_000L,
                scheduledCompletionMs = 21_000L,
                isPaused = false
            )
        )
        val scheduler = FakeAlarmScheduler()
        val foreground = FakeForegroundController()
        val useCase = PauseSessionUseCase(
            repository = repository,
            alarmScheduler = scheduler,
            foregroundController = foreground,
            elapsedRealtimeProvider = { 6_000L }
        )

        val result = useCase()

        assertTrue(result is SessionActionResult.Updated)
        assertTrue(repository.activeSession?.isPaused == true)
        assertEquals(15, repository.activeSession?.pausedRemainingSecs)
        assertEquals(1, scheduler.cancelCount)
        assertEquals(1, foreground.pausedCount)
    }

    @Test
    fun `resume use case resumes paused session and schedules alarm`() = runTest {
        val repository = FakeRepository(
            tasks = mutableListOf(sampleTask()),
            activeSession = ActiveSession(
                taskId = TASK_ID,
                mode = TimerMode.FOCUS,
                startedAtMs = 1_000L,
                scheduledCompletionMs = 21_000L,
                isPaused = true,
                pausedAtMs = 7_000L,
                pausedRemainingSecs = 42
            )
        )
        val scheduler = FakeAlarmScheduler()
        val foreground = FakeForegroundController()
        val useCase = ResumeSessionUseCase(
            repository = repository,
            alarmScheduler = scheduler,
            foregroundController = foreground,
            elapsedRealtimeProvider = { 10_000L }
        )

        val result = useCase()

        assertTrue(result is SessionActionResult.Updated)
        assertTrue(repository.activeSession?.isPaused == false)
        assertNull(repository.activeSession?.pausedAtMs)
        assertNull(repository.activeSession?.pausedRemainingSecs)
        assertEquals(52_000L, repository.activeSession?.scheduledCompletionMs)
        assertEquals(1, scheduler.scheduleCount)
        assertEquals(52_000L, scheduler.lastScheduledAtMs)
        assertEquals(1, foreground.runningCount)
    }

    @Test
    fun `stop use case clears active session and stops foreground state`() = runTest {
        val repository = FakeRepository(
            activeSession = ActiveSession(
                taskId = TASK_ID,
                mode = TimerMode.FOCUS,
                startedAtMs = 1_000L,
                scheduledCompletionMs = 21_000L
            )
        )
        val scheduler = FakeAlarmScheduler()
        val foreground = FakeForegroundController()
        val useCase = StopSessionUseCase(
            repository = repository,
            alarmScheduler = scheduler,
            foregroundController = foreground
        )

        val result = useCase()

        assertTrue(result is SessionActionResult.Updated)
        assertNull(repository.activeSession)
        assertEquals(1, scheduler.cancelCount)
        assertEquals(1, foreground.stopCount)
    }

    private fun sampleTask() = FocusTask(
        id = TASK_ID,
        title = "Draft proposal",
        estimateMinutes = 50,
        priority = TaskPriority.P1,
        status = TaskStatus.IN_PROGRESS
    )

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

    private class FakeAlarmScheduler : SessionAlarmScheduler {
        var scheduleCount: Int = 0
        var cancelCount: Int = 0
        var lastScheduledAtMs: Long? = null

        override fun schedule(triggerAtElapsedRealtimeMs: Long, taskTitle: String?) {
            scheduleCount += 1
            lastScheduledAtMs = triggerAtElapsedRealtimeMs
        }

        override fun cancel() {
            cancelCount += 1
        }
    }

    private class FakeForegroundController : SessionForegroundController {
        var runningCount: Int = 0
        var pausedCount: Int = 0
        var stopCount: Int = 0

        override fun showRunning(taskTitle: String?, modeName: String, remainingSeconds: Int) {
            runningCount += 1
        }

        override fun showPaused(taskTitle: String?, modeName: String, remainingSeconds: Int) {
            pausedCount += 1
        }

        override fun stop() {
            stopCount += 1
        }
    }

    companion object {
        private const val TASK_ID = "task-1"
    }
}

