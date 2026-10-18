package com.example.eartrainer.data.backup

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import com.example.eartrainer.data.settings.ChordSettingsStore
import com.example.eartrainer.data.settings.DictationSettingsStore
import com.example.eartrainer.data.settings.IntervalSettingsStore
import com.example.eartrainer.data.settings.ProgressionSettingsStore
import com.example.eartrainer.data.settings.RhythmSettingsStore
import com.example.eartrainer.data.settings.ScaleDegreeSettingsStore
import com.example.eartrainer.exercise.RhythmSettings
import com.example.eartrainer.exercise.RhythmTempo
import com.example.eartrainer.exercise.RhythmVocabulary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupTest {

    private val custom = RhythmSettings(RhythmVocabulary.SIXTEENTHS, bars = 2, tempo = RhythmTempo.FAST, focusOnWeakSpots = false)

    /** Every store must be able to read [preferences]; a wrongly typed value would throw here. */
    private fun decodeAll(preferences: Preferences) {
        IntervalSettingsStore.decode(preferences)
        ChordSettingsStore.decode(preferences)
        ScaleDegreeSettingsStore.decode(preferences)
        ProgressionSettingsStore.decode(preferences)
        DictationSettingsStore.decode(preferences)
        RhythmSettingsStore.decode(preferences)
    }

    @Test
    fun exportWritesEverySettingEvenWhenNothingWasChanged() {
        val exported = SettingsBackup.export(preferencesOf())
        assertTrue(exported.keys.containsAll(listOf("interval_training.styles", "chord_training.target", "scale_degrees.modes", "progressions.length", "dictation.length", "rhythm.tempo", "sound.instrument", "goal.daily_answers")))
        assertEquals(SettingValue(int = RhythmSettings().bars), exported["rhythm.bars"])
        assertTrue(exported.values.all { listOfNotNull(it.string, it.int, it.boolean, it.stringSet).size == 1 })
    }

    @Test
    fun exportThenImportRestoresTheSameSettings() {
        val preferences = mutablePreferencesOf().also { RhythmSettingsStore.encode(custom, it) }
        val restored = SettingsBackup.import(SettingsBackup.export(preferences))
        assertEquals(custom, RhythmSettingsStore.decode(restored))
        assertEquals(SettingsBackup.export(preferences), SettingsBackup.export(restored))
    }

    @Test
    fun valuesOfTheWrongTypeAndUnknownKeysAreDroppedSoTheStoresCanStillReadThem() {
        val restored = SettingsBackup.import(
            mapOf(
                "rhythm.bars" to SettingValue(string = "2"),
                "rhythm.tempo" to SettingValue(stringSet = setOf("FAST")),
                "interval_training.choice_count" to SettingValue(boolean = true),
                "chord_training.qualities" to SettingValue(int = 3),
                "rhythm.focus_weak_spots" to SettingValue(boolean = false),
                "someone.elses.key" to SettingValue(string = "x"),
                "rhythm.vocabulary" to SettingValue(),
            ),
        )
        decodeAll(restored)
        assertEquals(RhythmSettings(focusOnWeakSpots = false), RhythmSettingsStore.decode(restored))
        assertTrue(restored.asMap().keys.none { it.name == "someone.elses.key" })
    }

    @Test
    fun invalidChoicesFallBackToDefaults() {
        val restored = SettingsBackup.import(
            mapOf("rhythm.bars" to SettingValue(int = 9), "rhythm.tempo" to SettingValue(string = "PRESTISSIMO"), "rhythm.vocabulary" to SettingValue(string = "SYNCOPATION")),
        )
        assertEquals(RhythmSettings(vocabulary = RhythmVocabulary.SYNCOPATION), RhythmSettingsStore.decode(restored))
    }
}
