package com.example.eartrainer.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.eartrainer.exercise.RhythmSettings
import com.example.eartrainer.exercise.RhythmTempo
import com.example.eartrainer.exercise.RhythmVocabulary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Persists rhythm practice settings in the shared Preferences DataStore. */
class RhythmSettingsStore(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<RhythmSettings> = dataStore.data
        // An unreadable file should not block practice; fall back to defaults.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::decode)
        .distinctUntilChanged()

    suspend fun update(transform: (RhythmSettings) -> RhythmSettings) {
        dataStore.edit { preferences -> encode(transform(decode(preferences)), preferences) }
    }

    companion object {
        private val VOCABULARY = stringPreferencesKey("rhythm.vocabulary")
        private val BARS = intPreferencesKey("rhythm.bars")
        private val TEMPO = stringPreferencesKey("rhythm.tempo")
        private val FOCUS = booleanPreferencesKey("rhythm.focus_weak_spots")

        /** Ignores unknown names and falls back to defaults for anything missing or invalid. */
        internal fun decode(preferences: Preferences): RhythmSettings {
            val defaults = RhythmSettings()
            return RhythmSettings(
                vocabulary = preferences[VOCABULARY]?.let { name -> RhythmVocabulary.entries.find { it.name == name } } ?: defaults.vocabulary,
                bars = preferences[BARS]?.takeIf { it in RhythmSettings.BAR_OPTIONS } ?: defaults.bars,
                tempo = preferences[TEMPO]?.let { name -> RhythmTempo.entries.find { it.name == name } } ?: defaults.tempo,
                focusOnWeakSpots = preferences[FOCUS] ?: defaults.focusOnWeakSpots,
            )
        }

        internal fun encode(settings: RhythmSettings, preferences: MutablePreferences) {
            preferences[VOCABULARY] = settings.vocabulary.name
            preferences[BARS] = settings.bars
            preferences[TEMPO] = settings.tempo.name
            preferences[FOCUS] = settings.focusOnWeakSpots
        }
    }
}
