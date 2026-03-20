package com.lameck.ifocus.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import com.lameck.ifocus.R
import com.lameck.ifocus.reports.breakdownByTask
import com.lameck.ifocus.reports.breakdownByProject
import com.lameck.ifocus.reports.bestStreakDays
import com.lameck.ifocus.reports.computeSummary
import com.lameck.ifocus.reports.currentStreakDays
import com.lameck.ifocus.reports.recordsForCurrentWeek
import com.lameck.ifocus.reports.recordsForToday

private enum class AppTab {
    HOME,
    REPORTS,
    HISTORY,
    SETTINGS
}

private enum class ReportTab {
    TODAY,
    WEEK,
    BY_TASK,
    BY_PROJECT
}

@Composable
fun AppRootScreen(viewModel: FocusViewModel) {
    var selectedTab by remember { mutableStateOf(AppTab.HOME) }
    val professionalState by viewModel.professionalState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val csvExportSuccess = stringResource(R.string.csv_export_success)
    val csvExportFailed = stringResource(R.string.csv_export_failed)
    var onboardingPage by remember { mutableStateOf(0) }
    var showOnboarding by remember(professionalState.settings.hasCompletedOnboarding) {
        mutableStateOf(!professionalState.settings.hasCompletedOnboarding)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == AppTab.HOME,
                    onClick = { selectedTab = AppTab.HOME },
                    icon = { androidx.compose.material3.Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_home)) }
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.REPORTS,
                    onClick = { selectedTab = AppTab.REPORTS },
                    icon = { androidx.compose.material3.Icon(Icons.Default.BarChart, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_reports)) }
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.HISTORY,
                    onClick = { selectedTab = AppTab.HISTORY },
                    icon = { androidx.compose.material3.Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_history)) }
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.SETTINGS,
                    onClick = { selectedTab = AppTab.SETTINGS },
                    icon = { androidx.compose.material3.Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_settings)) }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            AppTab.HOME -> FocusScreen(viewModel = viewModel)
            AppTab.REPORTS -> ReportsScreen(
                state = professionalState,
                onExportCsv = {
                    scope.launch {
                        val result = exportCsvToAppStorage(context, viewModel.buildSessionHistoryCsv())
                        val message = if (result.isSuccess) {
                            csvExportSuccess.format(result.getOrNull()?.name ?: "sessions.csv")
                        } else {
                            csvExportFailed
                        }
                        snackbarHostState.showSnackbar(message)
                    }
                },
                modifier = Modifier.padding(innerPadding)
            )
            AppTab.HISTORY -> SessionHistoryScreen(
                state = professionalState,
                modifier = Modifier.padding(innerPadding)
            )
            AppTab.SETTINGS -> SettingsPlaceholder(
                settings = professionalState.settings,
                onThemeChanged = viewModel::setThemePreference,
                onAutoStartBreakChanged = viewModel::setAutoStartBreak,
                onAutoStartFocusChanged = viewModel::setAutoStartFocus,
                onDailyGoalChanged = viewModel::setDailyGoalMinutes,
                onCalendarSafePlanningChanged = viewModel::setCalendarSafePlanningEnabled,
                onOpenNotificationHelp = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                },
                onOpenExactAlarmHelp = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }

    LaunchedEffect(professionalState.settings.hasCompletedOnboarding) {
        if (professionalState.settings.hasCompletedOnboarding) {
            showOnboarding = false
        }
    }

    if (showOnboarding) {
        OnboardingDialog(
            page = onboardingPage,
            onBack = { onboardingPage = (onboardingPage - 1).coerceAtLeast(0) },
            onNext = {
                if (onboardingPage >= 2) {
                    viewModel.completeOnboarding()
                    showOnboarding = false
                } else {
                    onboardingPage += 1
                }
            }
        )
    }
}

@Composable
private fun ReportsScreen(
    state: ProfessionalUiState,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier
) {
    var reportTab by remember { mutableStateOf(ReportTab.TODAY) }
    val todayRecords = remember(state.sessionHistory) { recordsForToday(state.sessionHistory) }
    val weekRecords = remember(state.sessionHistory) { recordsForCurrentWeek(state.sessionHistory) }

    val summary = when (reportTab) {
        ReportTab.TODAY -> computeSummary(todayRecords)
        ReportTab.WEEK -> computeSummary(weekRecords)
        ReportTab.BY_TASK -> computeSummary(weekRecords)
        ReportTab.BY_PROJECT -> computeSummary(weekRecords)
    }
    val byTask = remember(weekRecords) { breakdownByTask(weekRecords).take(5) }
    val byProject = remember(weekRecords) { breakdownByProject(weekRecords).take(5) }
    val currentStreak = remember(state.sessionHistory, state.settings.dailyGoalMinutes) {
        currentStreakDays(state.sessionHistory, state.settings.dailyGoalMinutes)
    }
    val bestStreak = remember(state.sessionHistory, state.settings.dailyGoalMinutes) {
        bestStreakDays(state.sessionHistory, state.settings.dailyGoalMinutes)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = stringResource(R.string.reports_title), style = MaterialTheme.typography.headlineSmall)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ReportTab.entries.forEachIndexed { index, tab ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ReportTab.entries.size),
                    selected = reportTab == tab,
                    onClick = { reportTab = tab },
                    label = {
                        val labelRes = when (tab) {
                            ReportTab.TODAY -> R.string.report_tab_today
                            ReportTab.WEEK -> R.string.report_tab_week
                            ReportTab.BY_TASK -> R.string.report_tab_by_task
                            ReportTab.BY_PROJECT -> R.string.report_tab_by_project
                        }
                        Text(stringResource(labelRes))
                    }
                )
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.reports_total_focus_minutes, summary.totalMinutes))
                Text(stringResource(R.string.reports_completed_sessions, summary.completedSessions))
                Text(stringResource(R.string.reports_interrupted_sessions, summary.interruptedSessions))
                Text(stringResource(R.string.reports_avg_uninterrupted_minutes, summary.averageUninterruptedFocusMinutes))
                Text(stringResource(R.string.reports_streak_value, currentStreak, bestStreak))
                if (reportTab == ReportTab.BY_TASK) {
                    if (byTask.isEmpty()) {
                        Text(stringResource(R.string.reports_by_task_empty))
                    } else {
                        byTask.forEach { (taskTitle, minutes) ->
                            Text(stringResource(R.string.reports_by_task_item, taskTitle, minutes))
                        }
                    }
                }
                if (reportTab == ReportTab.BY_PROJECT) {
                    if (byProject.isEmpty()) {
                        Text(stringResource(R.string.reports_by_project_empty))
                    } else {
                        byProject.forEach { (projectName, minutes) ->
                            Text(stringResource(R.string.reports_by_project_item, projectName, minutes))
                        }
                    }
                }
                Button(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.export_csv))
                }
            }
        }
    }
}

