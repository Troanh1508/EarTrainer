package com.example.eartrainer.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** How much practice a day counts as reaching the goal on the Home screen. */
data class GoalSettings(val dailyAnswers: Int = DEFAULT_DAILY_ANSWERS) {
    init {
        require(dailyAnswers in DAILY_ANSWER_OPTIONS) { "Daily goal must be one of $DAILY_ANSWER_OPTIONS" }
    }

    companion object {
        /** Enough for a focused ten minutes or so. */
        const val DEFAULT_DAILY_ANSWERS = 30
        val DAILY_ANSWER_OPTIONS = listOf(10, 20, 30, 50, 75, 100)
    }
}

/** Persists the daily goal in the shared Preferences DataStore. */
class GoalSettingsStore(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<GoalSettings> = dataStore.data
        // An unreadable file should not hide the Home screen; fall back to defaults.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::decode)
        .distinctUntilChanged()

    suspend fun update(transform: (GoalSettings) -> GoalSettings) {
        dataStore.edit { preferences -> encode(transform(decode(preferences)), preferences) }
    }

    companion object {
        private val DAILY_ANSWERS = intPreferencesKey("goal.daily_answers")

        /** Falls back to the default for anything missing or not on offer. */
        internal fun decode(preferences: Preferences): GoalSettings = GoalSettings(
            dailyAnswers = preferences[DAILY_ANSWERS]?.takeIf { it in GoalSettings.DAILY_ANSWER_OPTIONS }
                ?: GoalSettings.DEFAULT_DAILY_ANSWERS,
        )

        internal fun encode(settings: GoalSettings, preferences: MutablePreferences) {
            preferences[DAILY_ANSWERS] = settings.dailyAnswers
        }
    }
}
