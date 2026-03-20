package com.lameck.ifocus.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.lameck.ifocus.R
import com.lameck.ifocus.ui.theme.IFocusTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    viewModel: FocusViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val professionalState by viewModel.professionalState.collectAsStateWithLifecycle()
    val snackBarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val sessionCompleteMessage = stringResource(R.string.focus_session_complete)
    val sessionCompleteForTask = stringResource(R.string.focus_session_complete_for_task)
    val csvExportSuccess = stringResource(R.string.csv_export_success)
    val csvExportFailed = stringResource(R.string.csv_export_failed)
    val notificationsPermissionDenied = stringResource(R.string.notifications_permission_denied)
    val scrollState = rememberScrollState()
    val selectedTask = professionalState.tasks.firstOrNull { it.id == professionalState.selectedTaskId }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var pendingStartAfterPermission by remember { mutableStateOf(false) }
    val notificationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingStartAfterPermission) {
            viewModel.toggleTimer()
        } else if (!granted && pendingStartAfterPermission) {
            scope.launch {
                snackBarHostState.showSnackbar(notificationsPermissionDenied)
            }
        }
        pendingStartAfterPermission = false
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.refreshSessionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            if (event is TimerEvent.SessionCompleted) {
                try {
                    val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    val r = RingtoneManager.getRingtone(context, notification)
                    r.play()
                } catch (_: Exception) {
                    // Ignore errors playing sound
                }

                val message = event.taskTitle?.let { taskTitle ->
                    sessionCompleteForTask.format(taskTitle)
                } ?: sessionCompleteMessage
                snackBarHostState.showSnackbar(message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackBarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.focus_dashboard_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FocusOverviewCard(
                selectedTask = selectedTask,
                uiState = uiState
            )

            TodayPlanCard(
                state = professionalState,
                onSelectTask = viewModel::selectTask,
                onAddTask = { title, projectName ->
                    viewModel.addTask(title = title, projectName = projectName)
                },
                onDeleteTask = viewModel::deleteTask,
                onArchiveTask = { taskId -> viewModel.setTaskArchived(taskId, true) },
                onUnarchiveTask = { taskId -> viewModel.setTaskArchived(taskId, false) },
                onUpdateTask = { taskId, title, projectName, estimateMinutes, priority, notes, plannedDateEpochDay ->
                    viewModel.updateTask(taskId, title, projectName, estimateMinutes, priority, notes, plannedDateEpochDay)
                },
                onFilterChanged = viewModel::setTaskFilter,
                onSortChanged = viewModel::setTaskSort
            )

            ProjectsCard(
                projects = professionalState.projects,
                onAddProject = viewModel::addProject,
                onRenameProject = viewModel::renameProject,
                onDeleteProject = viewModel::deleteProject
            )

            TimerWorkspaceCard(
                uiState = uiState,
                onModeSelected = viewModel::setMode,
                onToggle = {
                    val requiresPermission =
                        !uiState.isRunning &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED

                    if (requiresPermission) {
                        pendingStartAfterPermission = true
                        notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.toggleTimer()
                    }
                },
                onReset = viewModel::resetTimer
            )

            WeeklyInsightsCard(
                professionalState = professionalState,
                onExportCsv = {
                    scope.launch {
                        val csv = viewModel.buildSessionHistoryCsv()
                        val result = exportCsvToAppStorage(context, csv)
                        val message = if (result.isSuccess) {
                            csvExportSuccess.format(result.getOrNull()?.name ?: "sessions.csv")
                        } else {
                            csvExportFailed
                        }
                        snackBarHostState.showSnackbar(message)
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    professionalState.pendingSessionOutcome?.let { pendingOutcome ->
        SessionOutcomeDialog(
            pendingOutcome = pendingOutcome,
            onDismiss = viewModel::dismissPendingSessionOutcome,
            onOutcomeSelected = viewModel::completePendingSession
        )
    }

    professionalState.pendingInterruption?.let { pendingInterruption ->
        InterruptionDialog(
            pendingInterruption = pendingInterruption,
            onDismiss = viewModel::dismissPendingInterruption,
            onReasonSelected = viewModel::reportInterruption
        )
    }
}

@Composable
private fun TimerWorkspaceCard(
    uiState: TimerUiState,
    onModeSelected: (TimerMode) -> Unit,
    onToggle: () -> Unit,
    onReset: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.focus_timer),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )

            ModeSelector(
                currentMode = uiState.currentMode,
                onModeSelected = onModeSelected
            )

            TimerDisplay(
                remainingSeconds = uiState.remainingSeconds,
                totalSeconds = uiState.totalSeconds,
                ringSize = 250.dp
            )

            PlaybackControls(
                isRunning = uiState.isRunning,
                onToggle = onToggle,
                onReset = onReset
            )
        }
    }
}

