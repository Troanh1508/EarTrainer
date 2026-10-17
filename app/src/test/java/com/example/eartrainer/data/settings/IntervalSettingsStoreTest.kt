package com.example.eartrainer.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.eartrainer.exercise.IntervalExerciseSettings
import com.example.eartrainer.exercise.IntervalStyle
import com.example.eartrainer.music.Interval
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class IntervalSettingsStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val custom = IntervalExerciseSettings(
        intervals = listOf(Interval.MAJOR_SECOND, Interval.PERFECT_FIFTH, Interval.OCTAVE),
        styles = setOf(IntervalStyle.MELODIC_DESCENDING, IntervalStyle.HARMONIC),
        choiceCount = 8,
        focusOnWeakSpots = false,
    )

    @Test
    fun missingPreferencesDecodeToDefaults() {
        assertEquals(IntervalExerciseSettings(), IntervalSettingsStore.decode(preferencesOf()))
    }

    @Test
    fun encodeThenDecodeRoundTrips() {
        val preferences = mutablePreferencesOf()
        IntervalSettingsStore.encode(custom, preferences)
        assertEquals(custom, IntervalSettingsStore.decode(preferences))
    }

    @Test
    fun unknownNamesAndUnofferedChoiceCountsAreIgnored() {
        val preferences = preferencesOf(
            stringSetPreferencesKey("interval_training.intervals") to setOf("OCTAVE", "PERFECT_FIFTH", "NINTH"),
            stringSetPreferencesKey("interval_training.styles") to setOf("HARMONIC", "SUNG"),
            intPreferencesKey("interval_training.choice_count") to 5,
        )
        val decoded = IntervalSettingsStore.decode(preferences)
        assertEquals(listOf(Interval.PERFECT_FIFTH, Interval.OCTAVE), decoded.intervals)
        assertEquals(setOf(IntervalStyle.HARMONIC), decoded.styles)
        assertEquals(IntervalExerciseSettings().choiceCount, decoded.choiceCount)
    }

    @Test
    fun noKnownStylesFallBackToDefaults() {
        val preferences = preferencesOf(stringSetPreferencesKey("interval_training.styles") to setOf("SUNG"))
        assertEquals(IntervalExerciseSettings(), IntervalSettingsStore.decode(preferences))
    }

    @Test
    fun tooFewIntervalsFallBackToDefaults() {
        val preferences = preferencesOf(stringSetPreferencesKey("interval_training.intervals") to setOf("OCTAVE"))
        assertEquals(IntervalExerciseSettings(), IntervalSettingsStore.decode(preferences))
    }

    @Test
    fun updatesPersistToDisk() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(tempFolder.root, "settings.preferences_pb") }
            val store = IntervalSettingsStore(dataStore)
            assertEquals(IntervalExerciseSettings(), store.settings.first())

            store.update { custom }
            assertEquals(custom, store.settings.first())

            store.update { it.withStyleToggled(IntervalStyle.MELODIC_ASCENDING) }
            assertEquals(IntervalStyle.entries.toSet(), store.settings.first().styles)
        } finally {
            scope.cancel()
        }
    }
}
