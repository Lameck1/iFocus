package com.lameck.ifocus.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lameck.ifocus.data.FocusRepository
import com.lameck.ifocus.data.InMemoryFocusRepository
import com.lameck.ifocus.reports.SessionCsvFormatter
import com.lameck.ifocus.session.SessionAlarmScheduler
import com.lameck.ifocus.session.SessionForegroundController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class TimerMode(val durationMinutes: Int) {
    FOCUS(25),
    SHORT_BREAK(5),
    LONG_BREAK(15)
}

data class TimerUiState(
    val remainingSeconds: Int = TimerMode.FOCUS.durationMinutes * 60,
    val totalSeconds: Int = TimerMode.FOCUS.durationMinutes * 60,
    val currentMode: TimerMode = TimerMode.FOCUS,
    val isRunning: Boolean = false
)

sealed interface TimerEvent {
    data class SessionCompleted(val taskTitle: String?) : TimerEvent
}

enum class TaskPriority {
    P1, P2, P3
}

enum class TaskFilter {
    ALL,
    ACTIVE,
    COMPLETED,
    ARCHIVED
}

enum class TaskSort {
    PRIORITY,
    ESTIMATE,
    TITLE
}

enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK
}

enum class TaskStatus {
    PLANNED,
    IN_PROGRESS,
    COMPLETED,
    BLOCKED
}

enum class SessionOutcome {
    DONE,
    PARTIAL,
    BLOCKED
}

enum class InterruptionReason {
    MEETING,
    CHAT,
    CALL,
    CONTEXT_SWITCH
}

