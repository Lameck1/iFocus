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
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionControlCoordinatorTest {

    @Test
    fun `pause converts running session into paused and cancels alarm`() = runTest {
        val repository = FakeRepository(
            tasks = mutableListOf(sampleTask()),
            activeSession = ActiveSession(
                taskId = TASK_ID,
                mode = TimerMode.FOCUS,
                startedAtMs = 1_000L,
                scheduledCompletionMs = 25_000L,
                isPaused = false
            )
        )
        val scheduler = FakeAlarmScheduler()
        val foreground = FakeForegroundController()
        val coordinator = SessionControlCoordinator(
            repository,
            scheduler,
            foreground,
            elapsedRealtimeProvider = { 5_000L }
        )

        coordinator.pauseActiveSession()

        val paused = repository.activeSession
        assertTrue(paused?.isPaused == true)
        assertEquals(20, paused?.pausedRemainingSecs)
        assertEquals(1, scheduler.cancelCount)
        assertEquals(1, foreground.pausedCount)
        assertEquals("Draft proposal", foreground.lastTaskTitle)
    }

    @Test
    fun `resume schedules alarm and marks session running`() = runTest {
        val repository = FakeRepository(
            tasks = mutableListOf(sampleTask()),
            activeSession = ActiveSession(
                taskId = TASK_ID,
                mode = TimerMode.FOCUS,
                startedAtMs = 1_000L,
                scheduledCompletionMs = 25_000L,
                isPaused = true,
                pausedAtMs = 10_000L,
                pausedRemainingSecs = 30
            )
        )
        val scheduler = FakeAlarmScheduler()
        val foreground = FakeForegroundController()
        val coordinator = SessionControlCoordinator(
            repository,
            scheduler,
            foreground,
            elapsedRealtimeProvider = { 20_000L }
        )

        coordinator.resumePausedSession()

        val resumed = repository.activeSession
        assertTrue(resumed?.isPaused == false)
        assertNull(resumed?.pausedAtMs)
        assertNull(resumed?.pausedRemainingSecs)
        assertEquals(50_000L, resumed?.scheduledCompletionMs)
        assertEquals(1, scheduler.scheduleCount)
        assertEquals(50_000L, scheduler.lastScheduledAtMs)
        assertEquals(1, foreground.runningCount)
        assertEquals("Draft proposal", foreground.lastTaskTitle)
    }

    @Test
    fun `stop clears session and stops foreground`() = runTest {
        val repository = FakeRepository(
            activeSession = ActiveSession(
                taskId = TASK_ID,
                mode = TimerMode.FOCUS,
                startedAtMs = 1_000L,
                scheduledCompletionMs = 25_000L
            )
        )
        val scheduler = FakeAlarmScheduler()
        val foreground = FakeForegroundController()
        val coordinator = SessionControlCoordinator(repository, scheduler, foreground)

        coordinator.stopSession()

        assertNull(repository.activeSession)
        assertEquals(1, scheduler.cancelCount)
        assertEquals(1, foreground.stopCount)
    }

    @Test
    fun `pause clears expired running session`() = runTest {
        val repository = FakeRepository(
            activeSession = ActiveSession(
                taskId = TASK_ID,
                mode = TimerMode.FOCUS,
                startedAtMs = 1_000L,
                scheduledCompletionMs = 1_500L
            )
        )
        val scheduler = FakeAlarmScheduler()
        val foreground = FakeForegroundController()
        val coordinator = SessionControlCoordinator(
            repository,
            scheduler,
            foreground,
            elapsedRealtimeProvider = { 2_000L }
        )

        coordinator.pauseActiveSession()

        assertNull(repository.activeSession)
        assertEquals(1, scheduler.cancelCount)
        assertEquals(1, foreground.stopCount)
        assertEquals(0, foreground.pausedCount)
    }

    @Test
    fun `start focus creates session and schedules alarm when idle`() = runTest {
        val repository = FakeRepository(tasks = mutableListOf(sampleTask()))
        val scheduler = FakeAlarmScheduler()
        val foreground = FakeForegroundController()
        val coordinator = SessionControlCoordinator(
            repository,
            scheduler,
            foreground,
            elapsedRealtimeProvider = { 10_000L }
        )

        coordinator.startFocusSession()

        val started = repository.activeSession
        assertEquals(TASK_ID, started?.taskId)
        assertEquals(TimerMode.FOCUS, started?.mode)
        assertTrue(started?.isPaused == false)
        assertEquals(1_510_000L, started?.scheduledCompletionMs)
        assertEquals(1, scheduler.scheduleCount)
        assertEquals(1_510_000L, scheduler.lastScheduledAtMs)
        assertEquals(1, foreground.runningCount)
        assertEquals("Draft proposal", foreground.lastTaskTitle)
    }

    @Test
    fun `start focus resumes existing paused session`() = runTest {
        val repository = FakeRepository(
            tasks = mutableListOf(sampleTask()),
            activeSession = ActiveSession(
                taskId = TASK_ID,
                mode = TimerMode.FOCUS,
                startedAtMs = 1_000L,
                scheduledCompletionMs = 30_000L,
                isPaused = true,
                pausedAtMs = 5_000L,
                pausedRemainingSecs = 20
            )
        )
        val scheduler = FakeAlarmScheduler()
        val foreground = FakeForegroundController()
        val coordinator = SessionControlCoordinator(
            repository,
            scheduler,
            foreground,
            elapsedRealtimeProvider = { 40_000L }
        )

        coordinator.startFocusSession()

        val resumed = repository.activeSession
        assertTrue(resumed?.isPaused == false)
        assertEquals(60_000L, resumed?.scheduledCompletionMs)
        assertEquals(1, scheduler.scheduleCount)
        assertEquals(1, foreground.runningCount)
    }

    @Test
    fun `start deep work creates 50 minute session`() = runTest {
        val repository = FakeRepository(tasks = mutableListOf(sampleTask()))
        val scheduler = FakeAlarmScheduler()
        val foreground = FakeForegroundController()
        val coordinator = SessionControlCoordinator(
            repository,
            scheduler,
            foreground,
            elapsedRealtimeProvider = { 100_000L }
        )

        coordinator.startDeepWorkSession()

        assertEquals(3_100_000L, repository.activeSession?.scheduledCompletionMs)
        assertEquals(3_100_000L, scheduler.lastScheduledAtMs)
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
        var lastTaskTitle: String? = null

        override fun showRunning(taskTitle: String?, modeName: String, remainingSeconds: Int) {
            runningCount += 1
            lastTaskTitle = taskTitle
        }

        override fun showPaused(taskTitle: String?, modeName: String, remainingSeconds: Int) {
            pausedCount += 1
            lastTaskTitle = taskTitle
        }

        override fun stop() {
            stopCount += 1
        }
    }

    companion object {
        private const val TASK_ID = "task-1"
    }
}