@Composable
private fun FocusOverviewCard(
    selectedTask: FocusTask?,
    uiState: TimerUiState
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.current_focus_block),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = selectedTask?.title ?: stringResource(R.string.select_task_prompt),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(text = stringResource(uiState.currentMode.labelResId)) }
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(
                            text = if (uiState.isRunning) {
                                stringResource(R.string.timer_state_running)
                            } else {
                                stringResource(R.string.timer_state_paused)
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun TodayPlanCard(
    state: ProfessionalUiState,
    onSelectTask: (String) -> Unit,
    onAddTask: (String, String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onArchiveTask: (String) -> Unit,
    onUnarchiveTask: (String) -> Unit,
    onUpdateTask: (String, String, String, Int, TaskPriority, String, Long?) -> Unit,
    onFilterChanged: (TaskFilter) -> Unit,
    onSortChanged: (TaskSort) -> Unit
) {
    var newTaskTitle by rememberSaveable { mutableStateOf("") }
    var newTaskProject by rememberSaveable { mutableStateOf("") }
    var editingTask by remember { mutableStateOf<FocusTask?>(null) }
    val setEditingTask: (FocusTask?) -> Unit = { task -> editingTask = task }
    val todayEpochDay = System.currentTimeMillis().toEpochDay()
    val visibleTasks = state.tasks
        .visibleTasks(state.taskFilter, state.taskSort)
        .filter { task ->
            if (!state.settings.calendarSafePlanningEnabled) true
            else task.plannedDateEpochDay == null || task.plannedDateEpochDay <= todayEpochDay
        }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.today_focus_plan),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.task_list_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TaskFilterSelector(
                selectedFilter = state.taskFilter,
                onFilterChanged = onFilterChanged
            )

            TaskSortSelector(
                selectedSort = state.taskSort,
                onSortChanged = onSortChanged
            )

            if (visibleTasks.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.no_tasks_for_filter),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                visibleTasks
                    .take(4)
                    .forEach { task ->
                        TaskListItem(
                            task = task,
                            selected = task.id == state.selectedTaskId,
                            onSelect = { onSelectTask(task.id) },
                            onEdit = { setEditingTask(task) },
                            onArchiveToggle = {
                                if (task.isArchived) onUnarchiveTask(task.id) else onArchiveTask(task.id)
                            },
                            onDelete = { onDeleteTask(task.id) }
                        )
                    }
            }

            OutlinedTextField(
                value = newTaskTitle,
                onValueChange = { newTaskTitle = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(text = stringResource(R.string.new_task_hint)) }
            )
            OutlinedTextField(
                value = newTaskProject,
                onValueChange = { newTaskProject = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(text = stringResource(R.string.project_name)) }
            )

            Button(
                onClick = {
                    onAddTask(newTaskTitle, newTaskProject)
                    newTaskTitle = ""
                    newTaskProject = ""
                },
                enabled = newTaskTitle.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = stringResource(R.string.add_task))
            }
        }
    }

    editingTask?.let { taskToEdit ->
        EditTaskDialog(
            task = taskToEdit,
            onDismiss = { setEditingTask(null) },
            onSave = { title, projectName, estimateMinutes, priority, notes, plannedDateEpochDay ->
                onUpdateTask(taskToEdit.id, title, projectName, estimateMinutes, priority, notes, plannedDateEpochDay)
                setEditingTask(null)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskFilterSelector(
    selectedFilter: TaskFilter,
    onFilterChanged: (TaskFilter) -> Unit
) {
    val filters = TaskFilter.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        filters.forEachIndexed { index, filter ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = filters.size),
                onClick = { onFilterChanged(filter) },
                selected = selectedFilter == filter,
                label = { Text(text = stringResource(filter.labelResId)) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskSortSelector(
    selectedSort: TaskSort,
    onSortChanged: (TaskSort) -> Unit
) {
    val sorts = TaskSort.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        sorts.forEachIndexed { index, sort ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = sorts.size),
                onClick = { onSortChanged(sort) },
                selected = selectedSort == sort,
                label = { Text(text = stringResource(sort.labelResId)) }
            )
        }
    }
}

@Composable
private fun EditTaskDialog(
    task: FocusTask,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, TaskPriority, String, Long?) -> Unit
) {
    var taskTitle by rememberSaveable(task.id) { mutableStateOf(task.title) }
    var projectName by rememberSaveable(task.id) { mutableStateOf(task.projectName) }
    var estimateText by rememberSaveable(task.id) { mutableStateOf(task.estimateMinutes.toString()) }
    var priority by rememberSaveable(task.id) { mutableStateOf(task.priority) }
    var notes by rememberSaveable(task.id) { mutableStateOf(task.notes) }
    var plannedDateEpochDay by rememberSaveable(task.id) { mutableStateOf(task.plannedDateEpochDay) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.edit_task)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = taskTitle,
                    onValueChange = { taskTitle = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.task_title)) }
                )
                OutlinedTextField(
                    value = estimateText,
                    onValueChange = { estimateText = it.filter(Char::isDigit).take(3) },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.estimate_minutes)) }
                )
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.project_name)) }
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(text = stringResource(R.string.task_notes)) }
                )
                PlanningSelector(
                    plannedDateEpochDay = plannedDateEpochDay,
                    onChange = { plannedDateEpochDay = it }
                )
                TaskPrioritySelector(
                    selectedPriority = priority,
                    onPriorityChanged = { priority = it }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.dismiss)) }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val estimate = estimateText.toIntOrNull() ?: task.estimateMinutes
                    onSave(taskTitle, projectName, estimate, priority, notes, plannedDateEpochDay)
                }
            ) {
                Text(text = stringResource(R.string.save))
            }
        }
    )
}

