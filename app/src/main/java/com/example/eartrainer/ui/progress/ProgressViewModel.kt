package com.example.eartrainer.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.eartrainer.EarTrainerApplication
import com.example.eartrainer.data.history.IntervalAccuracy
import com.example.eartrainer.data.history.PracticeHistory
import com.example.eartrainer.data.history.SessionSummary
import com.example.eartrainer.exercise.IntervalStyle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class ProgressUiState(
    /** Null shows every style. */
    val styleFilter: IntervalStyle?,
    val accuracy: List<IntervalAccuracy>,
    val recentSessions: List<SessionSummary>,
) {
    val totalAttempts: Int get() = accuracy.sumOf { it.attempts }
    val totalCorrect: Int get() = accuracy.sumOf { it.correct }
    val overallAccuracy: Float? get() = if (totalAttempts == 0) null else totalCorrect.toFloat() / totalAttempts

    /** Whether anything has ever been recorded, regardless of the current filter. */
    val hasHistory: Boolean get() = recentSessions.isNotEmpty()
}

class ProgressViewModel(history: PracticeHistory) : ViewModel() {

    private val styleFilter = MutableStateFlow<IntervalStyle?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ProgressUiState?> = styleFilter
        .flatMapLatest { style ->
            combine(history.intervalAccuracy(style), history.recentSessions(RECENT_SESSION_LIMIT)) { accuracy, sessions ->
                ProgressUiState(style, accuracy, sessions)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    fun onStyleFilterSelected(style: IntervalStyle?) {
        styleFilter.value = style
    }

    companion object {
        const val RECENT_SESSION_LIMIT = 10

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EarTrainerApplication).container
                ProgressViewModel(container.practiceHistory)
            }
        }
    }
}
