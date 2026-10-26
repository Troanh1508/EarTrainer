package com.troanhmolo.eartrainer.data.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.troanhmolo.eartrainer.exercise.ChordExerciseSettings
import com.troanhmolo.eartrainer.exercise.ChordStyle
import com.troanhmolo.eartrainer.exercise.ChordTarget
import com.troanhmolo.eartrainer.music.ChordQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class ChordSettingsStoreTest {

    @Test
    fun missingPreferencesDecodeToDefaults() {
        assertEquals(ChordExerciseSettings(), ChordSettingsStore.decode(preferencesOf()))
    }

    @Test
    fun encodeThenDecodeRoundTrips() {
        val custom = ChordExerciseSettings(
            qualities = listOf(ChordQuality.MINOR, ChordQuality.DOMINANT_SEVENTH, ChordQuality.DIMINISHED_SEVENTH),
            styles = setOf(ChordStyle.ARPEGGIATED),
            target = ChordTarget.INVERSION,
            includeInversions = true,
            focusOnWeakSpots = false,
        )
        val preferences = mutablePreferencesOf()
        ChordSettingsStore.encode(custom, preferences)
        assertEquals(custom, ChordSettingsStore.decode(preferences))
    }

    @Test
    fun qualitiesComeBackInCatalogOrderAndUnknownValuesAreIgnored() {
        val decoded = ChordSettingsStore.decode(
            preferencesOf(
                stringSetPreferencesKey("chord_training.qualities") to setOf("MINOR_SEVENTH", "MAJOR", "SUS4"),
                stringPreferencesKey("chord_training.target") to "VOICING",
            ),
        )
        assertEquals(listOf(ChordQuality.MAJOR, ChordQuality.MINOR_SEVENTH), decoded.qualities)
        assertEquals(ChordTarget.QUALITY, decoded.target)
    }

    @Test
    fun invalidStoredSettingsFallBackToDefaults() {
        val preferences = preferencesOf(stringSetPreferencesKey("chord_training.qualities") to setOf("MAJOR"))
        assertEquals(ChordExerciseSettings(), ChordSettingsStore.decode(preferences))
    }
}
