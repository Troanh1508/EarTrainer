package com.example.eartrainer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.eartrainer.EarTrainerApplication
import com.example.eartrainer.data.settings.ScaleDegreeSettingsStore
import com.example.eartrainer.exercise.DegreeSet
import com.example.eartrainer.exercise.KeyChoice
import com.example.eartrainer.exercise.KeyContext
import com.example.eartrainer.exercise.ScaleDegreeSettings
import com.example.eartrainer.music.Mode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Edits are saved immediately; there is no separate "save" step. */
class ScaleDegreeSettingsViewModel(private val store: ScaleDegreeSettingsStore) : ViewModel() {

    val settings: StateFlow<ScaleDegreeSettings?> =
        store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    fun onModeToggled(mode: Mode) = update { it.withModeToggled(mode) }

    fun onDegreeSetSelected(degreeSet: DegreeSet) = update { it.copy(degreeSet = degreeSet) }

    fun onKeyChoiceSelected(keyChoice: KeyChoice) = update { it.copy(keyChoice = keyChoice) }

    fun onContextSelected(context: KeyContext) = update { it.copy(context = context) }

    fun onRestoreDefaults() = update { ScaleDegreeSettings() }

    fun onFocusOnWeakSpotsChanged(focus: Boolean) = update { it.copy(focusOnWeakSpots = focus) }

    private fun update(transform: (ScaleDegreeSettings) -> ScaleDegreeSettings) {
        viewModelScope.launch { store.update(transform) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EarTrainerApplication).container
                ScaleDegreeSettingsViewModel(container.scaleDegreeSettings)
            }
        }
    }
}
