package com.troanhmolo.eartrainer.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.troanhmolo.eartrainer.EarTrainerApplication
import com.troanhmolo.eartrainer.data.history.PracticeHistory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.TimeZone

class PracticeLogViewModel(
    history: PracticeHistory,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeZone: () -> TimeZone = TimeZone::getDefault,
) : ViewModel() {

    val uiState: StateFlow<PracticeLogUiState?> =
        combine(history.answersPerDay(), history.recentSessions(SESSION_LIMIT)) { days, sessions ->
            PracticeLogUiState.from(days, sessions, today = localEpochDay(clock(), timeZone()))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    companion object {
        const val SESSION_LIMIT = 20

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EarTrainerApplication).container
                PracticeLogViewModel(container.practiceHistory)
            }
        }
    }
}
