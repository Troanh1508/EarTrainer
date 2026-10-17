package com.example.eartrainer.exercise

import com.example.eartrainer.music.ChordInversion
import com.example.eartrainer.music.ChordQuality
import com.example.eartrainer.music.ListeningPrompt
import com.example.eartrainer.music.Pitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChordQuestionGeneratorTest {

    private val everything = ChordExerciseSettings(
        qualities = ChordQuality.entries,
        styles = ChordStyle.entries.toSet(),
        includeInversions = true,
    )

    private fun generate(settings: ChordExerciseSettings, count: Int = 2_000, seed: Int = 7) =
        ChordQuestionGenerator(settings, Random(seed)).let { generator -> List(count) { generator.generate() } }

    @Test
    fun everySoundedPitchStaysInRange() {
        val settings = everything.copy(pitchRange = 50..70)
        generate(settings).forEach { question ->
            question.pitches.forEach { assertTrue("$it outside ${settings.pitchRange}", it.midi in settings.pitchRange) }
        }
    }

    @Test
    fun qualityQuestionsOfferEveryEnabledQualityAndCountTheRightOne() {
        val settings = ChordExerciseSettings(qualities = listOf(ChordQuality.MAJOR, ChordQuality.MINOR_SEVENTH, ChordQuality.AUGMENTED))
        generate(settings, count = 300).forEach { question ->
            assertEquals(settings.qualities.map { ChordAnswer.Quality(it) }, question.choices)
            assertEquals(ChordAnswer.Quality(question.quality), question.correctAnswer)
            assertEquals(question.choices.size, question.choices.toSet().size)
        }
    }

    @Test
    fun inversionsAppearOnlyWhenIncluded() {
        assertTrue(generate(ChordExerciseSettings(), count = 300).all { it.inversion == ChordInversion.ROOT_POSITION })
        val withInversions = generate(everything)
        assertEquals(ChordInversion.entries.toSet(), withInversions.map { it.inversion }.toSet())
    }

    @Test
    fun inversionQuestionsOfferTheInversionsThatChordCanTake() {
        val settings = everything.copy(target = ChordTarget.INVERSION, includeInversions = false)
        val questions = generate(settings)
        questions.forEach { question ->
            assertEquals(question.quality.inversions.map { ChordAnswer.Inversion(it) }, question.choices)
            assertEquals(ChordAnswer.Inversion(question.inversion), question.correctAnswer)
        }
        // Naming inversions always uses them, regardless of the include-inversions switch.
        assertEquals(ChordInversion.entries.toSet(), questions.map { it.inversion }.toSet())
    }

    @Test
    fun stylesProduceBlockOrArpeggiatedPrompts() {
        val question = ChordQuestion(
            Pitch(64), ChordQuality.MAJOR, ChordInversion.FIRST, ChordStyle.BLOCK, ChordTarget.QUALITY,
            listOf(ChordAnswer.Quality(ChordQuality.MAJOR), ChordAnswer.Quality(ChordQuality.MINOR)),
        )
        val pitches = listOf(Pitch(64), Pitch(67), Pitch(72))
        assertEquals(pitches, question.pitches)
        assertEquals(ListeningPrompt.simultaneous(pitches), question.prompt)
        assertEquals(ListeningPrompt.arpeggiated(pitches), question.copy(style = ChordStyle.ARPEGGIATED).prompt)
        assertEquals("C", question.rootName)
    }

    @Test
    fun everyQualityStyleAndInversionIsEventuallyAsked() {
        val questions = generate(everything)
        assertEquals(ChordQuality.entries.toSet(), questions.map { it.quality }.toSet())
        assertEquals(ChordStyle.entries.toSet(), questions.map { it.style }.toSet())
    }

    @Test
    fun settingsRulesKeepAtLeastTwoQualitiesAndOneStyle() {
        val two = ChordExerciseSettings(qualities = listOf(ChordQuality.MAJOR, ChordQuality.MINOR))
        assertSame(two, two.withQualityToggled(ChordQuality.MAJOR))
        assertEquals(
            listOf(ChordQuality.MAJOR, ChordQuality.MINOR, ChordQuality.DOMINANT_SEVENTH),
            two.withQualityToggled(ChordQuality.DOMINANT_SEVENTH).qualities,
        )
        assertSame(two, two.withStyleToggled(ChordStyle.BLOCK))
        assertEquals(ChordStyle.entries.toSet(), two.withStyleToggled(ChordStyle.ARPEGGIATED).styles)
        assertThrows(IllegalArgumentException::class.java) { ChordExerciseSettings(qualities = listOf(ChordQuality.MAJOR)) }
        assertThrows(IllegalArgumentException::class.java) { ChordExerciseSettings(pitchRange = 60..70) }
    }

    @Test
    fun questionRejectsImpossibleInversionsAndBadChoices() {
        val choices = listOf(ChordAnswer.Quality(ChordQuality.MAJOR), ChordAnswer.Quality(ChordQuality.MINOR))
        assertThrows(IllegalArgumentException::class.java) {
            ChordQuestion(Pitch(60), ChordQuality.MAJOR, ChordInversion.THIRD, ChordStyle.BLOCK, ChordTarget.QUALITY, choices)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChordQuestion(Pitch(60), ChordQuality.AUGMENTED, ChordInversion.ROOT_POSITION, ChordStyle.BLOCK, ChordTarget.QUALITY, choices)
        }
    }

    @Test
    fun attemptRecordsTheQuestionAndAnswer() {
        val question = generate(everything, count = 1).single()
        val wrong = question.choices.first { it != question.correctAnswer }
        val answered = ExerciseEngine<ChordAnswer, ChordQuestion> { question }.start()
            .promptPlayed(1_000)
            .submitAnswer(wrong, 4_000)
        val attempt = checkNotNull(ChordAttempt.from(answered, "s"))
        assertEquals(question.quality, attempt.quality)
        assertEquals(question.inversion, attempt.inversion)
        assertEquals(question.target, attempt.target)
        assertEquals(wrong, attempt.answer)
        assertEquals(false, attempt.isCorrect)
        assertEquals(3_000L, attempt.responseMillis)
    }
}
