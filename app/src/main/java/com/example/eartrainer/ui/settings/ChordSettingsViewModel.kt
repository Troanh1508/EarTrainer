package com.example.eartrainer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.eartrainer.EarTrainerApplication
import com.example.eartrainer.data.settings.ChordSettingsStore
import com.example.eartrainer.exercise.ChordExerciseSettings
import com.example.eartrainer.exercise.ChordStyle
import com.example.eartrainer.exercise.ChordTarget
import com.example.eartrainer.music.ChordQuality
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Edits are saved immediately; there is no separate "save" step. */
class ChordSettingsViewModel(private val store: ChordSettingsStore) : ViewModel() {

    val settings: StateFlow<ChordExerciseSettings?> =
        store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    fun onTargetSelected(target: ChordTarget) = update { it.copy(target = target) }

    fun onQualityToggled(quality: ChordQuality) = update { it.withQualityToggled(quality) }

    fun onSelectAllQualities() = update { it.copy(qualities = ChordQuality.entries) }

    fun onIncludeInversionsChanged(include: Boolean) = update { it.copy(includeInversions = include) }

    fun onStyleToggled(style: ChordStyle) = update { it.withStyleToggled(style) }

    fun onRestoreDefaults() = update { ChordExerciseSettings() }

    fun onFocusOnWeakSpotsChanged(focus: Boolean) = update { it.copy(focusOnWeakSpots = focus) }

    private fun update(transform: (ChordExerciseSettings) -> ChordExerciseSettings) {
        viewModelScope.launch { store.update(transform) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EarTrainerApplication).container
                ChordSettingsViewModel(container.chordSettings)
            }
        }
    }
}

