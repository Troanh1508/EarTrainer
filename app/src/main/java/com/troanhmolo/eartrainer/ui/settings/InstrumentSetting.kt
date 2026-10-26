package com.troanhmolo.eartrainer.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.troanhmolo.eartrainer.EarTrainerApplication
import com.troanhmolo.eartrainer.R
import com.troanhmolo.eartrainer.audio.Instrument
import com.troanhmolo.eartrainer.audio.SoundSettings
import com.troanhmolo.eartrainer.data.settings.SoundSettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The app-wide instrument choice, shown on every exercise's settings screen. */
class SoundSettingsViewModel(private val store: SoundSettingsStore) : ViewModel() {

    val settings: StateFlow<SoundSettings?> =
        store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    fun onInstrumentSelected(instrument: Instrument) {
        viewModelScope.launch { store.update { it.copy(instrument = instrument) } }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EarTrainerApplication).container
                SoundSettingsViewModel(container.soundSettings)
            }
        }
    }
}

/** Connects [InstrumentSetting] to the shared sound settings. */
@Composable
fun InstrumentSettingRoute() {
    val viewModel: SoundSettingsViewModel = viewModel(key = "sound-settings", factory = SoundSettingsViewModel.Factory)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    settings?.let { InstrumentSetting(it.instrument, viewModel::onInstrumentSelected) }
}

@Composable
internal fun InstrumentSetting(selected: Instrument, onSelected: (Instrument) -> Unit) {
    SettingsSection(
        title = stringResource(R.string.settings_instrument_title),
        description = stringResource(R.string.settings_instrument_description),
    ) {
        Instrument.entries.forEach { instrument ->
            SettingsChip(instrument.displayName, selected = selected == instrument, onClick = { onSelected(instrument) })
        }
    }
}
