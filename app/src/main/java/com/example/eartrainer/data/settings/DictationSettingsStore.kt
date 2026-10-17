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
import com.example.eartrainer.exercise.DictationSettings
import com.example.eartrainer.exercise.KeyChoice
import com.example.eartrainer.exercise.KeyContext
import com.example.eartrainer.music.Mode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Persists melodic dictation settings in the shared Preferences DataStore. */
class DictationSettingsStore(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<DictationSettings> = dataStore.data
        // An unreadable file should not block practice; fall back to defaults.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::decode)
        .distinctUntilChanged()

    suspend fun update(transform: (DictationSettings) -> DictationSettings) {
        dataStore.edit { preferences -> encode(transform(decode(preferences)), preferences) }
    }

    companion object {
        private val MODES = stringSetPreferencesKey("dictation.modes")
        private val LENGTH = intPreferencesKey("dictation.length")
        private val KEY_CHOICE = stringPreferencesKey("dictation.key_choice")
        private val CONTEXT = stringPreferencesKey("dictation.context")
        private val FOCUS = booleanPreferencesKey("dictation.focus_weak_spots")

        /** Ignores unknown names and falls back to defaults for anything missing or invalid. */
        internal fun decode(preferences: Preferences): DictationSettings {
            val defaults = DictationSettings()
            val modes = preferences[MODES]?.mapNotNull { name -> Mode.entries.find { it.name == name } }?.toSet() ?: defaults.modes
            val length = preferences[LENGTH]?.takeIf { it in DictationSettings.LENGTH_OPTIONS } ?: defaults.length
            val keyChoice = preferences[KEY_CHOICE]?.let { name -> KeyChoice.entries.find { it.name == name } } ?: defaults.keyChoice
            val context = preferences[CONTEXT]?.let { name -> KeyContext.entries.find { it.name == name } } ?: defaults.context
            val focus = preferences[FOCUS] ?: defaults.focusOnWeakSpots
            return runCatching { DictationSettings(modes, length, keyChoice, context, focus) }.getOrDefault(defaults)
        }

        internal fun encode(settings: DictationSettings, preferences: MutablePreferences) {
            preferences[MODES] = settings.modes.map { it.name }.toSet()
            preferences[LENGTH] = settings.length
            preferences[KEY_CHOICE] = settings.keyChoice.name
            preferences[CONTEXT] = settings.context.name
            preferences[FOCUS] = settings.focusOnWeakSpots
        }
    }
}
