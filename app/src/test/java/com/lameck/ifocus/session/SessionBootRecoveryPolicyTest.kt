package com.lameck.ifocus.session

import android.content.Intent
import com.lameck.ifocus.data.FocusRepository
import com.lameck.ifocus.ui.ActiveSession
import com.lameck.ifocus.ui.FocusTask
import com.lameck.ifocus.ui.InterruptionReason
import com.lameck.ifocus.ui.SessionRecord
import com.lameck.ifocus.ui.TimerMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionBootRecoveryPolicyTest {

    @Test
    fun `boot completed clears active session and stops foreground operations`() = runTest {
        val repository = FakeRepository(
            activeSession = ActiveSession(
                taskId = "task-1",
                mode = TimerMode.FOCUS,
                startedAtMs = 1_000L,
                scheduledCompletionMs = 20_000L
            )
        )
        val scheduler = FakeAlarmScheduler()
        val foregroundController = FakeForegroundController()
        var reconcileCalls = 0

        val policy = SessionBootRecoveryPolicy(
            repository,
            scheduler,
            foregroundController,
            reconcileStartup = { reconcileCalls += 1 }
        )

        policy.handle(Intent.ACTION_BOOT_COMPLETED)

        assertNull(repository.activeSession)
        assertEquals(1, scheduler.cancelCount)
        assertEquals(1, foregroundController.stopCount)
        assertEquals(0, reconcileCalls)
    }

    @Test
    fun `package replaced triggers startup reconciliation only`() = runTest {
        val repository = FakeRepository(
            activeSession = ActiveSession(
                taskId = "task-1",
                mode = TimerMode.FOCUS,
                startedAtMs = 1_000L,
                scheduledCompletionMs = 20_000L
            )
        )
        val scheduler = FakeAlarmScheduler()
        val foregroundController = FakeForegroundController()
        var reconcileCalls = 0

        val policy = SessionBootRecoveryPolicy(
            repository,
            scheduler,
            foregroundController,
            reconcileStartup = { reconcileCalls += 1 }
        )

        policy.handle(Intent.ACTION_MY_PACKAGE_REPLACED)

        assertEquals(1, reconcileCalls)
        assertEquals(0, scheduler.cancelCount)
        assertEquals(0, foregroundController.stopCount)
    }

    private class FakeRepository(
        var activeSession: ActiveSession? = null
    ) : FocusRepository {
        override suspend fun loadTasks(): List<FocusTask> = emptyList()
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
        var cancelCount = 0

        override fun schedule(triggerAtElapsedRealtimeMs: Long, taskTitle: String?) = Unit

        override fun cancel() {
            cancelCount += 1
        }
    }

    private class FakeForegroundController : SessionForegroundController {
        var stopCount = 0

        override fun showRunning(taskTitle: String?, modeName: String, remainingSeconds: Int) = Unit
        override fun showPaused(taskTitle: String?, modeName: String, remainingSeconds: Int) = Unit

        override fun stop() {
            stopCount += 1
        }
    }
}


