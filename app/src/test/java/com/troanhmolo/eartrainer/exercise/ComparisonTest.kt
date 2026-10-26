package com.troanhmolo.eartrainer.exercise

import com.troanhmolo.eartrainer.data.history.progressionChordResults
import com.troanhmolo.eartrainer.music.ChordInversion
import com.troanhmolo.eartrainer.music.ChordQuality
import com.troanhmolo.eartrainer.music.DiatonicChord
import com.troanhmolo.eartrainer.music.Interval
import com.troanhmolo.eartrainer.music.Mode
import com.troanhmolo.eartrainer.music.RhythmCell
import com.troanhmolo.eartrainer.music.ScaleDegree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ComparisonTest {

    private fun <A, Q : ExerciseQuestion<A>> Comparison<A, Q>.questions() = List(200) { nextQuestion() }

    @Test
    fun intervalQuestionsAskOnlyForTheTwoWithThoseAsTheChoices() {
        val comparison = IntervalComparison(Interval.MAJOR_SIXTH, Interval.MINOR_SIXTH, IntervalStyle.HARMONIC, Random(3))
        val answers = listOf(Interval.MINOR_SIXTH, Interval.MAJOR_SIXTH)
        assertEquals(answers, comparison.answers)
        val questions = comparison.questions()
        assertEquals(answers.toSet(), questions.map { it.interval }.toSet())
        assertTrue(questions.all { it.choices == answers && it.style == IntervalStyle.HARMONIC })
        assertTrue(questions.all { it.upperPitch.midi <= 84 && it.lowerPitch.midi >= 48 })
        // Starting notes vary, so neither interval can be recognized by its pitch alone.
        assertTrue(questions.map { it.lowerPitch }.toSet().size > 10)
    }

    @Test
    fun intervalStylesAreMixedWhenNoStyleIsGiven() {
        val questions = IntervalComparison(Interval.TRITONE, Interval.PERFECT_FOURTH, style = null, random = Random(4)).questions()
        assertEquals(IntervalStyle.entries.toSet(), questions.map { it.style }.toSet())
    }

    @Test
    fun intervalExamplesStartFromTheSameNoteInTheSameStyle() {
        val comparison = IntervalComparison(Interval.MINOR_SIXTH, Interval.MAJOR_SIXTH, IntervalStyle.MELODIC_DESCENDING, Random(5))
        val (minor, major) = comparison.examples
        // Descending: the same lower note sounds last in both.
        assertEquals(minor.moments.last(), major.moments.last())
        assertEquals(8, minor.moments.first().single().midi - minor.moments.last().single().midi)
        assertEquals(9, major.moments.first().single().midi - major.moments.last().single().midi)
    }

    @Test
    fun chordQualitiesAreComparedAsRootPositionBlockChords() {
        val comparison = ChordComparison(ChordAnswer.Quality(ChordQuality.MINOR), ChordAnswer.Quality(ChordQuality.MAJOR), Random(6))
        assertEquals(listOf(ChordAnswer.Quality(ChordQuality.MAJOR), ChordAnswer.Quality(ChordQuality.MINOR)), comparison.answers)
        comparison.questions().forEach { question ->
            assertEquals(ChordTarget.QUALITY, question.target)
            assertEquals(ChordInversion.ROOT_POSITION, question.inversion)
            assertEquals(ChordStyle.BLOCK, question.style)
            assertEquals(comparison.answers, question.choices)
        }
        val (major, minor) = comparison.examples
        assertEquals(major.moments.single().first(), minor.moments.single().first())
    }

    @Test
    fun inversionsAreComparedOnChordsThatHaveBoth() {
        val third = ChordAnswer.Inversion(ChordInversion.THIRD)
        val first = ChordAnswer.Inversion(ChordInversion.FIRST)
        val comparison = ChordComparison(third, first, Random(7))
        comparison.questions().forEach { question ->
            assertTrue(question.quality.inversions.contains(ChordInversion.THIRD))
            assertEquals(ChordTarget.INVERSION, question.target)
        }
        assertThrows(IllegalArgumentException::class.java) { ChordComparison(third, ChordAnswer.Quality(ChordQuality.MAJOR)) }
    }

    @Test
    fun scaleDegreesAreComparedInRandomKeysWithExamplesInOneKey() {
        val comparison = ScaleDegreeComparison(ScaleDegree.SIXTH, ScaleDegree.FIFTH, Mode.MAJOR, Random(8))
        assertEquals(listOf(ScaleDegree.FIFTH, ScaleDegree.SIXTH), comparison.answers)
        val questions = comparison.questions()
        assertTrue(questions.all { it.key.mode == Mode.MAJOR && it.context == KeyContext.CADENCE && it.choices == comparison.answers })
        assertTrue(questions.map { it.key.tonic }.toSet().size > 3)
        val (fifth, sixth) = comparison.examples
        assertEquals(fifth.context, sixth.context)
    }

    @Test
    fun progressionChordsAreComparedAfterTheTonicSoOnlyTheyAreScored() {
        val mode = Mode.MAJOR
        val comparison = ProgressionChordComparison(DiatonicChord(mode, 4), DiatonicChord(mode, 2), Random(9))
        assertEquals(listOf(listOf(1, 2), listOf(1, 4)), comparison.answers.map { progression -> progression.chords.map { it.step } })
        val questions = comparison.questions()
        assertEquals(comparison.answers.toSet(), questions.map { it.progression }.toSet())
        assertTrue(questions.all { it.choices == comparison.answers && it.key.mode == mode })
        assertTrue(questions.map { it.key.tonic }.toSet().size > 3)
        // Scoring skips the given tonic, so a drill answer counts for the compared chord alone.
        questions.take(20).forEach { question ->
            val scored = progressionChordResults(mode, question.progression.chords.map { it.step }, comparison.answers[0].chords.map { it.step })
            assertEquals(listOf(question.progression.chords.last()), scored.map { it.item })
        }
        // The examples share one key: same tonic chord first.
        assertEquals(comparison.examples[0].moments.first(), comparison.examples[1].moments.first())
    }

    @Test
    fun aTonicMixUpIsComparedAsChangeOrNoChange() {
        val comparison = ProgressionChordComparison(DiatonicChord(Mode.MAJOR, 6), DiatonicChord(Mode.MAJOR, 1))
        assertEquals(listOf(listOf(1, 1), listOf(1, 6)), comparison.answers.map { progression -> progression.chords.map { it.step } })
    }

    @Test
    fun rhythmFiguresAlternateInOppositeOrders() {
        assertEquals(
            listOf(RhythmCell.QUARTER, RhythmCell.EIGHTHS, RhythmCell.QUARTER, RhythmCell.EIGHTHS),
            RhythmCellComparison.alternating(RhythmCell.QUARTER, RhythmCell.EIGHTHS).cells,
        )
        // A two-beat figure only starts on beat 1 or 3, so the one-beat figure fills in.
        assertEquals(listOf(RhythmCell.HALF, RhythmCell.QUARTER, RhythmCell.QUARTER), RhythmCellComparison.alternating(RhythmCell.HALF, RhythmCell.QUARTER).cells)
        assertEquals(listOf(RhythmCell.QUARTER, RhythmCell.QUARTER, RhythmCell.HALF), RhythmCellComparison.alternating(RhythmCell.QUARTER, RhythmCell.HALF).cells)
        assertEquals(
            listOf(RhythmCell.HALF, RhythmCell.DOTTED_QUARTER_EIGHTH),
            RhythmCellComparison.alternating(RhythmCell.HALF, RhythmCell.DOTTED_QUARTER_EIGHTH).cells,
        )
    }

    @Test
    fun rhythmComparisonsScoreOnlyTheTwoFigures() {
        for (first in RhythmCell.entries) {
            for (second in RhythmCell.entries.filter { it != first }) {
                val comparison = RhythmCellComparison(first, second, random = Random(10))
                val (a, b) = comparison.answers
                assertTrue("$first, $second", a != b && a.bars == 1 && b.bars == 1)
                assertTrue("$first, $second", (a.cells + b.cells).all { it == first || it == second })
                assertEquals(comparison.answers.toSet(), comparison.questions().map { it.rhythm }.toSet())
            }
        }
    }

    @Test
    fun aMixedModeComparisonUsesAModeWithBothNotes() {
        assertEquals(Mode.MAJOR, ScaleDegreeComparison.modeFor(ScaleDegree.THIRD, ScaleDegree.FOURTH))
        // Whenever some mode has both notes in its key, the drill uses such a mode.
        for (first in ScaleDegree.entries) {
            for (second in ScaleDegree.entries) {
                val both = listOf(first, second)
                if (Mode.entries.any { it.diatonicDegrees.containsAll(both) }) {
                    assertTrue("$both", ScaleDegreeComparison.modeFor(first, second).diatonicDegrees.containsAll(both))
                }
            }
        }
    }
}
