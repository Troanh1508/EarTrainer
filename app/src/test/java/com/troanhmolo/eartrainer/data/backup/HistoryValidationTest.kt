package com.troanhmolo.eartrainer.data.backup

import com.troanhmolo.eartrainer.data.history.ChordAttemptEntity
import com.troanhmolo.eartrainer.data.history.DictationAttemptEntity
import com.troanhmolo.eartrainer.data.history.IntervalAttemptEntity
import com.troanhmolo.eartrainer.data.history.ProgressionAttemptEntity
import com.troanhmolo.eartrainer.data.history.RhythmAttemptEntity
import com.troanhmolo.eartrainer.data.history.ScaleDegreeAttemptEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryValidationTest {

    private val interval = IntervalAttemptEntity(0, "s", 1_000, "PERFECT_FIFTH", "HARMONIC", 55, "PERFECT_FIFTH", true, 2, 1_800)
    private val chord = ChordAttemptEntity(0, "s", 1_000, "MINOR", "FIRST", "BLOCK", "QUALITY", 52, "MAJOR", false, 1, 2_500)
    private val degree = ScaleDegreeAttemptEntity(0, "s", 1_000, 60, "MAJOR", "THIRD", "CADENCE", "THIRD", true, 1, 1_200)
    private val progression = ProgressionAttemptEntity(0, "s", 1_000, 60, "MINOR", "1,6,4,5", "1,4,4,5", false, 1, 3_000)
    private val dictation = DictationAttemptEntity(0, "s", 1_000, 62, "MINOR", "TONIC,SECOND,THIRD", "TONIC,SECOND,FOURTH", false, 2, 7_000)
    private val rhythm = RhythmAttemptEntity(0, "s", 1_000, "MEDIUM", "QUARTER,EIGHTHS,HALF", "QUARTER,QUARTER_REST,HALF", false, 1, 4_000)

    @Test
    fun rowsTheAppWritesAreValid() {
        assertTrue(interval.isValid())
        assertTrue(chord.isValid())
        assertTrue(chord.copy(target = "INVERSION", answer = "ROOT_POSITION").isValid())
        assertTrue(degree.isValid())
        assertTrue(progression.isValid())
        assertTrue(dictation.isValid())
        assertTrue(rhythm.isValid())
    }

    @Test
    fun rowsWithMissingOrImpossibleBasicsAreInvalid() {
        assertFalse(interval.copy(sessionId = " ").isValid())
        assertFalse(interval.copy(answeredAtMillis = 0).isValid())
        assertFalse(interval.copy(playCount = -1).isValid())
        assertFalse(interval.copy(responseMillis = -5).isValid())
        assertFalse(interval.copy(lowerMidi = 200).isValid())
    }

    @Test
    fun rowsWithNamesTheAppDoesNotKnowAreInvalid() {
        assertFalse(interval.copy(interval = "PERFECT_TWELFTH").isValid())
        assertFalse(interval.copy(style = "harmonic").isValid())
        assertFalse(chord.copy(target = "ROOT").isValid())
        // An inversion question must be answered with an inversion.
        assertFalse(chord.copy(target = "INVERSION", answer = "MAJOR").isValid())
        assertFalse(degree.copy(context = "DRONE").isValid())
        assertFalse(progression.copy(progression = "1,8,4").isValid())
        assertFalse(progression.copy(answer = "").isValid())
        assertFalse(dictation.copy(melody = "TONIC,,THIRD").isValid())
        assertFalse(rhythm.copy(tempo = "LARGO").isValid())
        // Cells that do not fill whole bars are not a rhythm.
        assertFalse(rhythm.copy(answer = "QUARTER,HALF").isValid())
    }
}
