package com.lameck.ifocus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lameck.ifocus.data.FocusRepositoryProvider
import com.lameck.ifocus.session.AndroidSessionAlarmScheduler
import com.lameck.ifocus.session.AndroidSessionForegroundController
import com.lameck.ifocus.session.SessionStartupReconciler
import com.lameck.ifocus.ui.AppRootScreen
import com.lameck.ifocus.ui.FocusViewModel
import com.lameck.ifocus.ui.theme.IFocusTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            val repository = FocusRepositoryProvider.get(applicationContext)
            val alarmScheduler = AndroidSessionAlarmScheduler(applicationContext)
            SessionStartupReconciler(repository, alarmScheduler).reconcileAndReschedule()
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
                AppRootScreen(viewModel = focusViewModel)
            }
        }
    }
}
