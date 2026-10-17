package com.example.eartrainer.data.settings

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.eartrainer.exercise.ChordVocabulary
import com.example.eartrainer.exercise.KeyChoice
import com.example.eartrainer.exercise.ProgressionSettings
import com.example.eartrainer.exercise.ProgressionSource
import com.example.eartrainer.music.Mode
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressionSettingsStoreTest {

    @Test
    fun missingPreferencesDecodeToDefaults() {
        assertEquals(ProgressionSettings(), ProgressionSettingsStore.decode(preferencesOf()))
    }

    @Test
    fun encodeThenDecodeRoundTrips() {
        val custom = ProgressionSettings(Mode.entries.toSet(), ChordVocabulary.ALL, length = 5, keyChoice = KeyChoice.C_ONLY, source = ProgressionSource.RANDOM)
        val preferences = mutablePreferencesOf()
        ProgressionSettingsStore.encode(custom, preferences)
        assertEquals(custom, ProgressionSettingsStore.decode(preferences))
    }

    @Test
    fun unknownOrUnofferedValuesFallBackPerSetting() {
        val decoded = ProgressionSettingsStore.decode(
            preferencesOf(
                stringSetPreferencesKey("progressions.modes") to setOf("MINOR", "LOCRIAN"),
                stringPreferencesKey("progressions.vocabulary") to "JAZZ",
                intPreferencesKey("progressions.length") to 8,
            ),
        )
        assertEquals(ProgressionSettings(modes = setOf(Mode.MINOR)), decoded)
    }

    @Test
    fun commonProgressionsAreTheDefaultForExistingSettings() {
        // Settings saved before the source option existed have no source key.
        val saved = preferencesOf(intPreferencesKey("progressions.length") to 5)
        assertEquals(ProgressionSource.COMMON, ProgressionSettingsStore.decode(saved).source)
    }

    @Test
    fun noKnownModesFallBackToDefaults() {
        val preferences = preferencesOf(stringSetPreferencesKey("progressions.modes") to setOf("LOCRIAN"))
        assertEquals(ProgressionSettings(), ProgressionSettingsStore.decode(preferences))
    }
}
