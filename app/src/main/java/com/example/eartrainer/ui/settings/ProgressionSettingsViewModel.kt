package com.example.eartrainer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.eartrainer.EarTrainerApplication
import com.example.eartrainer.data.settings.ProgressionSettingsStore
import com.example.eartrainer.exercise.ChordVocabulary
import com.example.eartrainer.exercise.KeyChoice
import com.example.eartrainer.exercise.ProgressionSettings
import com.example.eartrainer.exercise.ProgressionSource
import com.example.eartrainer.music.Mode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Edits are saved immediately; there is no separate "save" step. */
class ProgressionSettingsViewModel(private val store: ProgressionSettingsStore) : ViewModel() {

    val settings: StateFlow<ProgressionSettings?> =
        store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    fun onSourceSelected(source: ProgressionSource) = update { it.copy(source = source) }

    fun onModeToggled(mode: Mode) = update { it.withModeToggled(mode) }

    fun onVocabularySelected(vocabulary: ChordVocabulary) = update { it.copy(vocabulary = vocabulary) }

    fun onLengthSelected(length: Int) {
        if (length in ProgressionSettings.LENGTH_OPTIONS) update { it.copy(length = length) }
    }

    fun onKeyChoiceSelected(keyChoice: KeyChoice) = update { it.copy(keyChoice = keyChoice) }

    fun onRestoreDefaults() = update { ProgressionSettings() }

    private fun update(transform: (ProgressionSettings) -> ProgressionSettings) {
        viewModelScope.launch { store.update(transform) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EarTrainerApplication).container
                ProgressionSettingsViewModel(container.progressionSettings)
            }
        }
    }
}
