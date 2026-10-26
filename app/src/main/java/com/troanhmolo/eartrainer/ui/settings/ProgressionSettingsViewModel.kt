package com.troanhmolo.eartrainer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.troanhmolo.eartrainer.EarTrainerApplication
import com.troanhmolo.eartrainer.data.settings.ProgressionSettingsStore
import com.troanhmolo.eartrainer.exercise.ChordVocabulary
import com.troanhmolo.eartrainer.exercise.KeyChoice
import com.troanhmolo.eartrainer.exercise.ProgressionSettings
import com.troanhmolo.eartrainer.exercise.ProgressionSource
import com.troanhmolo.eartrainer.music.Mode
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

    fun onFocusOnWeakSpotsChanged(focus: Boolean) = update { it.copy(focusOnWeakSpots = focus) }

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
