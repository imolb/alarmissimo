package com.alarmissimo.ui

import android.app.AlarmManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.alarmissimo.AlarmissimoApp
import com.alarmissimo.data.AlarmRepository
import com.alarmissimo.ui.screen.AlarmEventEditorScreen
import com.alarmissimo.ui.screen.AlarmSetEditorScreen
import com.alarmissimo.ui.screen.ConfigScreen
import com.alarmissimo.ui.screen.DashboardScreen
import com.alarmissimo.ui.theme.AlarmissimoTheme
import com.alarmissimo.ui.viewmodel.AlarmEventEditorViewModel
import com.alarmissimo.ui.viewmodel.AlarmSetEditorViewModel
import com.alarmissimo.ui.viewmodel.ConfigViewModel
import com.alarmissimo.ui.viewmodel.DashboardViewModel

/** Navigation route constants. */
object Routes {
    const val DASHBOARD = "dashboard"
    const val CONFIG = "config"
    const val ALARM_SET_EDITOR = "alarm_set_editor/{alarmSetId}"
    const val ALARM_EVENT_EDITOR = "alarm_event_editor/{alarmSetId}/{alarmEventId}"

    fun alarmSetEditor(alarmSetId: Long) = "alarm_set_editor/$alarmSetId"
    fun alarmEventEditor(alarmSetId: Long, alarmEventId: Long) =
        "alarm_event_editor/$alarmSetId/$alarmEventId"
}

/**
 * Single-activity host for the Alarmissimo app.
 * Contains the Compose [NavHost] with four destinations:
 * Dashboard → Config → AlarmSetEditor → AlarmEventEditor.
 */
class MainActivity : ComponentActivity() {

    // Runtime permission launcher for POST_NOTIFICATIONS (Android 13+)
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // On Android 12/12L (API 31/32) SCHEDULE_EXACT_ALARM requires user consent.
        // USE_EXACT_ALARM (API 33+) is auto-granted, so this only matters for API 31-32.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.S ||
            Build.VERSION.SDK_INT == Build.VERSION_CODES.S_V2
        ) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
        }

        val repository = (application as AlarmissimoApp).repository
        setContent {
            AlarmissimoTheme {
                AlarmissimoNavHost(repository = repository)
            }
        }
    }
}

/**
 * Navigation host for the entire app.
 *
 * @param repository The shared alarm repository passed down to ViewModels.
 * @param navController The navigation controller.
 */
@Composable
fun AlarmissimoNavHost(
    repository: AlarmRepository,
    navController: NavHostController = rememberNavController()
) {
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {

        // --- Dashboard ---
        composable(Routes.DASHBOARD) {
            val vm: DashboardViewModel = viewModel(
                factory = viewModelFactory { initializer { DashboardViewModel(repository) } }
            )
            DashboardScreen(
                viewModel = vm,
                onNavigateToConfig = { navController.navigate(Routes.CONFIG) },
                onNavigateToAlarmEventEditor = { setId, eventId ->
                    navController.navigate(Routes.alarmEventEditor(setId, eventId))
                }
            )
        }

        // --- Config ---
        composable(Routes.CONFIG) {
            val vm: ConfigViewModel = viewModel(
                factory = viewModelFactory { initializer { ConfigViewModel(repository) } }
            )
            ConfigScreen(
                viewModel = vm,
                onNavigateToAlarmSetEditor = { setId ->
                    navController.navigate(Routes.alarmSetEditor(setId))
                },
                onNavigateUp = { navController.popBackStack() }
            )
        }

        // --- Alarm-Set Editor ---
        composable(Routes.ALARM_SET_EDITOR) { backStackEntry ->
            val alarmSetId = backStackEntry.arguments?.getString("alarmSetId")?.toLongOrNull() ?: -1L
            val vm: AlarmSetEditorViewModel = viewModel(
                key = "ase_$alarmSetId",
                factory = viewModelFactory {
                    initializer { AlarmSetEditorViewModel(alarmSetId, repository) }
                }
            )
            AlarmSetEditorScreen(
                viewModel = vm,
                onNavigateToAlarmEventEditor = { setId, eventId ->
                    navController.navigate(Routes.alarmEventEditor(setId, eventId))
                },
                onNavigateToAlarmSet = { setId ->
                    navController.navigate(Routes.alarmSetEditor(setId))
                },
                onSaved = { navController.popBackStack() }
            )
        }

        // --- Alarm-Event Editor ---
        composable(Routes.ALARM_EVENT_EDITOR) { backStackEntry ->
            val alarmSetId = backStackEntry.arguments?.getString("alarmSetId")?.toLongOrNull() ?: -1L
            val alarmEventId = backStackEntry.arguments?.getString("alarmEventId")?.toLongOrNull() ?: -1L
            val vm: AlarmEventEditorViewModel = viewModel(
                key = "aee_${alarmSetId}_$alarmEventId",
                factory = viewModelFactory {
                    initializer { AlarmEventEditorViewModel(alarmSetId, alarmEventId, repository) }
                }
            )
            AlarmEventEditorScreen(
                viewModel = vm,
                onSaved = { navController.popBackStack() }
            )
        }
    }
}
