package com.example.eartrainer.exercise

import com.example.eartrainer.music.Key
import com.example.eartrainer.music.ListeningPrompt
import com.example.eartrainer.music.Melody
import com.example.eartrainer.music.Mode
import com.example.eartrainer.music.Pitch
import com.example.eartrainer.music.ScaleDegree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class DictationQuestionGeneratorTest {

    private fun generate(settings: DictationSettings, count: Int = 1_000, seed: Int = 21, weights: ((ScaleDegree) -> Double)? = null) =
        DictationQuestionGenerator(settings, Random(seed), weights).let { generator -> List(count) { generator.generate() } }

    private val cMajor = Key(Pitch(60), Mode.MAJOR)
    private fun melody(vararg degrees: ScaleDegree) = Melody(degrees.toList())

    @Test
    fun melodiesStartOnTheTonicUseOnlyKeyNotesAndNeverRepeat() {
        DictationSettings.LENGTH_OPTIONS.forEach { length ->
            generate(DictationSettings(modes = Mode.entries.toSet(), length = length), count = 300).forEach { question ->
                val degrees = question.melody.degrees
                assertEquals(length, degrees.size)
                assertEquals(ScaleDegree.TONIC, degrees.first())
                assertTrue(degrees.all { it in question.key.mode.diatonicDegrees })
                assertTrue(degrees.zipWithNext().none { (a, b) -> a == b })
            }
        }
    }

    @Test
    fun melodiesMoveMostlyByStep() {
        val scale = Mode.MAJOR.diatonicDegrees
        val moves = generate(DictationSettings(length = 6)).flatMap { question ->
            question.melody.degrees.map(scale::indexOf).zipWithNext { a, b -> abs(b - a) }
        }
        val stepShare = moves.count { it == 1 }.toDouble() / moves.size
        val leapShare = moves.count { it >= 4 }.toDouble() / moves.size
        assertTrue("steps $stepShare", stepShare > 0.45)
        assertTrue("wide leaps $leapShare", leapShare < 0.15)
        // Every note of the scale still appears.
        assertEquals(scale.toSet(), generate(DictationSettings(length = 6)).flatMap { it.melody.degrees }.toSet())
    }

    @Test
    fun answersMustBeCompleteStartOnTheTonicAndStayInTheKey() {
        val question = DictationQuestion(cMajor, melody(ScaleDegree.TONIC, ScaleDegree.SECOND, ScaleDegree.THIRD), KeyContext.CADENCE)
        assertTrue(question.accepts(melody(ScaleDegree.TONIC, ScaleDegree.SECOND, ScaleDegree.THIRD)))
        assertTrue(question.accepts(melody(ScaleDegree.TONIC, ScaleDegree.FIFTH, ScaleDegree.SIXTH)))
        assertFalse(question.accepts(melody(ScaleDegree.TONIC, ScaleDegree.SECOND)))
        assertFalse(question.accepts(melody(ScaleDegree.SECOND, ScaleDegree.SECOND, ScaleDegree.THIRD)))
        assertFalse(question.accepts(melody(ScaleDegree.TONIC, ScaleDegree.FLAT_THIRD, ScaleDegree.THIRD)))
        assertEquals(Mode.MAJOR.diatonicDegrees, question.keypad)
    }

    @Test
    fun aWrongButWellFormedAnswerIsScoredOnce() {
        val question = DictationQuestion(cMajor, melody(ScaleDegree.TONIC, ScaleDegree.SECOND, ScaleDegree.THIRD), KeyContext.CADENCE)
        val heard = ExerciseEngine<Melody, DictationQuestion> { question }.start().promptPlayed(0)
        val answered = heard.submitAnswer(melody(ScaleDegree.TONIC, ScaleDegree.SECOND, ScaleDegree.FOURTH), 1_000)
        assertEquals(SessionScore(answered = 1, correct = 0), answered.score)
        // A malformed entry is ignored rather than scored.
        assertSame(heard, heard.submitAnswer(melody(ScaleDegree.TONIC), 1_000))
    }

    @Test
    fun promptSetsTheKeyThenPlaysTheMelodyInTheOctaveAboveTheTonic() {
        val question = DictationQuestion(Key(Pitch(57), Mode.MINOR), melody(ScaleDegree.TONIC, ScaleDegree.FLAT_THIRD, ScaleDegree.FIFTH), KeyContext.TONIC_CHORD)
        assertEquals(
            ListeningPrompt(
                moments = listOf(listOf(Pitch(57)), listOf(Pitch(60)), listOf(Pitch(64))),
                articulation = ListeningPrompt.Articulation.MELODIC,
                context = listOf(Key(Pitch(57), Mode.MINOR).tonicChord()),
            ),
            question.prompt,
        )
    }

    @Test
    fun questionsRejectMelodiesThatDoNotStartOnTheTonicOrLeaveTheKey() {
        assertThrows(IllegalArgumentException::class.java) {
            DictationQuestion(cMajor, melody(ScaleDegree.SECOND, ScaleDegree.THIRD), KeyContext.CADENCE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DictationQuestion(cMajor, melody(ScaleDegree.TONIC, ScaleDegree.FLAT_SEVENTH), KeyContext.CADENCE)
        }
    }

    @Test
    fun weakDegreesAppearMoreOften() {
        val favourSixth: (ScaleDegree) -> Double = { if (it == ScaleDegree.SIXTH) 10.0 else 0.2 }
        fun sixthShare(questions: List<DictationQuestion>) =
            questions.flatMap { it.melody.degrees.drop(1) }.let { notes -> notes.count { it == ScaleDegree.SIXTH }.toDouble() / notes.size }
        val weighted = sixthShare(generate(DictationSettings(), weights = favourSixth))
        val uniform = sixthShare(generate(DictationSettings()))
        assertTrue("weighted $weighted vs uniform $uniform", weighted > uniform * 2)
    }

    @Test
    fun settingsRulesAndAttempts() {
        val defaults = DictationSettings()
        assertSame(defaults, defaults.withModeToggled(Mode.MAJOR))
        assertThrows(IllegalArgumentException::class.java) { DictationSettings(length = 8) }

        val question = DictationQuestion(cMajor, melody(ScaleDegree.TONIC, ScaleDegree.THIRD), KeyContext.CADENCE)
        val answered = ExerciseEngine<Melody, DictationQuestion> { question }.start()
            .promptPlayed(1_000)
            .submitAnswer(melody(ScaleDegree.TONIC, ScaleDegree.THIRD), 4_000)
        assertEquals(
            DictationAttempt("s", 4_000, cMajor, question.melody, question.melody, true, 1, 3_000),
            DictationAttempt.from(answered, "s"),
        )
        assertEquals("1 – 3", question.melody.label)
        assertEquals("Do Mi", question.melody.solfege)
    }
}
