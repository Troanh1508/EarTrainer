package com.example.eartrainer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.eartrainer.ui.chords.ChordTrainingRoute
import com.example.eartrainer.ui.home.HomeScreen
import com.example.eartrainer.ui.intervals.IntervalTrainingRoute
import com.example.eartrainer.ui.progress.ProgressRoute
import com.example.eartrainer.ui.progress.ProgressViewModel
import com.example.eartrainer.ui.settings.ChordSettingsRoute
import com.example.eartrainer.ui.settings.ChordSettingsViewModel
import com.example.eartrainer.ui.settings.IntervalSettingsRoute
import com.example.eartrainer.ui.settings.IntervalSettingsViewModel

private object Routes {
    const val HOME = "home"
    const val INTERVAL_TRAINING = "interval-training"
    const val INTERVAL_SETTINGS = "interval-settings"
    const val CHORD_TRAINING = "chord-training"
    const val CHORD_SETTINGS = "chord-settings"
    const val PROGRESS = "progress"
}

@Composable
fun EarTrainerNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenIntervalTraining = { navController.navigate(Routes.INTERVAL_TRAINING) },
                onOpenChordTraining = { navController.navigate(Routes.CHORD_TRAINING) },
                onOpenProgress = { navController.navigate(Routes.PROGRESS) },
            )
        }
        composable(Routes.INTERVAL_TRAINING) {
            IntervalTrainingRoute(
                onOpenSettings = { navController.navigate(Routes.INTERVAL_SETTINGS) },
                onNavigateUp = { navController.navigateUp() },
            )
        }
        composable(Routes.INTERVAL_SETTINGS) {
            val viewModel: IntervalSettingsViewModel = viewModel(factory = IntervalSettingsViewModel.Factory)
            IntervalSettingsRoute(viewModel = viewModel, onNavigateUp = { navController.navigateUp() })
        }
        composable(Routes.CHORD_TRAINING) {
            ChordTrainingRoute(
                onOpenSettings = { navController.navigate(Routes.CHORD_SETTINGS) },
                onNavigateUp = { navController.navigateUp() },
            )
        }
        composable(Routes.CHORD_SETTINGS) {
            val viewModel: ChordSettingsViewModel = viewModel(factory = ChordSettingsViewModel.Factory)
            ChordSettingsRoute(viewModel = viewModel, onNavigateUp = { navController.navigateUp() })
        }
        composable(Routes.PROGRESS) {
            val viewModel: ProgressViewModel = viewModel(factory = ProgressViewModel.Factory)
            ProgressRoute(viewModel = viewModel, onNavigateUp = { navController.navigateUp() })
        }
    }
}
