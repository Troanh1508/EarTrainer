package com.example.eartrainer.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.eartrainer.exercise.DegreeSet
import com.example.eartrainer.exercise.KeyChoice
import com.example.eartrainer.exercise.KeyContext
import com.example.eartrainer.exercise.ScaleDegreeSettings
import com.example.eartrainer.music.Mode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Persists scale-degree practice settings in the shared Preferences DataStore. */
class ScaleDegreeSettingsStore(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<ScaleDegreeSettings> = dataStore.data
        // An unreadable file should not block practice; fall back to defaults.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::decode)
        .distinctUntilChanged()

    suspend fun update(transform: (ScaleDegreeSettings) -> ScaleDegreeSettings) {
        dataStore.edit { preferences -> encode(transform(decode(preferences)), preferences) }
    }

    companion object {
        private val MODES = stringSetPreferencesKey("scale_degrees.modes")
        private val DEGREE_SET = stringPreferencesKey("scale_degrees.degree_set")
        private val KEY_CHOICE = stringPreferencesKey("scale_degrees.key_choice")
        private val CONTEXT = stringPreferencesKey("scale_degrees.context")

        private inline fun <reified E : Enum<E>> Preferences.enumOr(key: Preferences.Key<String>, default: E): E =
            this[key]?.let { name -> enumValues<E>().find { it.name == name } } ?: default

        /** Ignores unknown names and falls back to defaults for anything missing or invalid. */
        internal fun decode(preferences: Preferences): ScaleDegreeSettings {
            val defaults = ScaleDegreeSettings()
            val modes = preferences[MODES]?.mapNotNull { name -> Mode.entries.find { it.name == name } }?.toSet() ?: defaults.modes
            return runCatching {
                ScaleDegreeSettings(
                    modes = modes,
                    degreeSet = preferences.enumOr(DEGREE_SET, defaults.degreeSet),
                    keyChoice = preferences.enumOr(KEY_CHOICE, defaults.keyChoice),
                    context = preferences.enumOr(CONTEXT, defaults.context),
                )
            }.getOrDefault(defaults)
        }

        internal fun encode(settings: ScaleDegreeSettings, preferences: MutablePreferences) {
            preferences[MODES] = settings.modes.map { it.name }.toSet()
            preferences[DEGREE_SET] = settings.degreeSet.name
            preferences[KEY_CHOICE] = settings.keyChoice.name
            preferences[CONTEXT] = settings.context.name
        }
    }
}
