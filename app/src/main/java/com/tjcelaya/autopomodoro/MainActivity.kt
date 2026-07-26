package com.tjcelaya.autopomodoro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tjcelaya.autopomodoro.ui.ScheduleViewModel
import com.tjcelaya.autopomodoro.ui.screens.ScheduleEditScreen
import com.tjcelaya.autopomodoro.ui.screens.ScheduleListScreen
import com.tjcelaya.autopomodoro.ui.theme.AutopomodoroTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    /**
     * Pending schedule ID to navigate to, set from intent extras.
     * Compose observes this and navigates once the NavController is ready.
     * Reset to -1 after consumption to avoid re-navigation on recomposition.
     */
    private var pendingScheduleId by mutableIntStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        pendingScheduleId = extractScheduleId(intent)

        setContent {
            AutopomodoroTheme {
                val navController = rememberNavController()
                val viewModel: ScheduleViewModel = viewModel()

                // React to pendingScheduleId once the nav graph is ready
                val id = pendingScheduleId
                LaunchedEffect(id) {
                    if (id > 0) {
                        navController.navigate("edit/$id") {
                            launchSingleTop = true
                        }
                        pendingScheduleId = -1
                    }
                }

                NavHost(navController = navController, startDestination = "list") {
                    composable("list") {
                        ScheduleListScreen(
                            viewModel = viewModel,
                            onAddClick = { navController.navigate("edit/0") },
                            onScheduleClick = { id -> navController.navigate("edit/$id") },
                        )
                    }
                    composable(
                        route = "edit/{scheduleId}",
                        arguments = listOf(navArgument("scheduleId") { type = NavType.IntType }),
                    ) { backStackEntry ->
                        val scheduleId = backStackEntry.arguments?.getInt("scheduleId")
                        ScheduleEditScreen(
                            viewModel = viewModel,
                            scheduleId = scheduleId?.takeIf { it != 0 },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingScheduleId = extractScheduleId(intent)
    }

    private fun extractScheduleId(intent: Intent?): Int =
        intent?.getIntExtra(EXTRA_SCHEDULE_ID, -1) ?: -1

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    companion object {
        const val EXTRA_SCHEDULE_ID = "schedule_id"
    }
}
