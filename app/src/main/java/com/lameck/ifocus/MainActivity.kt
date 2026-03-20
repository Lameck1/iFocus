package com.lameck.ifocus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lameck.ifocus.data.FocusRepositoryProvider
import com.lameck.ifocus.session.AndroidSessionAlarmScheduler
import com.lameck.ifocus.session.AndroidSessionForegroundController
import com.lameck.ifocus.session.SessionControlCoordinator
import com.lameck.ifocus.session.SessionStartupReconciler
import com.lameck.ifocus.ui.AppRootScreen
import com.lameck.ifocus.ui.FocusViewModel
import com.lameck.ifocus.ui.theme.IFocusTheme
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var launchDestination by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            val repository = FocusRepositoryProvider.get(applicationContext)
            val alarmScheduler = AndroidSessionAlarmScheduler(applicationContext)
            SessionStartupReconciler(repository, alarmScheduler).reconcileAndReschedule()
            launchDestination = handleLaunchIntent(intent)
        }

        setContent {
            val repository = remember { FocusRepositoryProvider.get(applicationContext) }
            val alarmScheduler = remember { AndroidSessionAlarmScheduler(applicationContext) }
            val foregroundController = remember { AndroidSessionForegroundController(applicationContext) }
            val factory = remember(repository, alarmScheduler, foregroundController) {
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return FocusViewModel(
                            repository = repository,
                            sessionAlarmScheduler = alarmScheduler,
                            sessionForegroundController = foregroundController
                        ) as T
                    }
                }
            }
            val focusViewModel: FocusViewModel = viewModel(factory = factory)
            val professionalState by focusViewModel.professionalState.collectAsStateWithLifecycle()
            val darkTheme = when (professionalState.settings.themePreference) {
                com.lameck.ifocus.ui.ThemePreference.SYSTEM -> isSystemInDarkTheme()
                com.lameck.ifocus.ui.ThemePreference.DARK -> true
                com.lameck.ifocus.ui.ThemePreference.LIGHT -> false
            }

            IFocusTheme(darkTheme = darkTheme) {
                AppRootScreen(viewModel = focusViewModel, launchDestination = launchDestination)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch {
            launchDestination = handleLaunchIntent(intent)
        }
    }

    private suspend fun handleLaunchIntent(incomingIntent: Intent?): String? {
        val action = incomingIntent?.action
        val data = incomingIntent?.data
        if (action == null && data == null) return null

        val repository = FocusRepositoryProvider.get(applicationContext)
        val alarmScheduler = AndroidSessionAlarmScheduler(applicationContext)
        val foregroundController = AndroidSessionForegroundController(applicationContext)
        val coordinator = SessionControlCoordinator(
            repository = repository,
            alarmScheduler = alarmScheduler,
            foregroundController = foregroundController
        )

        when (action) {
            ACTION_SHORTCUT_START_FOCUS -> {
                coordinator.startFocusSession()
                return "HOME"
            }
            ACTION_SHORTCUT_START_DEEP_WORK -> {
                coordinator.startDeepWorkSession()
                return "HOME"
            }
            ACTION_SHORTCUT_RESUME_FOCUS -> {
                coordinator.startFocusSession()
                return "HOME"
            }
            ACTION_SHORTCUT_OPEN_REPORTS -> return "REPORTS"
        }

        if (action == Intent.ACTION_VIEW && data?.scheme == "focus") {
            return when (data.host?.lowercase(Locale.US)) {
                "start" -> {
                    val preset = data.getQueryParameter("preset")
                    when (preset) {
                        "50" -> coordinator.startDeepWorkSession()
                        else -> coordinator.startFocusSession()
                    }
                    "HOME"
                }
                "resume" -> {
                    coordinator.startFocusSession()
                    "HOME"
                }
                "reports" -> "REPORTS"
                "settings" -> "SETTINGS"
                else -> null
            }
        }

        return null
    }

    companion object {
        private const val ACTION_SHORTCUT_START_FOCUS = "com.lameck.ifocus.action.SHORTCUT_START_FOCUS"
        private const val ACTION_SHORTCUT_START_DEEP_WORK = "com.lameck.ifocus.action.SHORTCUT_START_DEEP_WORK"
        private const val ACTION_SHORTCUT_RESUME_FOCUS = "com.lameck.ifocus.action.SHORTCUT_RESUME_FOCUS"
        private const val ACTION_SHORTCUT_OPEN_REPORTS = "com.lameck.ifocus.action.SHORTCUT_OPEN_REPORTS"
    }
}
