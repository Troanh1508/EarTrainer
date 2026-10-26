package com.troanhmolo.eartrainer.data.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.troanhmolo.eartrainer.exercise.DegreeSet
import com.troanhmolo.eartrainer.exercise.KeyChoice
import com.troanhmolo.eartrainer.exercise.KeyContext
import com.troanhmolo.eartrainer.exercise.ScaleDegreeSettings
import com.troanhmolo.eartrainer.music.Mode
import org.junit.Assert.assertEquals
import org.junit.Test

class ScaleDegreeSettingsStoreTest {

    @Test
    fun missingPreferencesDecodeToDefaults() {
        assertEquals(ScaleDegreeSettings(), ScaleDegreeSettingsStore.decode(preferencesOf()))
    }

    @Test
    fun encodeThenDecodeRoundTrips() {
        val custom = ScaleDegreeSettings(
            modes = Mode.entries.toSet(),
            degreeSet = DegreeSet.CHROMATIC,
            keyChoice = KeyChoice.C_ONLY,
            context = KeyContext.TONIC_CHORD,
            focusOnWeakSpots = false,
        )
        val preferences = mutablePreferencesOf()
        ScaleDegreeSettingsStore.encode(custom, preferences)
        assertEquals(custom, ScaleDegreeSettingsStore.decode(preferences))
    }

    @Test
    fun unknownValuesFallBackPerSetting() {
        val decoded = ScaleDegreeSettingsStore.decode(
            preferencesOf(
                stringSetPreferencesKey("scale_degrees.modes") to setOf("MINOR", "DORIAN"),
                stringPreferencesKey("scale_degrees.context") to "DRONE",
                stringPreferencesKey("scale_degrees.key_choice") to "C_ONLY",
            ),
        )
        assertEquals(ScaleDegreeSettings(modes = setOf(Mode.MINOR), keyChoice = KeyChoice.C_ONLY), decoded)
    }

    @Test
    fun noKnownModesFallBackToDefaults() {
        val preferences = preferencesOf(stringSetPreferencesKey("scale_degrees.modes") to setOf("LYDIAN"))
        assertEquals(ScaleDegreeSettings(), ScaleDegreeSettingsStore.decode(preferences))
    }
}