data class FocusTask(
    val id: String,
    val title: String,
    val projectName: String = "",
    val estimateMinutes: Int,
    val priority: TaskPriority,
    val status: TaskStatus = TaskStatus.PLANNED,
    val todayMinutesFocused: Int = 0,
    val notes: String = "",
    val isArchived: Boolean = false,
    val plannedDateEpochDay: Long? = null,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

data class FocusProject(
    val name: String,
    val isArchived: Boolean = false,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

data class SessionRecord(
    val id: String,
    val taskTitle: String,
    val taskId: String? = null,
    val projectName: String? = null,
    val focusedMinutes: Int,
    val outcome: SessionOutcome,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class PendingSessionOutcome(
    val taskId: String,
    val taskTitle: String,
    val focusedMinutes: Int
)

data class PendingInterruption(
    val taskId: String?,
    val taskTitle: String?
)

data class AppSettings(
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val autoStartBreak: Boolean = false,
    val autoStartFocus: Boolean = false,
    val dailyGoalMinutes: Int = 120,
    val calendarSafePlanningEnabled: Boolean = true,
    val hasCompletedOnboarding: Boolean = false
)

data class ProfessionalUiState(
    val tasks: List<FocusTask>,
    val projects: List<FocusProject> = emptyList(),
    val selectedTaskId: String?,
    val activeSessionTaskId: String? = null,
    val pendingSessionOutcome: PendingSessionOutcome? = null,
    val sessionHistory: List<SessionRecord> = emptyList(),
    val interruptionCounts: Map<InterruptionReason, Int> = emptyMap(),
    val pendingInterruption: PendingInterruption? = null,
    val taskFilter: TaskFilter = TaskFilter.ALL,
    val taskSort: TaskSort = TaskSort.PRIORITY,
    val settings: AppSettings = AppSettings()
)

data class ActiveSession(
    val id: String = "singleton",
    val taskId: String,
    val mode: TimerMode,
    val startedAtMs: Long,
    val scheduledCompletionMs: Long,
    val isPaused: Boolean = false,
    val pausedAtMs: Long? = null,
    val pausedRemainingSecs: Int? = null
)

fun interface ElapsedRealtimeProvider {
    fun now(): Long
}

class FocusViewModel(
    private val elapsedRealtimeProvider: ElapsedRealtimeProvider = ElapsedRealtimeProvider { SystemClock.elapsedRealtime() },
    private val repository: FocusRepository = InMemoryFocusRepository(),
    private val sessionAlarmScheduler: SessionAlarmScheduler = SessionAlarmScheduler.NoOp,
    private val sessionForegroundController: SessionForegroundController = SessionForegroundController.NoOp
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TimerEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<TimerEvent> = _events.asSharedFlow()

    private val _professionalState = MutableStateFlow(initialProfessionalState())
    val professionalState: StateFlow<ProfessionalUiState> = _professionalState.asStateFlow()

    private val _activeSession = MutableStateFlow<ActiveSession?>(null)
    val activeSession: StateFlow<ActiveSession?> = _activeSession.asStateFlow()

    private var timerJob: Job? = null
    private var targetEndElapsedRealtimeMs: Long? = null

    companion object {
        private const val ONE_SECOND_MS = 1_000L
        private const val TICK_INTERVAL_MS = 250L
    }

    init {
        loadPersistedProfessionalState()
        recoverActiveSession(resetWhenMissing = false)
    }

    fun refreshSessionState() {
        recoverActiveSession(resetWhenMissing = true)
    }

    private fun recoverActiveSession(resetWhenMissing: Boolean) {
        viewModelScope.launch {
            val persisted = repository.loadActiveSession()

            if (resetWhenMissing && persisted == null) {
                timerJob?.cancel()
                timerJob = null
                targetEndElapsedRealtimeMs = null
                _activeSession.value = null
                _professionalState.update { it.copy(activeSessionTaskId = null) }
                val duration = _uiState.value.currentMode.durationMinutes * 60
                _uiState.update {
                    it.copy(
                        remainingSeconds = duration,
                        totalSeconds = duration,
                        isRunning = false
                    )
                }
                sessionForegroundController.stop()
                return@launch
            }

            if (persisted == null) return@launch

            if (resetWhenMissing) {
                timerJob?.cancel()
                timerJob = null
                targetEndElapsedRealtimeMs = null
            }

            val taskExists = _professionalState.value.tasks.any { it.id == persisted.taskId }
            if (!taskExists) {
                repository.clearActiveSession()
                sessionAlarmScheduler.cancel()
                if (resetWhenMissing) {
                    sessionForegroundController.stop()
                }
                return@launch
            }

            val durationSecs = persisted.mode.durationMinutes * 60
            val remainingSeconds = if (persisted.isPaused) {
                persisted.pausedRemainingSecs ?: durationSecs
            } else {
                val remainingMs = (persisted.scheduledCompletionMs - elapsedRealtimeProvider.now()).coerceAtLeast(0L)
                ((remainingMs + ONE_SECOND_MS - 1) / ONE_SECOND_MS).toInt()
            }

            if (remainingSeconds <= 0) {
                repository.clearActiveSession()
                sessionAlarmScheduler.cancel()
                return@launch
            }

            _activeSession.value = persisted.copy(
                pausedRemainingSecs = if (persisted.isPaused) remainingSeconds else null
            )
            _professionalState.update {
                it.copy(
                    selectedTaskId = persisted.taskId,
                    activeSessionTaskId = persisted.taskId
                )
            }
            _uiState.update {
                it.copy(
                    currentMode = persisted.mode,
                    remainingSeconds = remainingSeconds,
                    totalSeconds = durationSecs,
                    isRunning = false
                )
            }

            if (!persisted.isPaused) {
                startTimer()
            } else {
                updateForegroundPausedState(persisted.taskId, persisted.mode, remainingSeconds)
            }
        }
    }

    fun toggleTimer() {
        if (_uiState.value.isRunning) {
            val shouldCaptureInterruption =
                _uiState.value.currentMode == TimerMode.FOCUS &&
                    _uiState.value.remainingSeconds < _uiState.value.totalSeconds
            pauseSessionIfActive()
            if (shouldCaptureInterruption) {
                capturePendingInterruption()
            }
        } else {
            when {
                _activeSession.value?.isPaused == true -> resumeSessionIfPaused()
                _uiState.value.currentMode == TimerMode.FOCUS -> {
                    val selectedTaskId = _professionalState.value.selectedTaskId
                    if (selectedTaskId != null) {
                        startSessionOnTask(selectedTaskId)
                    }
                }
                else -> startTimer()
            }
        }
    }

    fun startSessionOnTask(taskId: String) {
        if (_professionalState.value.tasks.none { it.id == taskId }) return
        
        val mode = _uiState.value.currentMode
        val durationSecs = mode.durationMinutes * 60
        val nowMs = elapsedRealtimeProvider.now()
        val completionMs = nowMs + (durationSecs * 1_000L)

        val session = ActiveSession(
            taskId = taskId,
            mode = mode,
            startedAtMs = nowMs,
            scheduledCompletionMs = completionMs,
            isPaused = false
        )
        
        _activeSession.value = session
        _professionalState.update {
            it.copy(activeSessionTaskId = taskId)
        }
        _uiState.update {
            it.copy(
                remainingSeconds = durationSecs,
                totalSeconds = durationSecs,
                isRunning = false
            )
        }
        
        persistActiveSession(session)
        startTimer()
    }

    fun selectTask(taskId: String) {
        val taskExists = _professionalState.value.tasks.any { it.id == taskId }
        if (!taskExists) return

        val currentSelectedTaskId = _professionalState.value.selectedTaskId
        if (currentSelectedTaskId == taskId) return

        // If a focus timer is paused/running, reset it when switching tasks
        if (_uiState.value.currentMode == TimerMode.FOCUS) {
            resetTimer()
        }

        _professionalState.update { it.copy(selectedTaskId = taskId) }
    }

    fun addTask(
        title: String,
        projectName: String = "",
        estimateMinutes: Int = 50,
        priority: TaskPriority = TaskPriority.P2,
        plannedDateEpochDay: Long? = null
    ) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) return

        val newTask = FocusTask(
            id = UUID.randomUUID().toString(),
            title = trimmedTitle,
            projectName = projectName.trim(),
            estimateMinutes = estimateMinutes,
            priority = priority,
            plannedDateEpochDay = plannedDateEpochDay,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        _professionalState.update { state ->
            val updatedTasks = state.tasks + newTask
            state.copy(
                tasks = updatedTasks,
                selectedTaskId = state.selectedTaskId ?: newTask.id
            )
        }
        persistTasks()
        ensureProjectExists(newTask.projectName)
    }

    fun updateTask(
        taskId: String,
        title: String,
        projectName: String? = null,
        estimateMinutes: Int,
        priority: TaskPriority,
        notes: String,
        plannedDateEpochDay: Long? = null
    ) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) return
        val now = System.currentTimeMillis()

        _professionalState.update { state ->
            state.copy(
                tasks = state.tasks.map { task ->
                    if (task.id == taskId) {
                        task.copy(
                            title = trimmedTitle,
                            projectName = (projectName ?: task.projectName).trim(),
                            estimateMinutes = estimateMinutes.coerceIn(15, 180),
                            priority = priority,
                            notes = notes.trim(),
                            plannedDateEpochDay = plannedDateEpochDay ?: task.plannedDateEpochDay,
                            updatedAtEpochMs = now
                        )
                    } else {
                        task
                    }
                }
            )
        }
        persistTasks()
        val updatedTask = _professionalState.value.tasks.firstOrNull { it.id == taskId }
        ensureProjectExists(updatedTask?.projectName.orEmpty())
    }

    fun setTaskArchived(taskId: String, archived: Boolean) {
        val now = System.currentTimeMillis()
        _professionalState.update { state ->
            val updatedTasks = state.tasks.map { task ->
                if (task.id == taskId) {
                    task.copy(isArchived = archived, updatedAtEpochMs = now)
                } else {
                    task
                }
            }
            val selectedTaskId = state.selectedTaskId
            val selectedStillActive = updatedTasks.any { it.id == selectedTaskId && !it.isArchived }
            state.copy(
                tasks = updatedTasks,
                selectedTaskId = if (selectedStillActive) selectedTaskId else updatedTasks.firstOrNull { !it.isArchived }?.id
            )
        }
        persistTasks()
    }

    fun setTaskFilter(filter: TaskFilter) {
        _professionalState.update { it.copy(taskFilter = filter) }
    }

    fun setTaskSort(sort: TaskSort) {
        _professionalState.update { it.copy(taskSort = sort) }
    }

    fun deleteTask(taskId: String) {
        val deletingSelectedTask = _professionalState.value.selectedTaskId == taskId
        if (deletingSelectedTask && _uiState.value.currentMode == TimerMode.FOCUS) {
            // Prevent a running/paused focus block from being accidentally reassigned after deletion.
            resetTimer()
        }

        _professionalState.update { state ->
            val updatedTasks = state.tasks.filterNot { it.id == taskId }
            state.copy(
                tasks = updatedTasks,
                selectedTaskId = when {
                    state.selectedTaskId == taskId -> updatedTasks.firstOrNull()?.id
                    else -> state.selectedTaskId
                },
                pendingSessionOutcome = state.pendingSessionOutcome?.takeIf { it.taskId != taskId },
                pendingInterruption = state.pendingInterruption?.takeIf { it.taskId != taskId }
            )
        }
        persistTasks()
    }

    fun completePendingSession(outcome: SessionOutcome) {
        val pendingOutcome = _professionalState.value.pendingSessionOutcome ?: return
        val completedTask = _professionalState.value.tasks.firstOrNull { it.id == pendingOutcome.taskId }

        val updatedTasks = _professionalState.value.tasks.map { task ->
            if (task.id != pendingOutcome.taskId) {
                task
            } else {
                task.copy(
                    status = when (outcome) {
                        SessionOutcome.DONE -> TaskStatus.COMPLETED
                        SessionOutcome.PARTIAL -> TaskStatus.IN_PROGRESS
                        SessionOutcome.BLOCKED -> TaskStatus.BLOCKED
                    },
                    todayMinutesFocused = task.todayMinutesFocused + pendingOutcome.focusedMinutes
                )
            }
        }

        val newRecord = SessionRecord(
            id = UUID.randomUUID().toString(),
            taskTitle = pendingOutcome.taskTitle,
            taskId = pendingOutcome.taskId,
            projectName = completedTask?.projectName?.takeIf { it.isNotBlank() },
            focusedMinutes = pendingOutcome.focusedMinutes,
            outcome = outcome,
            createdAtEpochMs = System.currentTimeMillis()
        )

        _professionalState.update { state ->
            state.copy(
                tasks = updatedTasks,
                pendingSessionOutcome = null,
                sessionHistory = (state.sessionHistory + newRecord).takeLast(50),
                selectedTaskId = updatedTasks.firstOrNull { it.status != TaskStatus.COMPLETED && !it.isArchived }?.id
                    ?: state.selectedTaskId
            )
        }
        persistTasks()
        persistSessionRecord(newRecord)
    }

    fun dismissPendingSessionOutcome() {
        _professionalState.update { it.copy(pendingSessionOutcome = null) }
    }

    fun setThemePreference(themePreference: ThemePreference) {
        _professionalState.update {
            it.copy(settings = it.settings.copy(themePreference = themePreference))
        }
        persistSettings()
    }

    fun setAutoStartBreak(enabled: Boolean) {
        _professionalState.update {
            it.copy(settings = it.settings.copy(autoStartBreak = enabled))
        }
        persistSettings()
    }

    fun setAutoStartFocus(enabled: Boolean) {
        _professionalState.update {
            it.copy(settings = it.settings.copy(autoStartFocus = enabled))
        }
        persistSettings()
    }

    fun setDailyGoalMinutes(minutes: Int) {
        _professionalState.update {
            it.copy(settings = it.settings.copy(dailyGoalMinutes = minutes.coerceIn(25, 600)))
        }
        persistSettings()
    }

    fun setCalendarSafePlanningEnabled(enabled: Boolean) {
        _professionalState.update {
            it.copy(settings = it.settings.copy(calendarSafePlanningEnabled = enabled))
        }
        persistSettings()
    }

    fun completeOnboarding() {
        _professionalState.update {
            it.copy(settings = it.settings.copy(hasCompletedOnboarding = true))
        }
        persistSettings()
    }

    fun addProject(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        if (_professionalState.value.projects.any { it.name.equals(trimmed, ignoreCase = true) }) return
        val project = FocusProject(name = trimmed, updatedAtEpochMs = System.currentTimeMillis())
        _professionalState.update { it.copy(projects = (it.projects + project).sortedBy { p -> p.name.lowercase() }) }
        viewModelScope.launch { repository.upsertProject(project) }
    }

    fun renameProject(oldName: String, newName: String) {
        val source = oldName.trim()
        val target = newName.trim()
        if (source.isEmpty() || target.isEmpty() || source == target) return
        val now = System.currentTimeMillis()
        _professionalState.update { state ->
            val updatedTasks = state.tasks.map { task ->
                if (task.projectName == source) task.copy(projectName = target, updatedAtEpochMs = now) else task
            }
            val updatedProjects = state.projects
                .filterNot { it.name == source }
                .let { projects ->
                    if (projects.any { it.name.equals(target, ignoreCase = true) }) projects
                    else projects + FocusProject(name = target, updatedAtEpochMs = now)
                }
                .sortedBy { it.name.lowercase() }
            state.copy(tasks = updatedTasks, projects = updatedProjects)
        }
        persistTasks()
        viewModelScope.launch {
            repository.deleteProject(source)
            repository.upsertProject(FocusProject(name = target, updatedAtEpochMs = now))
        }
    }

    fun deleteProject(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val now = System.currentTimeMillis()
        _professionalState.update { state ->
            val updatedTasks = state.tasks.map { task ->
                if (task.projectName == trimmed) task.copy(projectName = "", updatedAtEpochMs = now) else task
            }
            state.copy(
                tasks = updatedTasks,
                projects = state.projects.filterNot { it.name == trimmed }
            )
        }
        persistTasks()
        viewModelScope.launch { repository.deleteProject(trimmed) }
    }

    fun buildSessionHistoryCsv(): String {
        return SessionCsvFormatter.format(_professionalState.value.sessionHistory)
    }

    fun reportInterruption(reason: InterruptionReason) {
        _professionalState.update { state ->
            val current = state.interruptionCounts[reason] ?: 0
            state.copy(
                interruptionCounts = state.interruptionCounts + (reason to current + 1),
                pendingInterruption = null
            )
        }
        persistInterruptionCounts()
    }

    fun dismissPendingInterruption() {
        _professionalState.update { it.copy(pendingInterruption = null) }
    }

    private fun startTimer() {
        if (_uiState.value.isRunning) return

        ensureActiveSessionForCurrentMode()

        if (_uiState.value.currentMode == TimerMode.FOCUS) {
            _professionalState.update { state ->
                val selectedTaskId = state.selectedTaskId ?: return@update state
                state.copy(
                    tasks = state.tasks.map { task ->
                        if (task.id == selectedTaskId && task.status == TaskStatus.PLANNED) {
                            task.copy(status = TaskStatus.IN_PROGRESS)
                        } else {
                            task
                        }
                    }
                )
            }
            persistTasks()
        }

        val remainingMs = _uiState.value.remainingSeconds * ONE_SECOND_MS
        targetEndElapsedRealtimeMs = elapsedRealtimeProvider.now() + remainingMs
        scheduleCompletionAlarm()
        _uiState.update { it.copy(isRunning = true) }
        if (_uiState.value.currentMode == TimerMode.FOCUS) {
            val taskId = _activeSession.value?.taskId ?: _professionalState.value.selectedTaskId
            updateForegroundRunningState(taskId, _uiState.value.currentMode, _uiState.value.remainingSeconds)
        }

        timerJob = viewModelScope.launch {
            while (true) {
                val endTime = targetEndElapsedRealtimeMs ?: break
                val timeLeftMs = (endTime - elapsedRealtimeProvider.now()).coerceAtLeast(0L)
                val remainingSeconds = ((timeLeftMs + ONE_SECOND_MS - 1) / ONE_SECOND_MS).toInt()
                _uiState.update { state ->
                    if (state.remainingSeconds == remainingSeconds) state
                    else state.copy(remainingSeconds = remainingSeconds)
                }

                if (timeLeftMs == 0L) {
                    onTimerFinished()
                    break
                }

                delay(TICK_INTERVAL_MS)
            }
        }
    }

    private fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null

        val remainingSeconds = targetEndElapsedRealtimeMs?.let { endTime ->
            val timeLeftMs = (endTime - elapsedRealtimeProvider.now()).coerceAtLeast(0L)
            ((timeLeftMs + ONE_SECOND_MS - 1) / ONE_SECOND_MS).toInt()
        } ?: _uiState.value.remainingSeconds

        targetEndElapsedRealtimeMs = null
        sessionAlarmScheduler.cancel()
        _uiState.update { it.copy(isRunning = false, remainingSeconds = remainingSeconds) }
        if (_uiState.value.currentMode == TimerMode.FOCUS) {
            val taskId = _activeSession.value?.taskId ?: _professionalState.value.selectedTaskId
            updateForegroundPausedState(taskId, _uiState.value.currentMode, remainingSeconds)
        }
    }

    fun resetTimer() {
        pauseTimer()
        clearActiveSessionState()
        val duration = _uiState.value.currentMode.durationMinutes * 60
        _uiState.update {
            it.copy(
                remainingSeconds = duration,
                totalSeconds = duration,
                isRunning = false
            )
        }
    }

    fun setMode(mode: TimerMode) {
        pauseTimer()
        clearActiveSessionState()
        val duration = mode.durationMinutes * 60
        _uiState.update {
            it.copy(
                currentMode = mode,
                remainingSeconds = duration,
                totalSeconds = duration,
                isRunning = false
            )
        }
    }

    private fun onTimerFinished() {
        timerJob?.cancel()
        timerJob = null
        targetEndElapsedRealtimeMs = null
        sessionAlarmScheduler.cancel()
        val completedMode = _uiState.value.currentMode
        val selectedTask = _professionalState.value.tasks.firstOrNull { it.id == _professionalState.value.selectedTaskId }

        clearActiveSessionState()

        if (completedMode == TimerMode.FOCUS && selectedTask != null) {
            _professionalState.update {
                it.copy(
                    pendingSessionOutcome = PendingSessionOutcome(
                        taskId = selectedTask.id,
                        taskTitle = selectedTask.title,
                        focusedMinutes = (_uiState.value.totalSeconds / 60).coerceAtLeast(1)
                    )
                )
            }
        }

        when (completedMode) {
            TimerMode.FOCUS -> {
                prepareNextMode(TimerMode.SHORT_BREAK)
                if (_professionalState.value.settings.autoStartBreak) {
                    startTimer()
                }
            }

            TimerMode.SHORT_BREAK,
            TimerMode.LONG_BREAK -> {
                prepareNextMode(TimerMode.FOCUS)
                if (_professionalState.value.settings.autoStartFocus && _professionalState.value.selectedTaskId != null) {
                    startTimer()
                }
            }
        }

        _events.tryEmit(TimerEvent.SessionCompleted(selectedTask?.title))
    }

    private fun prepareNextMode(nextMode: TimerMode) {
        val duration = nextMode.durationMinutes * 60
        _uiState.update {
            it.copy(
                currentMode = nextMode,
                remainingSeconds = duration,
                totalSeconds = duration,
                isRunning = false
            )
        }
    }

    private fun initialProfessionalState(): ProfessionalUiState {
        val initialTasks = listOf(
            FocusTask(
                id = UUID.randomUUID().toString(),
                title = "Draft client proposal",
                projectName = "Client Work",
                estimateMinutes = 90,
                priority = TaskPriority.P1
            ),
            FocusTask(
                id = UUID.randomUUID().toString(),
                title = "Review pull requests",
                projectName = "Engineering",
                estimateMinutes = 50,
                priority = TaskPriority.P2
            ),
            FocusTask(
                id = UUID.randomUUID().toString(),
                title = "Plan sprint priorities",
                projectName = "Planning",
                estimateMinutes = 30,
                priority = TaskPriority.P1
            )
        )
        val initialProjects = initialTasks.mapNotNull { task ->
            task.projectName.takeIf { it.isNotBlank() }?.let { FocusProject(name = it) }
        }.distinctBy { it.name }
        return ProfessionalUiState(
            tasks = initialTasks,
            projects = initialProjects,
            selectedTaskId = initialTasks.firstOrNull()?.id
        )
    }

    private fun capturePendingInterruption() {
        val selectedTask = _professionalState.value.tasks.firstOrNull { it.id == _professionalState.value.selectedTaskId }
        _professionalState.update {
            it.copy(
                pendingInterruption = PendingInterruption(
                    taskId = selectedTask?.id,
                    taskTitle = selectedTask?.title
                )
            )
        }
    }

    private fun loadPersistedProfessionalState() {
        viewModelScope.launch {
            val persistedTasks = repository.loadTasks()
            val persistedProjects = repository.loadProjects()
            val persistedSessions = repository.loadSessionHistory()
            val interruptionCounts = repository.loadInterruptionCounts()
            val persistedSettings = repository.loadAppSettings()
            if (persistedTasks.isEmpty()) {
                persistTasks()
                persistProjects()
                _professionalState.update {
                    it.copy(
                        interruptionCounts = interruptionCounts,
                        settings = persistedSettings
                    )
                }
                persistSettings()
                return@launch
            }

            _professionalState.update { state ->
                state.copy(
                    tasks = persistedTasks,
                        projects = mergeProjects(persistedProjects, persistedTasks),
                    selectedTaskId = persistedTasks.firstOrNull { it.id == state.selectedTaskId }?.id
                        ?: persistedTasks.firstOrNull()?.id,
                    sessionHistory = persistedSessions,
                    interruptionCounts = interruptionCounts,
                    settings = persistedSettings
                )
            }
        }
    }

    private fun mergeProjects(projects: List<FocusProject>, tasks: List<FocusTask>): List<FocusProject> {
        val taskProjects = tasks.mapNotNull { it.projectName.takeIf(String::isNotBlank) }
            .distinct()
            .map { FocusProject(name = it) }
        return (projects + taskProjects)
            .distinctBy { it.name.lowercase() }
            .sortedBy { it.name.lowercase() }
    }

    private fun persistTasks() {
        val tasks = _professionalState.value.tasks
        viewModelScope.launch {
            repository.upsertTasks(tasks)
        }
    }

    private fun persistProjects() {
        val projects = _professionalState.value.projects
        viewModelScope.launch {
            repository.upsertProjects(projects)
        }
    }

    private fun ensureProjectExists(projectName: String) {
        val trimmed = projectName.trim()
        if (trimmed.isEmpty()) return
        if (_professionalState.value.projects.any { it.name.equals(trimmed, ignoreCase = true) }) return
        val project = FocusProject(name = trimmed, updatedAtEpochMs = System.currentTimeMillis())
        _professionalState.update { it.copy(projects = (it.projects + project).sortedBy { p -> p.name.lowercase() }) }
        viewModelScope.launch { repository.upsertProject(project) }
    }

    private fun persistSessionRecord(record: SessionRecord) {
        viewModelScope.launch {
            repository.insertSession(record)
        }
    }

    private fun persistInterruptionCounts() {
        val counts = _professionalState.value.interruptionCounts
        viewModelScope.launch {
            repository.upsertInterruptionCounts(counts)
        }
    }

    private fun persistSettings() {
        val settings = _professionalState.value.settings
        viewModelScope.launch {
            repository.upsertAppSettings(settings)
        }
    }

    fun pauseSessionIfActive() {
        pauseTimer()
        
        val remainingSeconds = _uiState.value.remainingSeconds
        _activeSession.value?.let { session ->
            val pausedSession = session.copy(
                isPaused = true,
                pausedAtMs = elapsedRealtimeProvider.now(),
                pausedRemainingSecs = remainingSeconds
            )
            _activeSession.value = pausedSession
            persistActiveSession(pausedSession)
        }
    }

    fun resumeSessionIfPaused() {
        val session = _activeSession.value ?: return
        if (!session.isPaused) return

        val remainingMs = (session.pausedRemainingSecs ?: _uiState.value.remainingSeconds).toLong() * ONE_SECOND_MS
        val nowMs = elapsedRealtimeProvider.now()
        val newCompletionMs = nowMs + remainingMs

        val resumed = session.copy(
            isPaused = false,
            pausedAtMs = null,
            pausedRemainingSecs = null,
            startedAtMs = nowMs,
            scheduledCompletionMs = newCompletionMs
        )

        _activeSession.value = resumed
        _uiState.update { it.copy(isRunning = false) }
        persistActiveSession(resumed)
        startTimer()
    }

    fun stopSessionAndClear() {
        timerJob?.cancel()
        timerJob = null
        targetEndElapsedRealtimeMs = null

        clearActiveSessionState()
        _uiState.update { it.copy(isRunning = false) }
    }

    private fun persistActiveSession(session: ActiveSession) {
        viewModelScope.launch {
            repository.upsertActiveSession(session)
        }
    }

    private fun clearActiveSessionState() {
        _activeSession.value = null
        _professionalState.update { it.copy(activeSessionTaskId = null) }
        sessionAlarmScheduler.cancel()
        sessionForegroundController.stop()
        viewModelScope.launch {
            repository.clearActiveSession()
        }
    }

    private fun updateForegroundRunningState(taskId: String?, mode: TimerMode, remainingSeconds: Int) {
        sessionForegroundController.showRunning(
            taskTitle = resolveTaskTitle(taskId),
            modeName = mode.name,
            remainingSeconds = remainingSeconds
        )
    }

    private fun updateForegroundPausedState(taskId: String?, mode: TimerMode, remainingSeconds: Int) {
        sessionForegroundController.showPaused(
            taskTitle = resolveTaskTitle(taskId),
            modeName = mode.name,
            remainingSeconds = remainingSeconds
        )
    }

    private fun resolveTaskTitle(taskId: String?): String? {
        if (taskId == null) return null
        return _professionalState.value.tasks.firstOrNull { it.id == taskId }?.title
    }

    override fun onCleared() {
        sessionForegroundController.stop()
        super.onCleared()
    }

    private fun ensureActiveSessionForCurrentMode() {
        if (_activeSession.value != null) return

        val selectedTaskId = _professionalState.value.selectedTaskId ?: return
        val mode = _uiState.value.currentMode
        val nowMs = elapsedRealtimeProvider.now()
        val completionMs = nowMs + (_uiState.value.remainingSeconds * ONE_SECOND_MS)
        val session = ActiveSession(
            taskId = selectedTaskId,
            mode = mode,
            startedAtMs = nowMs,
            scheduledCompletionMs = completionMs,
            isPaused = false
        )

        _activeSession.value = session
        _professionalState.update { it.copy(activeSessionTaskId = selectedTaskId) }
        persistActiveSession(session)
    }

    private fun scheduleCompletionAlarm() {
        val triggerAtMs = targetEndElapsedRealtimeMs ?: return
        val taskTitle = _professionalState.value.tasks
            .firstOrNull { it.id == _professionalState.value.selectedTaskId }
            ?.title
        sessionAlarmScheduler.schedule(triggerAtMs, taskTitle)
    }
}
