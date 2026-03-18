package com.lameck.ifocus.ui

import com.lameck.ifocus.data.InMemoryFocusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FocusViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeClock = FakeElapsedRealtimeProvider()
    private val fakeAlarmScheduler = FakeSessionAlarmScheduler()
    private val fakeForegroundController = FakeSessionForegroundController()
    private lateinit var repository: InMemoryFocusRepository
    private lateinit var viewModel: FocusViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = InMemoryFocusRepository()
        viewModel = FocusViewModel(
            elapsedRealtimeProvider = fakeClock,
            repository = repository,
            sessionAlarmScheduler = fakeAlarmScheduler,
            sessionForegroundController = fakeForegroundController
        )
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) {
            viewModel.resetTimer()
        }
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is FOCUS mode with 25 minutes`() {
        val uiState = viewModel.uiState.value
        assertEquals(TimerMode.FOCUS, uiState.currentMode)
        assertEquals(25 * 60, uiState.remainingSeconds)
        assertFalse(uiState.isRunning)
    }

    @Test
    fun `toggleTimer starts and pauses the timer`() = runTest(testDispatcher) {
        viewModel.toggleTimer()
        assertTrue(viewModel.uiState.value.isRunning)

        advanceBy(1300)
        viewModel.toggleTimer()
        assertFalse(viewModel.uiState.value.isRunning)
        assertEquals(25 * 60 - 1, viewModel.uiState.value.remainingSeconds)
    }

    @Test
    fun `timer counts down correctly`() = runTest(testDispatcher) {
        viewModel.toggleTimer()

        advanceBy(1001)
        assertEquals(25 * 60 - 1, viewModel.uiState.value.remainingSeconds)

        advanceBy(5000)
        assertEquals(25 * 60 - 6, viewModel.uiState.value.remainingSeconds)

        viewModel.toggleTimer()
    }

    @Test
    fun `setMode updates the timer duration`() {
        viewModel.setMode(TimerMode.SHORT_BREAK)
        assertEquals(TimerMode.SHORT_BREAK, viewModel.uiState.value.currentMode)
        assertEquals(5 * 60, viewModel.uiState.value.remainingSeconds)

        viewModel.setMode(TimerMode.LONG_BREAK)
        assertEquals(TimerMode.LONG_BREAK, viewModel.uiState.value.currentMode)
        assertEquals(15 * 60, viewModel.uiState.value.remainingSeconds)
    }

    @Test
    fun `resetTimer resets the remaining seconds`() = runTest(testDispatcher) {
        viewModel.toggleTimer()
        advanceBy(10000)
        
        viewModel.resetTimer()
        assertEquals(25 * 60, viewModel.uiState.value.remainingSeconds)
        assertFalse(viewModel.uiState.value.isRunning)
    }

    @Test
    fun `timer completion emits session completed event`() = runTest(testDispatcher) {
        viewModel.setMode(TimerMode.SHORT_BREAK) // 5 mins = 300 seconds

        var emittedEvent: TimerEvent? = null
        val collectorJob = launch {
            viewModel.events.collect {
                emittedEvent = it
            }
        }

        viewModel.toggleTimer()

        advanceBy(300 * 1000L + 100)

        assertTrue(emittedEvent is TimerEvent.SessionCompleted)
        assertFalse(viewModel.uiState.value.isRunning)
        assertEquals(0, viewModel.uiState.value.remainingSeconds)

        collectorJob.cancelAndJoin()
    }

    @Test
    fun `focus completion switches to short break and stays paused when auto start break disabled`() = runTest(testDispatcher) {
        viewModel.setAutoStartBreak(false)
        viewModel.setMode(TimerMode.FOCUS)

        viewModel.toggleTimer()
        advanceBy(25 * 60 * 1_000L + 100)

        val ui = viewModel.uiState.value
        assertEquals(TimerMode.SHORT_BREAK, ui.currentMode)
        assertFalse(ui.isRunning)
        assertEquals(5 * 60, ui.remainingSeconds)
    }

    @Test
    fun `focus completion auto starts short break when enabled`() = runTest(testDispatcher) {
        viewModel.setAutoStartBreak(true)
        viewModel.setMode(TimerMode.FOCUS)

        viewModel.toggleTimer()
        advanceBy(25 * 60 * 1_000L + 100)

        val ui = viewModel.uiState.value
        assertEquals(TimerMode.SHORT_BREAK, ui.currentMode)
        assertTrue(ui.isRunning)
        assertTrue(ui.remainingSeconds in 1..(5 * 60))
    }

    @Test
    fun `short break completion auto starts focus when enabled`() = runTest(testDispatcher) {
        viewModel.setAutoStartFocus(true)
        viewModel.setMode(TimerMode.SHORT_BREAK)

        viewModel.toggleTimer()
        advanceBy(5 * 60 * 1_000L + 100)

        val ui = viewModel.uiState.value
        assertEquals(TimerMode.FOCUS, ui.currentMode)
        assertTrue(ui.isRunning)
        assertTrue(ui.remainingSeconds in 1..(25 * 60))
    }

    @Test
    fun `pause and resume continue from current remaining time`() = runTest(testDispatcher) {
        viewModel.toggleTimer()
        advanceBy(3100)
        viewModel.toggleTimer()

        val pausedRemaining = viewModel.uiState.value.remainingSeconds
        assertFalse(viewModel.uiState.value.isRunning)

        advanceBy(5000)
        assertEquals(pausedRemaining, viewModel.uiState.value.remainingSeconds)

        viewModel.toggleTimer()
        advanceBy(1000)
        assertEquals(pausedRemaining - 1, viewModel.uiState.value.remainingSeconds)

        viewModel.toggleTimer()
    }

    @Test
    fun `adding a task selects it when no task is selected`() {
        val initialCount = viewModel.professionalState.value.tasks.size
        viewModel.addTask("Prepare architecture review", estimateMinutes = 60, priority = TaskPriority.P1)

        val state = viewModel.professionalState.value
        assertEquals(initialCount + 1, state.tasks.size)
        assertNotNull(state.tasks.firstOrNull { it.title == "Prepare architecture review" })
        assertNotNull(state.selectedTaskId)
    }

    @Test
    fun `focus completion creates pending outcome for selected task`() = runTest(testDispatcher) {
        val selectedTaskId = viewModel.professionalState.value.selectedTaskId
        assertNotNull(selectedTaskId)

        viewModel.setMode(TimerMode.FOCUS)
        viewModel.toggleTimer()
        advanceBy(25 * 60 * 1000L + 100)

        val pending = viewModel.professionalState.value.pendingSessionOutcome
        assertNotNull(pending)
        assertEquals(selectedTaskId, pending?.taskId)
        assertEquals(25, pending?.focusedMinutes)
    }

    @Test
    fun `completing pending session updates task and history`() {
        val selectedTask = viewModel.professionalState.value.tasks.first()
        viewModel.selectTask(selectedTask.id)

        viewModel.addTask("Temporary")
        viewModel.dismissPendingSessionOutcome()

        viewModel.completePendingSession(SessionOutcome.DONE)
        assertTrue(viewModel.professionalState.value.sessionHistory.isEmpty())

        viewModel.professionalState.value.let { state ->
            val task = state.tasks.first { it.id == selectedTask.id }
            assertEquals(TaskStatus.PLANNED, task.status)
        }

        // Simulate a completed focus session by running a full cycle.
        viewModel.setMode(TimerMode.FOCUS)
        viewModel.selectTask(selectedTask.id)
        viewModel.toggleTimer()
        fakeClock.advanceBy(25 * 60 * 1000L + 100)
        testDispatcher.scheduler.advanceTimeBy(25 * 60 * 1000L + 100)
        testDispatcher.scheduler.runCurrent()

        assertNotNull(viewModel.professionalState.value.pendingSessionOutcome)
        viewModel.completePendingSession(SessionOutcome.DONE)

        val updatedState = viewModel.professionalState.value
        val updatedTask = updatedState.tasks.first { it.id == selectedTask.id }
        assertEquals(TaskStatus.COMPLETED, updatedTask.status)
        assertTrue(updatedTask.todayMinutesFocused >= 25)
        assertEquals(1, updatedState.sessionHistory.size)
        assertNull(updatedState.pendingSessionOutcome)
    }

    @Test
    fun `deleteTask removes task and updates selected task`() {
        val firstTaskId = viewModel.professionalState.value.tasks.first().id
        val secondTaskId = viewModel.professionalState.value.tasks[1].id

        viewModel.selectTask(firstTaskId)
        viewModel.deleteTask(firstTaskId)

        val state = viewModel.professionalState.value
        assertFalse(state.tasks.any { it.id == firstTaskId })
        assertEquals(secondTaskId, state.selectedTaskId)
    }

    @Test
    fun `pausing active focus session captures and records interruption reason`() = runTest(testDispatcher) {
        viewModel.setMode(TimerMode.FOCUS)
        viewModel.toggleTimer()
        advanceBy(2000)

        viewModel.toggleTimer()
        assertNotNull(viewModel.professionalState.value.pendingInterruption)

        viewModel.reportInterruption(InterruptionReason.MEETING)

        val state = viewModel.professionalState.value
        assertNull(state.pendingInterruption)
        assertEquals(1, state.interruptionCounts[InterruptionReason.MEETING])
    }

    @Test
    fun `switching tasks after pause resets focus timer to full duration`() = runTest(testDispatcher) {
        val firstTaskId = viewModel.professionalState.value.tasks.first().id
        val secondTaskId = viewModel.professionalState.value.tasks[1].id

        viewModel.selectTask(firstTaskId)
        viewModel.setMode(TimerMode.FOCUS)
        viewModel.toggleTimer()
        advanceBy(2_500)
        viewModel.toggleTimer()

        assertTrue(viewModel.uiState.value.remainingSeconds < 25 * 60)

        viewModel.selectTask(secondTaskId)

        assertEquals(secondTaskId, viewModel.professionalState.value.selectedTaskId)
        assertEquals(25 * 60, viewModel.uiState.value.remainingSeconds)
        assertFalse(viewModel.uiState.value.isRunning)
    }

    @Test
    fun `switching tasks while running stops and resets focus timer`() = runTest(testDispatcher) {
        val firstTaskId = viewModel.professionalState.value.tasks.first().id
        val secondTaskId = viewModel.professionalState.value.tasks[1].id

        viewModel.selectTask(firstTaskId)
        viewModel.setMode(TimerMode.FOCUS)
        viewModel.toggleTimer()
        advanceBy(1_100)

        viewModel.selectTask(secondTaskId)

        assertEquals(secondTaskId, viewModel.professionalState.value.selectedTaskId)
        assertFalse(viewModel.uiState.value.isRunning)
        assertEquals(25 * 60, viewModel.uiState.value.remainingSeconds)
    }

    @Test
    fun `starting and pausing focus timer schedules then cancels completion alarm`() = runTest(testDispatcher) {
        viewModel.setMode(TimerMode.FOCUS)

        viewModel.toggleTimer()

        assertEquals(25 * 60 * 1_000L, fakeAlarmScheduler.lastScheduledAtMs)
        assertEquals(0, fakeAlarmScheduler.cancelCount)

        advanceBy(1_200)
        viewModel.toggleTimer()

        assertEquals(1, fakeAlarmScheduler.cancelCount)
    }

    @Test
    fun `resuming paused focus timer schedules alarm with updated trigger time`() = runTest(testDispatcher) {
        viewModel.setMode(TimerMode.FOCUS)
        viewModel.toggleTimer()
        advanceBy(10_000)
        viewModel.toggleTimer()

        val scheduleBeforeResume = fakeAlarmScheduler.scheduleCount
        val cancelBeforeResume = fakeAlarmScheduler.cancelCount
        val remainingBeforeResume = viewModel.uiState.value.remainingSeconds

        viewModel.toggleTimer()

        assertEquals(scheduleBeforeResume + 1, fakeAlarmScheduler.scheduleCount)
        assertEquals(cancelBeforeResume, fakeAlarmScheduler.cancelCount)
        assertEquals(fakeClock.now() + (remainingBeforeResume * 1_000L), fakeAlarmScheduler.lastScheduledAtMs)

        viewModel.toggleTimer()
    }

    @Test
    fun `focus timer start and pause updates foreground controller state`() = runTest(testDispatcher) {
        viewModel.setMode(TimerMode.FOCUS)
        viewModel.toggleTimer()

        assertEquals(1, fakeForegroundController.runningCount)
        assertEquals(0, fakeForegroundController.pausedCount)

        advanceBy(1_100)
        viewModel.toggleTimer()

        assertEquals(1, fakeForegroundController.runningCount)
        assertEquals(1, fakeForegroundController.pausedCount)
    }

    @Test
    fun `stopSessionAndClear clears active session and stops foreground controller`() = runTest(testDispatcher) {
        viewModel.setMode(TimerMode.FOCUS)
        viewModel.toggleTimer()
        advanceBy(1_500)
        val stopCountBefore = fakeForegroundController.stopCount

        viewModel.stopSessionAndClear()

        assertFalse(viewModel.uiState.value.isRunning)
        assertNull(viewModel.activeSession.value)
        assertEquals(stopCountBefore + 1, fakeForegroundController.stopCount)
    }

    @Test
    fun `refreshSessionState resets running UI when persisted session is removed externally`() = runTest(testDispatcher) {
        viewModel.setMode(TimerMode.FOCUS)
        viewModel.toggleTimer()
        advanceBy(1_000)

        repository.clearActiveSession()
        viewModel.refreshSessionState()
        advanceBy(0)

        assertFalse(viewModel.uiState.value.isRunning)
        assertNull(viewModel.activeSession.value)
        assertEquals(25 * 60, viewModel.uiState.value.remainingSeconds)
    }

    @Test
    fun `refreshSessionState loads externally paused session into ui state`() = runTest(testDispatcher) {
        val selectedTaskId = viewModel.professionalState.value.selectedTaskId ?: error("expected selected task")
        repository.upsertActiveSession(
            ActiveSession(
                taskId = selectedTaskId,
                mode = TimerMode.FOCUS,
                startedAtMs = fakeClock.now(),
                scheduledCompletionMs = fakeClock.now() + 120_000L,
                isPaused = true,
                pausedAtMs = fakeClock.now(),
                pausedRemainingSecs = 120
            )
        )

        viewModel.refreshSessionState()
        advanceBy(0)

        assertFalse(viewModel.uiState.value.isRunning)
        assertEquals(120, viewModel.uiState.value.remainingSeconds)
        assertTrue(viewModel.activeSession.value?.isPaused == true)
        assertEquals(1, fakeForegroundController.pausedCount)
    }

    @Test
    fun `selectTask ignores unknown task ids`() {
        val selectedBefore = viewModel.professionalState.value.selectedTaskId

        viewModel.selectTask("missing-task-id")

        assertEquals(selectedBefore, viewModel.professionalState.value.selectedTaskId)
    }

    @Test
    fun `deleting selected task while focus timer is running resets timer`() = runTest(testDispatcher) {
        val selectedTask = viewModel.professionalState.value.tasks.first()

        viewModel.selectTask(selectedTask.id)
        viewModel.setMode(TimerMode.FOCUS)
        viewModel.toggleTimer()
        advanceBy(1_800)

        viewModel.deleteTask(selectedTask.id)

        assertFalse(viewModel.uiState.value.isRunning)
        assertEquals(25 * 60, viewModel.uiState.value.remainingSeconds)
        assertFalse(viewModel.professionalState.value.tasks.any { it.id == selectedTask.id })
    }

    @Test
    fun `updateTask edits title estimate and priority`() {
        val task = viewModel.professionalState.value.tasks.first()

        viewModel.updateTask(
            taskId = task.id,
            title = "Updated strategic plan",
            estimateMinutes = 75,
            priority = TaskPriority.P3,
            notes = "Follow up with client"
        )

        val updated = viewModel.professionalState.value.tasks.first { it.id == task.id }
        assertEquals("Updated strategic plan", updated.title)
        assertEquals(75, updated.estimateMinutes)
        assertEquals(TaskPriority.P3, updated.priority)
        assertEquals("Follow up with client", updated.notes)
    }

    @Test
    fun `setTaskArchived toggles archive state and updates filter selection`() {
        val task = viewModel.professionalState.value.tasks.first()

        viewModel.setTaskArchived(task.id, true)
        var updated = viewModel.professionalState.value.tasks.first { it.id == task.id }
        assertTrue(updated.isArchived)

        viewModel.setTaskFilter(TaskFilter.ARCHIVED)
        assertEquals(TaskFilter.ARCHIVED, viewModel.professionalState.value.taskFilter)

        viewModel.setTaskArchived(task.id, false)
        updated = viewModel.professionalState.value.tasks.first { it.id == task.id }
        assertFalse(updated.isArchived)
    }

    @Test
    fun `task filter and sort selections update professional state`() {
        viewModel.setTaskFilter(TaskFilter.ACTIVE)
        viewModel.setTaskSort(TaskSort.TITLE)

        val state = viewModel.professionalState.value
        assertEquals(TaskFilter.ACTIVE, state.taskFilter)
        assertEquals(TaskSort.TITLE, state.taskSort)
    }

    @Test
    fun `settings intents update professional settings state`() {
        viewModel.setThemePreference(ThemePreference.DARK)
        viewModel.setAutoStartBreak(true)
        viewModel.setAutoStartFocus(true)

        val settings = viewModel.professionalState.value.settings
        assertEquals(ThemePreference.DARK, settings.themePreference)
        assertTrue(settings.autoStartBreak)
        assertTrue(settings.autoStartFocus)
    }

    @Test
    fun `view model loads persisted settings from repository`() = runTest(testDispatcher) {
        val localRepository = InMemoryFocusRepository()
        localRepository.upsertAppSettings(
            AppSettings(
                themePreference = ThemePreference.LIGHT,
                autoStartBreak = true,
                autoStartFocus = false
            )
        )

        val restored = FocusViewModel(
            elapsedRealtimeProvider = fakeClock,
            repository = localRepository,
            sessionAlarmScheduler = fakeAlarmScheduler,
            sessionForegroundController = fakeForegroundController
        )

        testDispatcher.scheduler.runCurrent()

        val settings = restored.professionalState.value.settings
        assertEquals(ThemePreference.LIGHT, settings.themePreference)
        assertTrue(settings.autoStartBreak)
        assertFalse(settings.autoStartFocus)

        restored.resetTimer()
    }

    private fun advanceBy(milliseconds: Long) {
        fakeClock.advanceBy(milliseconds)
        testDispatcher.scheduler.advanceTimeBy(milliseconds)
        testDispatcher.scheduler.runCurrent()
    }

    private class FakeElapsedRealtimeProvider(
        private var nowMs: Long = 0L
    ) : ElapsedRealtimeProvider {
        override fun now(): Long = nowMs

        fun advanceBy(milliseconds: Long) {
            nowMs += milliseconds
        }
    }

    private class FakeSessionAlarmScheduler : com.lameck.ifocus.session.SessionAlarmScheduler {
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

    private class FakeSessionForegroundController : com.lameck.ifocus.session.SessionForegroundController {
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
}
