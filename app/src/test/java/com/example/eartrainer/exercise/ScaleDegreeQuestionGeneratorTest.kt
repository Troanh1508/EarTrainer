package com.example.eartrainer.exercise

import com.example.eartrainer.music.Key
import com.example.eartrainer.music.Mode
import com.example.eartrainer.music.Pitch
import com.example.eartrainer.music.ScaleDegree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ScaleDegreeQuestionGeneratorTest {

    private fun generate(settings: ScaleDegreeSettings, count: Int = 2_000, seed: Int = 3) =
        ScaleDegreeQuestionGenerator(settings, Random(seed)).let { generator -> List(count) { generator.generate() } }

    @Test
    fun diatonicQuestionsOfferTheSevenNotesOfTheirMode() {
        val questions = generate(ScaleDegreeSettings(modes = Mode.entries.toSet()))
        questions.forEach { question ->
            assertEquals(question.key.mode.diatonicDegrees, question.choices)
            assertTrue(question.degree in question.choices)
        }
        assertEquals(Mode.entries.toSet(), questions.map { it.key.mode }.toSet())
        assertEquals(ScaleDegree.entries.toSet() - setOf(ScaleDegree.FLAT_SECOND, ScaleDegree.SHARP_FOURTH), questions.map { it.degree }.toSet())
    }

    @Test
    fun chromaticQuestionsOfferAllTwelveNotes() {
        val questions = generate(ScaleDegreeSettings(degreeSet = DegreeSet.CHROMATIC))
        questions.forEach { assertEquals(ScaleDegree.entries, it.choices) }
        assertEquals(ScaleDegree.entries.toSet(), questions.map { it.degree }.toSet())
    }

    @Test
    fun keysAreRandomWithinTheTonicRangeOrAlwaysC() {
        val random = generate(ScaleDegreeSettings()).map { it.key.tonic.midi }
        assertTrue(random.all { it in KeyChoice.TONIC_RANGE })
        assertEquals(KeyChoice.TONIC_RANGE.toSet(), random.toSet())
        assertTrue(generate(ScaleDegreeSettings(keyChoice = KeyChoice.C_ONLY), count = 200).all { it.key.tonic == Pitch(60) })
    }

    @Test
    fun promptSetsTheKeyThenPlaysTheTargetAboveTheTonic() {
        val key = Key(Pitch(62), Mode.MINOR)
        val question = ScaleDegreeQuestion(key, ScaleDegree.FLAT_SIXTH, KeyContext.CADENCE, Mode.MINOR.diatonicDegrees)
        assertEquals(Pitch(70), question.target)
        assertEquals(listOf(listOf(Pitch(70))), question.prompt.moments)
        assertEquals(key.cadence(), question.prompt.context)
        assertEquals(listOf(key.tonicChord()), question.copy(context = KeyContext.TONIC_CHORD).prompt.context)
    }

    @Test
    fun everySoundedPitchIsWithinAComfortableRange() {
        generate(ScaleDegreeSettings(modes = Mode.entries.toSet(), degreeSet = DegreeSet.CHROMATIC)).forEach { question ->
            val all = question.prompt.context.flatten() + question.prompt.moments.flatten()
            assertTrue(all.all { it.midi in 43..77 })
        }
    }

    @Test
    fun modesToggleButNeverBecomeEmpty() {
        val defaults = ScaleDegreeSettings()
        assertSame(defaults, defaults.withModeToggled(Mode.MAJOR))
        val both = defaults.withModeToggled(Mode.MINOR)
        assertEquals(Mode.entries.toSet(), both.modes)
        assertEquals(setOf(Mode.MINOR), both.withModeToggled(Mode.MAJOR).modes)
        assertThrows(IllegalArgumentException::class.java) { ScaleDegreeSettings(modes = emptySet()) }
    }

    @Test
    fun attemptRecordsKeyDegreeAndAnswer() {
        val question = ScaleDegreeQuestion(Key(Pitch(60), Mode.MAJOR), ScaleDegree.SIXTH, KeyContext.TONIC_CHORD, Mode.MAJOR.diatonicDegrees)
        val answered = ExerciseEngine<ScaleDegree, ScaleDegreeQuestion> { question }.start()
            .promptPlayed(1_000)
            .promptPlayed(2_000)
            .submitAnswer(ScaleDegree.FIFTH, 5_000)
        assertEquals(
            ScaleDegreeAttempt("s", 5_000, question.key, ScaleDegree.SIXTH, KeyContext.TONIC_CHORD, ScaleDegree.FIFTH, false, 2, 4_000),
            ScaleDegreeAttempt.from(answered, "s"),
        )
    }
}
