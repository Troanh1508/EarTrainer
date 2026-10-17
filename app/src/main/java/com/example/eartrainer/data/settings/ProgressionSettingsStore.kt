package com.example.eartrainer.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.eartrainer.exercise.ChordVocabulary
import com.example.eartrainer.exercise.KeyChoice
import com.example.eartrainer.exercise.ProgressionSettings
import com.example.eartrainer.exercise.ProgressionSource
import com.example.eartrainer.music.Mode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Persists chord progression practice settings in the shared Preferences DataStore. */
class ProgressionSettingsStore(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<ProgressionSettings> = dataStore.data
        // An unreadable file should not block practice; fall back to defaults.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::decode)
        .distinctUntilChanged()

    suspend fun update(transform: (ProgressionSettings) -> ProgressionSettings) {
        dataStore.edit { preferences -> encode(transform(decode(preferences)), preferences) }
    }

    companion object {
        private val MODES = stringSetPreferencesKey("progressions.modes")
        private val VOCABULARY = stringPreferencesKey("progressions.vocabulary")
        private val LENGTH = intPreferencesKey("progressions.length")
        private val KEY_CHOICE = stringPreferencesKey("progressions.key_choice")
        private val SOURCE = stringPreferencesKey("progressions.source")
        private val FOCUS = booleanPreferencesKey("progressions.focus_weak_spots")

        /** Ignores unknown names and falls back to defaults for anything missing or invalid. */
        internal fun decode(preferences: Preferences): ProgressionSettings {
            val defaults = ProgressionSettings()
            val modes = preferences[MODES]?.mapNotNull { name -> Mode.entries.find { it.name == name } }?.toSet() ?: defaults.modes
            val vocabulary = preferences[VOCABULARY]?.let { name -> ChordVocabulary.entries.find { it.name == name } } ?: defaults.vocabulary
            val keyChoice = preferences[KEY_CHOICE]?.let { name -> KeyChoice.entries.find { it.name == name } } ?: defaults.keyChoice
            val length = preferences[LENGTH]?.takeIf { it in ProgressionSettings.LENGTH_OPTIONS } ?: defaults.length
            val source = preferences[SOURCE]?.let { name -> ProgressionSource.entries.find { it.name == name } } ?: defaults.source
            val focus = preferences[FOCUS] ?: defaults.focusOnWeakSpots
            return runCatching { ProgressionSettings(modes, vocabulary, length, keyChoice, source, focus) }.getOrDefault(defaults)
        }

        internal fun encode(settings: ProgressionSettings, preferences: MutablePreferences) {
            preferences[MODES] = settings.modes.map { it.name }.toSet()
            preferences[VOCABULARY] = settings.vocabulary.name
            preferences[LENGTH] = settings.length
            preferences[KEY_CHOICE] = settings.keyChoice.name
            preferences[SOURCE] = settings.source.name
            preferences[FOCUS] = settings.focusOnWeakSpots
        }
    }
}