@Composable
private fun SessionHistoryScreen(state: ProfessionalUiState, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(text = stringResource(R.string.history_title), style = MaterialTheme.typography.headlineSmall)
        }
        if (state.sessionHistory.isEmpty()) {
            item {
                Text(text = stringResource(R.string.history_empty))
            }
        } else {
            items(state.sessionHistory.reversed()) { record ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(record.taskTitle, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.reports_total_focus_minutes, record.focusedMinutes))
                        Text(formatTimestamp(record.createdAtEpochMs))
                        Text(record.outcome.name)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPlaceholder(
    settings: AppSettings,
    onThemeChanged: (ThemePreference) -> Unit,
    onAutoStartBreakChanged: (Boolean) -> Unit,
    onAutoStartFocusChanged: (Boolean) -> Unit,
    onDailyGoalChanged: (Int) -> Unit,
    onCalendarSafePlanningChanged: (Boolean) -> Unit,
    onOpenNotificationHelp: () -> Unit,
    onOpenExactAlarmHelp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_theme_title), style = MaterialTheme.typography.titleMedium)
                RowOfThemeButtons(current = settings.themePreference, onThemeChanged = onThemeChanged)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingSwitchRow(
                    title = stringResource(R.string.settings_auto_start_break),
                    checked = settings.autoStartBreak,
                    onCheckedChange = onAutoStartBreakChanged
                )
                SettingSwitchRow(
                    title = stringResource(R.string.settings_auto_start_focus),
                    checked = settings.autoStartFocus,
                    onCheckedChange = onAutoStartFocusChanged
                )
                SettingStepRow(
                    title = stringResource(R.string.settings_daily_goal_minutes),
                    value = settings.dailyGoalMinutes,
                    onDecrement = { onDailyGoalChanged(settings.dailyGoalMinutes - 25) },
                    onIncrement = { onDailyGoalChanged(settings.dailyGoalMinutes + 25) }
                )
                SettingSwitchRow(
                    title = stringResource(R.string.settings_calendar_safe_planning),
                    checked = settings.calendarSafePlanningEnabled,
                    onCheckedChange = onCalendarSafePlanningChanged
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_help_title), style = MaterialTheme.typography.titleMedium)
                Button(onClick = onOpenNotificationHelp, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_help_notifications))
                }
                Button(onClick = onOpenExactAlarmHelp, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_help_exact_alarm))
                }
            }
        }
    }
}

@Composable
private fun OnboardingDialog(
    page: Int,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val title = when (page) {
        0 -> stringResource(R.string.onboarding_title_1)
        1 -> stringResource(R.string.onboarding_title_2)
        else -> stringResource(R.string.onboarding_title_3)
    }
    val body = when (page) {
        0 -> stringResource(R.string.onboarding_body_1)
        1 -> stringResource(R.string.onboarding_body_2)
        else -> stringResource(R.string.onboarding_body_3)
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = { Text(body) },
        dismissButton = {
            if (page > 0) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.onboarding_back)) }
            }
        },
        confirmButton = {
            TextButton(onClick = onNext) {
                Text(if (page >= 2) stringResource(R.string.onboarding_done) else stringResource(R.string.onboarding_next))
            }
        }
    )
}

@Composable
private fun SettingStepRow(
    title: String,
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(title)
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDecrement) { Text("-") }
            Text("${value}m")
            TextButton(onClick = onIncrement) { Text("+") }
        }
    }
}

@Composable
private fun RowOfThemeButtons(
    current: ThemePreference,
    onThemeChanged: (ThemePreference) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ThemePreference.entries.forEach { option ->
            TextButton(onClick = { onThemeChanged(option) }) {
                val label = when (option) {
                    ThemePreference.SYSTEM -> stringResource(R.string.settings_theme_system)
                    ThemePreference.LIGHT -> stringResource(R.string.settings_theme_light)
                    ThemePreference.DARK -> stringResource(R.string.settings_theme_dark)
                }
                val selectedPrefix = if (option == current) "* " else ""
                Text("$selectedPrefix$label")
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(title)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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

private fun formatTimestamp(epochMs: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
}


