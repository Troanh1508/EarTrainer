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

/** How many finished sessions a day count as reaching the goal on the Home screen. */
data class GoalSettings(val dailySessions: Int = DEFAULT_DAILY_SESSIONS) {
    init {
        require(dailySessions in DAILY_SESSION_OPTIONS) { "Daily goal must be one of $DAILY_SESSION_OPTIONS" }
    }

    companion object {
        /** About ten to fifteen focused minutes. */
        const val DEFAULT_DAILY_SESSIONS = 2
        val DAILY_SESSION_OPTIONS = listOf(1, 2, 3, 4, 5)
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
        // A new key: goals saved as a number of answers do not carry over as sessions.
        private val DAILY_SESSIONS = intPreferencesKey("goal.daily_sessions")

        /** Falls back to the default for anything missing or not on offer. */
        internal fun decode(preferences: Preferences): GoalSettings = GoalSettings(
            dailySessions = preferences[DAILY_SESSIONS]?.takeIf { it in GoalSettings.DAILY_SESSION_OPTIONS }
                ?: GoalSettings.DEFAULT_DAILY_SESSIONS,
        )

        internal fun encode(settings: GoalSettings, preferences: MutablePreferences) {
            preferences[DAILY_SESSIONS] = settings.dailySessions
        }
    }
}
