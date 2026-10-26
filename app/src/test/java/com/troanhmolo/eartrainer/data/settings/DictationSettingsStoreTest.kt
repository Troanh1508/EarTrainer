package com.troanhmolo.eartrainer.data.settings

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.troanhmolo.eartrainer.exercise.DictationSettings
import com.troanhmolo.eartrainer.exercise.KeyChoice
import com.troanhmolo.eartrainer.exercise.KeyContext
import com.troanhmolo.eartrainer.music.Mode
import org.junit.Assert.assertEquals
import org.junit.Test

class DictationSettingsStoreTest {

    @Test
    fun missingPreferencesDecodeToDefaults() {
        assertEquals(DictationSettings(), DictationSettingsStore.decode(preferencesOf()))
    }

    @Test
    fun encodeThenDecodeRoundTrips() {
        val custom = DictationSettings(Mode.entries.toSet(), length = 6, keyChoice = KeyChoice.C_ONLY, context = KeyContext.TONIC_CHORD, focusOnWeakSpots = false)
        val preferences = mutablePreferencesOf()
        DictationSettingsStore.encode(custom, preferences)
        assertEquals(custom, DictationSettingsStore.decode(preferences))
    }

    @Test
    fun unknownOrUnofferedValuesFallBackPerSetting() {
        val decoded = DictationSettingsStore.decode(
            preferencesOf(
                stringSetPreferencesKey("dictation.modes") to setOf("MINOR", "PHRYGIAN"),
                stringPreferencesKey("dictation.context") to "DRONE",
                intPreferencesKey("dictation.length") to 12,
            ),
        )
        assertEquals(DictationSettings(modes = setOf(Mode.MINOR)), decoded)
    }

    @Test
    fun noKnownModesFallBackToDefaults() {
        assertEquals(DictationSettings(), DictationSettingsStore.decode(preferencesOf(stringSetPreferencesKey("dictation.modes") to setOf("PHRYGIAN"))))
    }
}
