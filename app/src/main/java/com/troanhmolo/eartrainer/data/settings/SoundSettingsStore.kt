package com.troanhmolo.eartrainer.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.troanhmolo.eartrainer.audio.Instrument
import com.troanhmolo.eartrainer.audio.SoundSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Persists the app-wide sound settings in the shared Preferences DataStore. */
class SoundSettingsStore(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<SoundSettings> = dataStore.data
        // An unreadable file should not block practice; fall back to defaults.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::decode)
        .distinctUntilChanged()

    suspend fun update(transform: (SoundSettings) -> SoundSettings) {
        dataStore.edit { preferences -> encode(transform(decode(preferences)), preferences) }
    }

    companion object {
        private val INSTRUMENT = stringPreferencesKey("sound.instrument")

        /** Ignores unknown names and falls back to defaults for anything missing. */
        internal fun decode(preferences: Preferences): SoundSettings {
            val defaults = SoundSettings()
            return SoundSettings(
                instrument = preferences[INSTRUMENT]?.let { name -> Instrument.entries.find { it.name == name } } ?: defaults.instrument,
            )
        }

        internal fun encode(settings: SoundSettings, preferences: MutablePreferences) {
            preferences[INSTRUMENT] = settings.instrument.name
        }
    }
}
