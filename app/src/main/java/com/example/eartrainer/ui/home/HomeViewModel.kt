package com.example.eartrainer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.eartrainer.EarTrainerApplication
import com.example.eartrainer.data.history.PracticeHistory
import com.example.eartrainer.data.settings.GoalSettings
import com.example.eartrainer.data.settings.GoalSettingsStore
import com.example.eartrainer.exercise.ExerciseKind
import com.example.eartrainer.ui.log.localEpochDay
import com.example.eartrainer.ui.progress.AccuracyItem
import com.example.eartrainer.ui.progress.accuracyFor
import com.example.eartrainer.ui.progress.everyAnswerFilters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.TimeZone

class HomeViewModel(
    history: PracticeHistory,
    private val goalSettings: GoalSettingsStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeZone: () -> TimeZone = TimeZone::getDefault,
) : ViewModel() {

    /** Every exercise's items over all its answers, from the same queries as the Progress screen. */
    private val accuracy: Flow<Map<ExerciseKind, List<AccuracyItem>>> = combine(
        ExerciseKind.entries.flatMap { exercise ->
            exercise.everyAnswerFilters.map { filter -> history.accuracyFor(filter).map { items -> exercise to items } }
        },
    ) { parts -> parts.groupBy({ it.first }, { it.second }).mapValues { (_, lists) -> lists.flatten() } }

    val uiState: StateFlow<HomeUiState?> =
        combine(
            history.answersPerDay(),
            history.finishedSessionsPerDay(),
            history.recentSessions(limit = 1),
            accuracy,
            goalSettings.settings,
        ) { days, finished, sessions, items, goal ->
            HomeUiState.from(
                days,
                finished,
                today = localEpochDay(clock(), timeZone()),
                dailyGoal = goal.dailySessions,
                lastExercise = sessions.firstOrNull()?.exercise,
                accuracy = items,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    fun onDailyGoalSelected(sessions: Int) {
        if (sessions !in GoalSettings.DAILY_SESSION_OPTIONS) return
        viewModelScope.launch { goalSettings.update { it.copy(dailySessions = sessions) } }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EarTrainerApplication).container
                HomeViewModel(container.practiceHistory, container.goalSettings)
            }
        }
    }
}