@Composable
private fun PlanningSelector(plannedDateEpochDay: Long?, onChange: (Long?) -> Unit) {
    val today = System.currentTimeMillis().toEpochDay()
    val tomorrow = today + 1
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        val items = listOf<Long?>(null, today, tomorrow)
        items.forEachIndexed { index, value ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = items.size),
                selected = plannedDateEpochDay == value,
                onClick = { onChange(value) },
                label = {
                    Text(
                        when (value) {
                            null -> stringResource(R.string.plan_unscheduled)
                            today -> stringResource(R.string.plan_today)
                            else -> stringResource(R.string.plan_tomorrow)
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun ProjectsCard(
    projects: List<FocusProject>,
    onAddProject: (String) -> Unit,
    onRenameProject: (String, String) -> Unit,
    onDeleteProject: (String) -> Unit
) {
    var newProject by rememberSaveable { mutableStateOf("") }
    var renaming by remember { mutableStateOf<FocusProject?>(null) }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.projects_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            projects.filterNot { it.isArchived }.take(5).forEach { project ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(project.name, modifier = Modifier.weight(1f))
                    TextButton(onClick = { renaming = project }) { Text(stringResource(R.string.edit_task)) }
                    TextButton(onClick = { onDeleteProject(project.name) }) { Text(stringResource(R.string.delete_task)) }
                }
            }
            OutlinedTextField(
                value = newProject,
                onValueChange = { newProject = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.project_add_hint)) }
            )
            Button(
                onClick = {
                    onAddProject(newProject)
                    newProject = ""
                },
                enabled = newProject.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.project_add))
            }
        }
    }

    renaming?.let { project ->
        RenameProjectDialog(
            project = project,
            onDismiss = { renaming = null },
            onSave = { newName ->
                onRenameProject(project.name, newName)
                renaming = null
            }
        )
    }
}

@Composable
private fun RenameProjectDialog(project: FocusProject, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable(project.name) { mutableStateOf(project.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.project_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.project_name)) }
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) } },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text(stringResource(R.string.save)) } }
    )
}

private fun Long.toEpochDay(): Long = TimeUnit.MILLISECONDS.toDays(this)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskPrioritySelector(
    selectedPriority: TaskPriority,
    onPriorityChanged: (TaskPriority) -> Unit
) {
    val priorities = TaskPriority.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        priorities.forEachIndexed { index, taskPriority ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = priorities.size),
                onClick = { onPriorityChanged(taskPriority) },
                selected = taskPriority == selectedPriority,
                label = { Text(text = taskPriority.name) }
            )
        }
    }
}

