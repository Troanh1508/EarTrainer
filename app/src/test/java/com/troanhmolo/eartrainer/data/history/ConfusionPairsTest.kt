package com.troanhmolo.eartrainer.data.history

import com.troanhmolo.eartrainer.exercise.rhythmCellPairs
import com.troanhmolo.eartrainer.exercise.rhythmCellResults
import com.troanhmolo.eartrainer.music.DiatonicChord
import com.troanhmolo.eartrainer.music.Mode
import com.troanhmolo.eartrainer.music.Rhythm
import com.troanhmolo.eartrainer.music.RhythmCell
import com.troanhmolo.eartrainer.music.ScaleDegree
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfusionPairsTest {

    private fun chord(step: Int) = DiatonicChord(Mode.MAJOR, step)

    @Test
    fun progressionChordsPairWithTheChordChosenInTheirPlaceAfterTheTonic() {
        assertEquals(
            listOf(chord(6) to chord(6), chord(4) to chord(2), chord(5) to null),
            progressionChordPairs(Mode.MAJOR, asked = listOf(1, 6, 4, 5), chosen = listOf(1, 6, 2)),
        )
        // Scoring agrees with the pairs.
        assertEquals(listOf(true, false, false), progressionChordResults(Mode.MAJOR, listOf(1, 6, 4, 5), listOf(1, 6, 2)).map { it.isCorrect })
    }

    @Test
    fun dictationNotesPairWithTheWrittenNoteOrNullWhenLeftBlank() {
        val played = listOf(ScaleDegree.TONIC, ScaleDegree.SECOND, ScaleDegree.THIRD)
        val written = listOf(ScaleDegree.TONIC, ScaleDegree.FOURTH, null)
        assertEquals(listOf(ScaleDegree.SECOND to ScaleDegree.FOURTH, ScaleDegree.THIRD to null), dictationNotePairs(played, written))
    }

    @Test
    fun rhythmCellsPairWithTheChosenCellOnTheSameBeat() {
        val played = Rhythm(listOf(RhythmCell.HALF, RhythmCell.QUARTER, RhythmCell.EIGHTHS))
        // The first half bar is split differently, so nothing starts on beat 2 to compare with.
        val chosen = Rhythm(listOf(RhythmCell.QUARTER, RhythmCell.QUARTER, RhythmCell.QUARTER, RhythmCell.EIGHTHS))
        assertEquals(
            listOf(RhythmCell.HALF to RhythmCell.QUARTER, RhythmCell.QUARTER to RhythmCell.QUARTER, RhythmCell.EIGHTHS to RhythmCell.EIGHTHS),
            rhythmCellPairs(played, chosen),
        )
        assertEquals(listOf(false, true, true), rhythmCellResults(played, chosen).map { it.isCorrect })
    }

    @Test
    fun identicalPairsAreCounted() {
        val counted = listOf("a" to "a", "a" to "b", "a" to "a", "b" to null).countConfusions()
        assertEquals(
            setOf(Confusion("a", "a", 2), Confusion("a", "b", 1), Confusion("b", null, 1)),
            counted.toSet(),
        )
    }
}
