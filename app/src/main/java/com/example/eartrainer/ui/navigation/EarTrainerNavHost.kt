package com.example.eartrainer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.eartrainer.ui.home.HomeScreen
import com.example.eartrainer.ui.intervals.IntervalTrainingRoute
import com.example.eartrainer.ui.intervals.IntervalTrainingViewModel

private object Routes {
    const val HOME = "home"
    const val INTERVAL_TRAINING = "interval-training"
}

@Composable
fun EarTrainerNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(onOpenIntervalTraining = { navController.navigate(Routes.INTERVAL_TRAINING) })
        }
        composable(Routes.INTERVAL_TRAINING) {
            // Scoped to this destination: leaving the screen clears the ViewModel, which
            // releases its audio player.
            val viewModel: IntervalTrainingViewModel = viewModel(factory = IntervalTrainingViewModel.Factory)
            IntervalTrainingRoute(viewModel = viewModel, onNavigateUp = { navController.navigateUp() })
        }
    }
}