@Composable
private fun TaskListItem(
    task: FocusTask,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = CardDefaults.outlinedCardBorder(enabled = true)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (task.projectName.isNotBlank()) {
                    Text(
                        text = task.projectName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = task.priority.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.minutes_short, task.estimateMinutes),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = task.status.label(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (task.isArchived) {
                        Text(
                            text = stringResource(R.string.task_archived),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                if (task.notes.isNotBlank()) {
                    Text(
                        text = task.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                task.plannedDateEpochDay?.let {
                    Text(
                        text = stringResource(R.string.plan_for_day, it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.edit_task)
                )
            }
            OutlinedIconButton(onClick = onArchiveToggle, modifier = Modifier.size(34.dp)) {
                Text(
                    text = if (task.isArchived) {
                        stringResource(R.string.task_unarchive_short)
                    } else {
                        stringResource(R.string.task_archive_short)
                    }
                )
            }
            OutlinedIconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_task)
                )
            }
        }
    }
}

@Composable
private fun WeeklyInsightsCard(
    professionalState: ProfessionalUiState,
    onExportCsv: () -> Unit
) {
    val totalMinutes = professionalState.sessionHistory.sumOf { it.focusedMinutes }
    val doneSessions = professionalState.sessionHistory.count { it.outcome == SessionOutcome.DONE }
    val sessionCount = professionalState.sessionHistory.size
    val completionRate = if (sessionCount == 0) 0 else (doneSessions * 100) / sessionCount
    val interruptionCount = professionalState.interruptionCounts.values.sum()
    val topInterruption = professionalState.interruptionCounts.maxByOrNull { it.value }?.key

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.weekly_insights),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            HorizontalDivider()
            Text(
                text = pluralStringResource(
                    R.plurals.focused_minutes_value,
                    totalMinutes,
                    totalMinutes
                )
            )
            Text(text = stringResource(R.string.sessions_completed_value, doneSessions, sessionCount))
            Text(text = stringResource(R.string.completion_rate_value, completionRate))
            Text(
                text = pluralStringResource(
                    R.plurals.interruptions_value,
                    interruptionCount,
                    interruptionCount
                )
            )
            topInterruption?.let {
                Text(text = stringResource(R.string.top_interruption_value, it.label()))
            }
            OutlinedButton(
                onClick = onExportCsv,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.export_csv))
            }
        }
    }
}

private fun exportCsvToAppStorage(context: android.content.Context, csvContent: String): Result<File> {
    return runCatching {
        val exportDir = File(context.filesDir, "exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val output = File(exportDir, "focus_sessions_$timestamp.csv")
        output.writeText(csvContent)
        output
    }
}

@Composable
private fun InterruptionDialog(
    pendingInterruption: PendingInterruption,
    onDismiss: () -> Unit,
    onReasonSelected: (InterruptionReason) -> Unit
) {
    val taskLabel = pendingInterruption.taskTitle ?: stringResource(R.string.current_focus_block)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.interruption_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(text = stringResource(R.string.interruption_prompt, taskLabel))

                Button(
                    onClick = { onReasonSelected(InterruptionReason.MEETING) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.interruption_meeting))
                }
                Button(
                    onClick = { onReasonSelected(InterruptionReason.CHAT) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.interruption_chat))
                }
                Button(
                    onClick = { onReasonSelected(InterruptionReason.CALL) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.interruption_call))
                }
                Button(
                    onClick = { onReasonSelected(InterruptionReason.CONTEXT_SWITCH) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.interruption_context_switch))
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.dismiss))
                }
            }
        }
    }
}

@Composable
private fun SessionOutcomeDialog(
    pendingOutcome: PendingSessionOutcome,
    onDismiss: () -> Unit,
    onOutcomeSelected: (SessionOutcome) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.session_wrap_up)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = pluralStringResource(
                        R.plurals.session_outcome_prompt,
                        pendingOutcome.focusedMinutes,
                        pendingOutcome.taskTitle,
                        pendingOutcome.focusedMinutes
                    )
                )
                Button(
                    onClick = { onOutcomeSelected(SessionOutcome.DONE) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.done))
                }
                Button(
                    onClick = { onOutcomeSelected(SessionOutcome.PARTIAL) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.partial))
                }
                Button(
                    onClick = { onOutcomeSelected(SessionOutcome.BLOCKED) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.blocked))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dismiss))
            }
        },
        confirmButton = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSelector(
    currentMode: TimerMode,
    onModeSelected: (TimerMode) -> Unit
) {
    val modes = TimerMode.entries.toTypedArray()

    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth()
    ) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                onClick = { onModeSelected(mode) },
                selected = currentMode == mode,
                modifier = Modifier.testTag(mode.tag),
                label = {
                    Text(
                        text = stringResource(mode.labelResId),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            )
        }
    }
}

