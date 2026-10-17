package com.example.eartrainer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.eartrainer.EarTrainerApplication
import com.example.eartrainer.data.settings.IntervalSettingsStore
import com.example.eartrainer.exercise.IntervalExerciseSettings
import com.example.eartrainer.exercise.IntervalStyle
import com.example.eartrainer.music.Interval
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Edits are saved immediately; there is no separate "save" step. */
class IntervalSettingsViewModel(private val store: IntervalSettingsStore) : ViewModel() {

    val settings: StateFlow<IntervalExerciseSettings?> =
        store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    fun onIntervalToggled(interval: Interval) = update { it.withIntervalToggled(interval) }

    fun onSelectAllIntervals() = update { it.copy(intervals = Interval.entries) }

    fun onStyleToggled(style: IntervalStyle) = update { it.withStyleToggled(style) }

    fun onChoiceCountSelected(count: Int) {
        if (count in IntervalExerciseSettings.CHOICE_COUNT_OPTIONS) update { it.copy(choiceCount = count) }
    }

    fun onRestoreDefaults() = update { IntervalExerciseSettings() }

    private fun update(transform: (IntervalExerciseSettings) -> IntervalExerciseSettings) {
        viewModelScope.launch { store.update(transform) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EarTrainerApplication).container
                IntervalSettingsViewModel(container.intervalSettings)
            }
        }
    }
}
