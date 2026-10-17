package com.example.eartrainer.exercise

import com.example.eartrainer.music.Interval
import com.example.eartrainer.music.Pitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class IntervalQuestionGeneratorTest {

    private fun generate(count: Int, settings: IntervalExerciseSettings = IntervalExerciseSettings(), seed: Int = 42) =
        IntervalQuestionGenerator(settings, Random(seed)).let { generator -> List(count) { generator.generate() } }

    @Test
    fun allPitchesStayWithinConfiguredRange() {
        val settings = IntervalExerciseSettings(pitchRange = 55..72, styles = IntervalStyle.entries.toSet())
        generate(2_000, settings).forEach { question ->
            question.prompt.moments.flatten().forEach { pitch ->
                assertTrue("$pitch outside ${settings.pitchRange}", pitch.midi in settings.pitchRange)
            }
        }
    }

    @Test
    fun widestIntervalFitsAtEdgeOfNarrowestRange() {
        val settings = IntervalExerciseSettings(pitchRange = 60..72)
        generate(500, settings).filter { it.interval == Interval.OCTAVE }.forEach {
            assertEquals(Pitch(60), it.lowerPitch)
            assertEquals(Pitch(72), it.upperPitch)
        }
    }

    @Test
    fun choicesContainCorrectAnswerWithoutDuplicates() {
        generate(2_000).forEach { question ->
            assertTrue(question.correctAnswer in question.choices)
            assertEquals(question.choices.size, question.choices.toSet().size)
            assertEquals(6, question.choices.size)
        }
    }

    @Test
    fun choicesAreNeighbouringIntervalsInAscendingOrder() {
        generate(500).forEach { question ->
            val semitones = question.choices.map { it.semitones }
            assertEquals(semitones.sorted(), semitones)
            assertEquals(semitones.size - 1, semitones.last() - semitones.first())
        }
    }

    @Test
    fun correctAnswerAppearsInEveryPosition() {
        val positions = generate(2_000).map { it.choices.indexOf(it.correctAnswer) }.toSet()
        assertEquals((0 until 6).toSet(), positions)
    }

    @Test
    fun choicesComeOnlyFromEnabledIntervals() {
        val enabled = listOf(Interval.MAJOR_THIRD, Interval.PERFECT_FIFTH, Interval.OCTAVE)
        generate(300, IntervalExerciseSettings(intervals = enabled, choiceCount = 6)).forEach { question ->
            assertEquals(enabled, question.choices)
            assertTrue(question.interval in enabled)
        }
    }

    @Test
    fun everyEnabledIntervalIsEventuallyAsked() {
        assertEquals(Interval.entries.toSet(), generate(2_000).map { it.interval }.toSet())
    }

    @Test
    fun ascendingPromptPlaysLowerNoteThenUpperNote() {
        generate(200).forEach { question ->
            assertEquals(IntervalStyle.MELODIC_ASCENDING, question.style)
            assertEquals(listOf(listOf(question.lowerPitch), listOf(question.upperPitch)), question.prompt.moments)
            assertEquals(question.interval, Interval.between(question.lowerPitch, question.upperPitch))
        }
    }

    @Test
    fun otherStylesProducePromptsOfTheSameInterval() {
        val question = IntervalQuestion(Pitch(60), Interval.PERFECT_FIFTH, IntervalStyle.MELODIC_DESCENDING, listOf(Interval.PERFECT_FIFTH, Interval.MINOR_SIXTH))
        assertEquals(listOf(listOf(Pitch(67)), listOf(Pitch(60))), question.prompt.moments)
        val harmonic = question.copy(style = IntervalStyle.HARMONIC)
        assertEquals(listOf(listOf(Pitch(60), Pitch(67))), harmonic.prompt.moments)
    }

    @Test
    fun invalidSettingsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { IntervalExerciseSettings(intervals = emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { IntervalExerciseSettings(pitchRange = 60..70) }
        assertThrows(IllegalArgumentException::class.java) { IntervalExerciseSettings(pitchRange = 120..140) }
        assertThrows(IllegalArgumentException::class.java) { IntervalExerciseSettings(choiceCount = 1) }
    }

    @Test
    fun questionRejectsInvalidChoices() {
        assertThrows(IllegalArgumentException::class.java) {
            IntervalQuestion(Pitch(60), Interval.MAJOR_THIRD, IntervalStyle.MELODIC_ASCENDING, listOf(Interval.MINOR_THIRD))
        }
        assertThrows(IllegalArgumentException::class.java) {
            IntervalQuestion(
                Pitch(60), Interval.MAJOR_THIRD, IntervalStyle.MELODIC_ASCENDING,
                listOf(Interval.MAJOR_THIRD, Interval.MAJOR_THIRD),
            )
        }
    }
}
