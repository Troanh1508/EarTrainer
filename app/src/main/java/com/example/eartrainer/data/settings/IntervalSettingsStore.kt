package com.example.eartrainer.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.eartrainer.exercise.IntervalExerciseSettings
import com.example.eartrainer.exercise.IntervalStyle
import com.example.eartrainer.music.Interval
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Persists interval practice settings locally with Preferences DataStore. */
class IntervalSettingsStore(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<IntervalExerciseSettings> = dataStore.data
        // An unreadable file should not block practice; fall back to defaults.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::decode)
        .distinctUntilChanged()

    suspend fun update(transform: (IntervalExerciseSettings) -> IntervalExerciseSettings) {
        dataStore.edit { preferences -> encode(transform(decode(preferences)), preferences) }
    }

    companion object {
        private val INTERVALS = stringSetPreferencesKey("interval_training.intervals")
        private val STYLES = stringSetPreferencesKey("interval_training.styles")
        private val CHOICE_COUNT = intPreferencesKey("interval_training.choice_count")

        /**
         * Reads settings, ignoring unknown enum names (e.g. after a rename) and falling back to
         * defaults for anything missing or no longer valid.
         */
        internal fun decode(preferences: Preferences): IntervalExerciseSettings {
            val defaults = IntervalExerciseSettings()
            val intervals = preferences[INTERVALS]
                ?.mapNotNull { name -> Interval.entries.find { it.name == name } }
                ?.sortedBy { it.semitones }
                ?: defaults.intervals
            val styles = preferences[STYLES]
                ?.mapNotNull { name -> IntervalStyle.entries.find { it.name == name } }
                ?.toSet()
                ?: defaults.styles
            val choiceCount = preferences[CHOICE_COUNT]
                ?.takeIf { it in IntervalExerciseSettings.CHOICE_COUNT_OPTIONS }
                ?: defaults.choiceCount
            return runCatching {
                defaults.copy(intervals = intervals, styles = styles, choiceCount = choiceCount)
            }.getOrDefault(defaults)
        }

        internal fun encode(settings: IntervalExerciseSettings, preferences: MutablePreferences) {
            preferences[INTERVALS] = settings.intervals.map { it.name }.toSet()
            preferences[STYLES] = settings.styles.map { it.name }.toSet()
            preferences[CHOICE_COUNT] = settings.choiceCount
        }
    }
}
