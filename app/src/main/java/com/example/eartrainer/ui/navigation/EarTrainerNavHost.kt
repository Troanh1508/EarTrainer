package com.example.eartrainer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.eartrainer.ui.backup.BackupRoute
import com.example.eartrainer.ui.backup.BackupViewModel
import com.example.eartrainer.ui.chords.ChordTrainingRoute
import com.example.eartrainer.ui.compare.ComparisonDestination
import com.example.eartrainer.ui.compare.ComparisonRequest
import com.example.eartrainer.ui.degrees.ScaleDegreeTrainingRoute
import com.example.eartrainer.ui.dictation.DictationTrainingRoute
import com.example.eartrainer.ui.home.HomeScreen
import com.example.eartrainer.ui.intervals.IntervalTrainingRoute
import com.example.eartrainer.ui.log.PracticeLogRoute
import com.example.eartrainer.ui.log.PracticeLogViewModel
import com.example.eartrainer.ui.progress.ProgressRoute
import com.example.eartrainer.ui.progress.ProgressViewModel
import com.example.eartrainer.ui.progressions.ProgressionTrainingRoute
import com.example.eartrainer.ui.rhythm.RhythmTrainingRoute
import com.example.eartrainer.ui.settings.ChordSettingsRoute
import com.example.eartrainer.ui.settings.ChordSettingsViewModel
import com.example.eartrainer.ui.settings.DictationSettingsRoute
import com.example.eartrainer.ui.settings.DictationSettingsViewModel
import com.example.eartrainer.ui.settings.IntervalSettingsRoute
import com.example.eartrainer.ui.settings.IntervalSettingsViewModel
import com.example.eartrainer.ui.settings.ProgressionSettingsRoute
import com.example.eartrainer.ui.settings.ProgressionSettingsViewModel
import com.example.eartrainer.ui.settings.RhythmSettingsRoute
import com.example.eartrainer.ui.settings.RhythmSettingsViewModel
import com.example.eartrainer.ui.settings.ScaleDegreeSettingsRoute
import com.example.eartrainer.ui.settings.ScaleDegreeSettingsViewModel

private object Routes {
    const val HOME = "home"
    const val INTERVAL_TRAINING = "interval-training"
    const val INTERVAL_SETTINGS = "interval-settings"
    const val CHORD_TRAINING = "chord-training"
    const val CHORD_SETTINGS = "chord-settings"
    const val SCALE_DEGREE_TRAINING = "scale-degree-training"
    const val SCALE_DEGREE_SETTINGS = "scale-degree-settings"
    const val PROGRESSION_TRAINING = "progression-training"
    const val PROGRESSION_SETTINGS = "progression-settings"
    const val DICTATION_TRAINING = "dictation-training"
    const val DICTATION_SETTINGS = "dictation-settings"
    const val RHYTHM_TRAINING = "rhythm-training"
    const val RHYTHM_SETTINGS = "rhythm-settings"
    const val PROGRESS = "progress"
    const val PRACTICE_LOG = "practice-log"
    const val BACKUP = "backup"
}

@Composable
fun EarTrainerNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenIntervalTraining = { navController.navigate(Routes.INTERVAL_TRAINING) },
                onOpenChordTraining = { navController.navigate(Routes.CHORD_TRAINING) },
                onOpenScaleDegreeTraining = { navController.navigate(Routes.SCALE_DEGREE_TRAINING) },
                onOpenProgressionTraining = { navController.navigate(Routes.PROGRESSION_TRAINING) },
                onOpenDictationTraining = { navController.navigate(Routes.DICTATION_TRAINING) },
                onOpenRhythmTraining = { navController.navigate(Routes.RHYTHM_TRAINING) },
                onOpenProgress = { navController.navigate(Routes.PROGRESS) },
                onOpenBackup = { navController.navigate(Routes.BACKUP) },
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
        composable(Routes.SCALE_DEGREE_TRAINING) {
            ScaleDegreeTrainingRoute(
                onOpenSettings = { navController.navigate(Routes.SCALE_DEGREE_SETTINGS) },
                onNavigateUp = { navController.navigateUp() },
            )
        }
        composable(Routes.SCALE_DEGREE_SETTINGS) {
            val viewModel: ScaleDegreeSettingsViewModel = viewModel(factory = ScaleDegreeSettingsViewModel.Factory)
            ScaleDegreeSettingsRoute(viewModel = viewModel, onNavigateUp = { navController.navigateUp() })
        }
        composable(Routes.PROGRESSION_TRAINING) {
            ProgressionTrainingRoute(
                onOpenSettings = { navController.navigate(Routes.PROGRESSION_SETTINGS) },
                onNavigateUp = { navController.navigateUp() },
            )
        }
        composable(Routes.PROGRESSION_SETTINGS) {
            val viewModel: ProgressionSettingsViewModel = viewModel(factory = ProgressionSettingsViewModel.Factory)
            ProgressionSettingsRoute(viewModel = viewModel, onNavigateUp = { navController.navigateUp() })
        }
        composable(Routes.DICTATION_TRAINING) {
            DictationTrainingRoute(
                onOpenSettings = { navController.navigate(Routes.DICTATION_SETTINGS) },
                onNavigateUp = { navController.navigateUp() },
            )
        }
        composable(Routes.DICTATION_SETTINGS) {
            val viewModel: DictationSettingsViewModel = viewModel(factory = DictationSettingsViewModel.Factory)
            DictationSettingsRoute(viewModel = viewModel, onNavigateUp = { navController.navigateUp() })
        }
        composable(Routes.RHYTHM_TRAINING) {
            RhythmTrainingRoute(
                onOpenSettings = { navController.navigate(Routes.RHYTHM_SETTINGS) },
                onNavigateUp = { navController.navigateUp() },
            )
        }
        composable(Routes.RHYTHM_SETTINGS) {
            val viewModel: RhythmSettingsViewModel = viewModel(factory = RhythmSettingsViewModel.Factory)
            RhythmSettingsRoute(viewModel = viewModel, onNavigateUp = { navController.navigateUp() })
        }
        composable(Routes.PROGRESS) {
            val viewModel: ProgressViewModel = viewModel(factory = ProgressViewModel.Factory)
            ProgressRoute(
                viewModel = viewModel,
                onCompare = { navController.navigate(it.route) },
                onOpenPracticeLog = { navController.navigate(Routes.PRACTICE_LOG) },
                onNavigateUp = { navController.navigateUp() },
            )
        }
        composable(Routes.PRACTICE_LOG) {
            val viewModel: PracticeLogViewModel = viewModel(factory = PracticeLogViewModel.Factory)
            PracticeLogRoute(viewModel = viewModel, onNavigateUp = { navController.navigateUp() })
        }
        composable(ComparisonRequest.ROUTE_PATTERN) { entry ->
            val arguments = entry.arguments
            val request = ComparisonRequest.fromRoute(
                arguments?.getString("exercise"),
                arguments?.getString("scope"),
                arguments?.getString("first"),
                arguments?.getString("second"),
            )
            if (request == null) {
                LaunchedEffect(Unit) { navController.navigateUp() }
            } else {
                ComparisonDestination(request, onNavigateUp = { navController.navigateUp() })
            }
        }
        composable(Routes.BACKUP) {
            val viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory)
            BackupRoute(viewModel = viewModel, onNavigateUp = { navController.navigateUp() })
        }
    }
}
