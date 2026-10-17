package com.example.eartrainer.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.eartrainer.exercise.ChordExerciseSettings
import com.example.eartrainer.exercise.ChordStyle
import com.example.eartrainer.exercise.ChordTarget
import com.example.eartrainer.music.ChordQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Persists chord practice settings in the same Preferences DataStore as the interval settings. */
class ChordSettingsStore(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<ChordExerciseSettings> = dataStore.data
        // An unreadable file should not block practice; fall back to defaults.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::decode)
        .distinctUntilChanged()

    suspend fun update(transform: (ChordExerciseSettings) -> ChordExerciseSettings) {
        dataStore.edit { preferences -> encode(transform(decode(preferences)), preferences) }
    }

    companion object {
        private val QUALITIES = stringSetPreferencesKey("chord_training.qualities")
        private val STYLES = stringSetPreferencesKey("chord_training.styles")
        private val TARGET = stringPreferencesKey("chord_training.target")
        private val INCLUDE_INVERSIONS = booleanPreferencesKey("chord_training.include_inversions")
        private val FOCUS = booleanPreferencesKey("chord_training.focus_weak_spots")

        /** Ignores unknown names and falls back to defaults for anything missing or invalid. */
        internal fun decode(preferences: Preferences): ChordExerciseSettings {
            val defaults = ChordExerciseSettings()
            val qualities = preferences[QUALITIES]
                ?.let { names -> ChordQuality.entries.filter { it.name in names } }
                ?: defaults.qualities
            val styles = preferences[STYLES]
                ?.mapNotNull { name -> ChordStyle.entries.find { it.name == name } }
                ?.toSet()
                ?: defaults.styles
            val target = preferences[TARGET]?.let { name -> ChordTarget.entries.find { it.name == name } } ?: defaults.target
            val includeInversions = preferences[INCLUDE_INVERSIONS] ?: defaults.includeInversions
            return runCatching {
                defaults.copy(
                    qualities = qualities,
                    styles = styles,
                    target = target,
                    includeInversions = includeInversions,
                    focusOnWeakSpots = preferences[FOCUS] ?: defaults.focusOnWeakSpots,
                )
            }.getOrDefault(defaults)
        }

        internal fun encode(settings: ChordExerciseSettings, preferences: MutablePreferences) {
            preferences[QUALITIES] = settings.qualities.map { it.name }.toSet()
            preferences[STYLES] = settings.styles.map { it.name }.toSet()
            preferences[TARGET] = settings.target.name
            preferences[INCLUDE_INVERSIONS] = settings.includeInversions
            preferences[FOCUS] = settings.focusOnWeakSpots
        }
    }
}
