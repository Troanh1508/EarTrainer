package com.example.eartrainer.data.settings

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.eartrainer.exercise.RhythmSettings
import com.example.eartrainer.exercise.RhythmTempo
import com.example.eartrainer.exercise.RhythmVocabulary
import org.junit.Assert.assertEquals
import org.junit.Test

class RhythmSettingsStoreTest {

    @Test
    fun missingPreferencesDecodeToDefaults() {
        assertEquals(RhythmSettings(), RhythmSettingsStore.decode(preferencesOf()))
    }

    @Test
    fun encodeThenDecodeRoundTrips() {
        val custom = RhythmSettings(RhythmVocabulary.SIXTEENTHS, bars = 2, tempo = RhythmTempo.FAST, focusOnWeakSpots = false)
        val preferences = mutablePreferencesOf()
        RhythmSettingsStore.encode(custom, preferences)
        assertEquals(custom, RhythmSettingsStore.decode(preferences))
    }

    @Test
    fun unknownOrUnofferedValuesFallBackPerSetting() {
        val decoded = RhythmSettingsStore.decode(
            preferencesOf(
                stringPreferencesKey("rhythm.vocabulary") to "TRIPLETS",
                intPreferencesKey("rhythm.bars") to 4,
                stringPreferencesKey("rhythm.tempo") to "SLOW",
            ),
        )
        assertEquals(RhythmSettings(tempo = RhythmTempo.SLOW), decoded)
    }
}