@Composable
fun TimerDisplay(
    remainingSeconds: Int,
    totalSeconds: Int,
    ringSize: androidx.compose.ui.unit.Dp = 280.dp
) {
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val progress by animateFloatAsState(
        targetValue = if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds else 0f,
        label = "TimerProgress"
    )
    val timerText = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    val timerLabel = stringResource(R.string.timer_label)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag(FocusTestTags.TIMER_DISPLAY)
            .semantics {
                contentDescription = timerLabel
                stateDescription = timerText
            }
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(ringSize),
            strokeWidth = 12.dp,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            strokeCap = StrokeCap.Round,
        )
        Text(
            text = timerText,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 72.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun PlaybackControls(
    isRunning: Boolean,
    onToggle: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier
                .height(56.dp)
                .testTag(FocusTestTags.RESET_BUTTON)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.reset_timer),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(text = stringResource(R.string.reset_timer))
        }

        Spacer(modifier = Modifier.size(12.dp))

        Button(
            onClick = onToggle,
            modifier = Modifier
                .height(56.dp)
                .testTag(FocusTestTags.TOGGLE_BUTTON)
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isRunning) {
                    stringResource(R.string.pause)
                } else {
                    stringResource(R.string.play)
                }
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = if (isRunning) stringResource(R.string.pause_focus) else stringResource(R.string.start_focus)
            )
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp,navigation=buttons")
@Composable
fun FocusScreenPreview() {
    IFocusTheme {
        FocusScreen()
    }
}

private val TimerMode.labelResId: Int
    get() = when (this) {
        TimerMode.FOCUS -> R.string.focus
        TimerMode.SHORT_BREAK -> R.string.short_break
        TimerMode.LONG_BREAK -> R.string.long_break
    }

private val TimerMode.tag: String
    get() = when (this) {
        TimerMode.FOCUS -> FocusTestTags.MODE_FOCUS
        TimerMode.SHORT_BREAK -> FocusTestTags.MODE_SHORT_BREAK
        TimerMode.LONG_BREAK -> FocusTestTags.MODE_LONG_BREAK
    }

object FocusTestTags {
    const val TIMER_DISPLAY = "timer_display"
    const val TOGGLE_BUTTON = "toggle_button"
    const val RESET_BUTTON = "reset_button"
    const val MODE_FOCUS = "mode_focus"
    const val MODE_SHORT_BREAK = "mode_short_break"
    const val MODE_LONG_BREAK = "mode_long_break"
}

@Composable
private fun InterruptionReason.label(): String {
    return when (this) {
        InterruptionReason.MEETING -> stringResource(R.string.interruption_meeting)
        InterruptionReason.CHAT -> stringResource(R.string.interruption_chat)
        InterruptionReason.CALL -> stringResource(R.string.interruption_call)
        InterruptionReason.CONTEXT_SWITCH -> stringResource(R.string.interruption_context_switch)
    }
}

@Composable
private fun TaskStatus.label(): String {
    return when (this) {
        TaskStatus.PLANNED -> stringResource(R.string.task_status_planned)
        TaskStatus.IN_PROGRESS -> stringResource(R.string.task_status_in_progress)
        TaskStatus.COMPLETED -> stringResource(R.string.task_status_completed)
        TaskStatus.BLOCKED -> stringResource(R.string.task_status_blocked)
    }
}

private val TaskFilter.labelResId: Int
    get() = when (this) {
        TaskFilter.ALL -> R.string.task_filter_all
        TaskFilter.ACTIVE -> R.string.task_filter_active
        TaskFilter.COMPLETED -> R.string.task_filter_completed
        TaskFilter.ARCHIVED -> R.string.task_filter_archived
    }

private val TaskSort.labelResId: Int
    get() = when (this) {
        TaskSort.PRIORITY -> R.string.task_sort_priority
        TaskSort.ESTIMATE -> R.string.task_sort_estimate
        TaskSort.TITLE -> R.string.task_sort_title
    }

private fun List<FocusTask>.visibleTasks(filter: TaskFilter, sort: TaskSort): List<FocusTask> {
    val filtered = when (filter) {
        TaskFilter.ALL -> filter { !it.isArchived }
        TaskFilter.ACTIVE -> filter { !it.isArchived && it.status != TaskStatus.COMPLETED }
        TaskFilter.COMPLETED -> filter { !it.isArchived && it.status == TaskStatus.COMPLETED }
        TaskFilter.ARCHIVED -> filter { it.isArchived }
    }

    return when (sort) {
        TaskSort.PRIORITY -> filtered.sortedBy { it.priority }
        TaskSort.ESTIMATE -> filtered.sortedByDescending { it.estimateMinutes }
        TaskSort.TITLE -> filtered.sortedBy { it.title.lowercase(Locale.getDefault()) }
    }
}

