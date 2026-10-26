package com.troanhmolo.eartrainer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.troanhmolo.eartrainer.EarTrainerApplication
import com.troanhmolo.eartrainer.data.settings.RhythmSettingsStore
import com.troanhmolo.eartrainer.exercise.RhythmSettings
import com.troanhmolo.eartrainer.exercise.RhythmTempo
import com.troanhmolo.eartrainer.exercise.RhythmVocabulary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Edits are saved immediately; there is no separate "save" step. */
class RhythmSettingsViewModel(private val store: RhythmSettingsStore) : ViewModel() {

    val settings: StateFlow<RhythmSettings?> =
        store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    fun onVocabularySelected(vocabulary: RhythmVocabulary) = update { it.copy(vocabulary = vocabulary) }

    fun onBarsSelected(bars: Int) {
        if (bars in RhythmSettings.BAR_OPTIONS) update { it.copy(bars = bars) }
    }

    fun onTempoSelected(tempo: RhythmTempo) = update { it.copy(tempo = tempo) }

    fun onRestoreDefaults() = update { RhythmSettings() }

    fun onFocusOnWeakSpotsChanged(focus: Boolean) = update { it.copy(focusOnWeakSpots = focus) }

    private fun update(transform: (RhythmSettings) -> RhythmSettings) {
        viewModelScope.launch { store.update(transform) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EarTrainerApplication).container
                RhythmSettingsViewModel(container.rhythmSettings)
            }
        }
    }
}
