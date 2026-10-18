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
        assertEquals(GoalSettings(30), GoalSettingsStore.decode(preferencesOf()))
    }

    @Test
    fun everyOfferedGoalRoundTrips() {
        GoalSettings.DAILY_ANSWER_OPTIONS.forEach { answers ->
            val preferences = mutablePreferencesOf()
            GoalSettingsStore.encode(GoalSettings(answers), preferences)
            assertEquals(GoalSettings(answers), GoalSettingsStore.decode(preferences))
        }
    }

    @Test
    fun goalsNotOnOfferFallBackToTheDefault() {
        assertEquals(GoalSettings(), GoalSettingsStore.decode(preferencesOf(intPreferencesKey("goal.daily_answers") to 37)))
        assertThrows(IllegalArgumentException::class.java) { GoalSettings(0) }
    }
}
