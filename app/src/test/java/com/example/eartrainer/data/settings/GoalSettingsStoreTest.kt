package com.example.eartrainer.data.settings

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GoalSettingsStoreTest {

    @Test
    fun thirtyAnswersADayIsTheDefault() {
        assertEquals(GoalSettings(2), GoalSettingsStore.decode(preferencesOf()))
    }

    @Test
    fun everyOfferedGoalRoundTrips() {
        GoalSettings.DAILY_SESSION_OPTIONS.forEach { sessions ->
            val preferences = mutablePreferencesOf()
            GoalSettingsStore.encode(GoalSettings(sessions), preferences)
            assertEquals(GoalSettings(sessions), GoalSettingsStore.decode(preferences))
        }
    }

    @Test
    fun goalsNotOnOfferFallBackToTheDefault() {
        assertEquals(GoalSettings(), GoalSettingsStore.decode(preferencesOf(intPreferencesKey("goal.daily_sessions") to 7)))
        // A goal saved as a number of answers, before goals counted sessions, is not read as sessions.
        assertEquals(GoalSettings(), GoalSettingsStore.decode(preferencesOf(intPreferencesKey("goal.daily_answers") to 3)))
        assertThrows(IllegalArgumentException::class.java) { GoalSettings(0) }
    }
}
