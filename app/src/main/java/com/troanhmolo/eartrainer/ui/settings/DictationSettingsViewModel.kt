package com.troanhmolo.eartrainer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.troanhmolo.eartrainer.EarTrainerApplication
import com.troanhmolo.eartrainer.data.settings.DictationSettingsStore
import com.troanhmolo.eartrainer.exercise.DictationSettings
import com.troanhmolo.eartrainer.exercise.KeyChoice
import com.troanhmolo.eartrainer.exercise.KeyContext
import com.troanhmolo.eartrainer.music.Mode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Edits are saved immediately; there is no separate "save" step. */
class DictationSettingsViewModel(private val store: DictationSettingsStore) : ViewModel() {

    val settings: StateFlow<DictationSettings?> =
        store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    fun onModeToggled(mode: Mode) = update { it.withModeToggled(mode) }

    fun onLengthSelected(length: Int) {
        if (length in DictationSettings.LENGTH_OPTIONS) update { it.copy(length = length) }
    }

    fun onKeyChoiceSelected(keyChoice: KeyChoice) = update { it.copy(keyChoice = keyChoice) }

    fun onContextSelected(context: KeyContext) = update { it.copy(context = context) }

    fun onRestoreDefaults() = update { DictationSettings() }

    fun onFocusOnWeakSpotsChanged(focus: Boolean) = update { it.copy(focusOnWeakSpots = focus) }

    private fun update(transform: (DictationSettings) -> DictationSettings) {
        viewModelScope.launch { store.update(transform) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EarTrainerApplication).container
                DictationSettingsViewModel(container.dictationSettings)
            }
        }
    }
}
