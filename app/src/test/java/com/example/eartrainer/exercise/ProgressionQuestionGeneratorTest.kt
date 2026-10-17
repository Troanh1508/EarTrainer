package com.example.eartrainer.exercise

import com.example.eartrainer.music.ChordProgression
import com.example.eartrainer.music.DiatonicChord
import com.example.eartrainer.music.Key
import com.example.eartrainer.music.ListeningPrompt
import com.example.eartrainer.music.Mode
import com.example.eartrainer.music.Pitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ProgressionQuestionGeneratorTest {

    private fun generate(settings: ProgressionSettings, count: Int = 500, seed: Int = 11) =
        ProgressionQuestionGenerator(settings, Random(seed)).let { generator -> List(count) { generator.generate() } }

    private fun ChordProgression.distanceTo(other: ChordProgression) = chords.indices.count { chords[it] != other.chords[it] }

    @Test
    fun catalogStartsOnTheTonicNeverRepeatsAChordAndUsesOnlyTheVocabulary() {
        ChordVocabulary.entries.forEach { vocabulary ->
            ProgressionSettings.LENGTH_OPTIONS.forEach { length ->
                val all = ProgressionQuestionGenerator.allProgressions(Mode.MAJOR, vocabulary, length)
                val expectedCount = (vocabulary.steps.size - 1).let { others -> Math.pow(others.toDouble(), (length - 1).toDouble()).toInt() }
                assertEquals("$vocabulary/$length", expectedCount, all.size)
                all.forEach { progression ->
                    assertEquals(length, progression.chords.size)
                    assertEquals(1, progression.chords.first().step)
                    assertTrue(progression.chords.zipWithNext().none { (a, b) -> a == b })
                    assertTrue(progression.chords.all { it.step in vocabulary.steps })
                }
            }
        }
    }

    @Test
    fun everyQuestionHasFourDistinctChoicesIncludingTheAnswer() {
        val settings = ProgressionSettings(modes = Mode.entries.toSet(), vocabulary = ChordVocabulary.ALL, length = 5)
        listOf(settings, settings.copy(source = ProgressionSource.RANDOM)).forEach { each ->
            generate(each).forEach { question ->
                assertEquals(ProgressionSettings.CHOICE_COUNT, question.choices.size)
                assertEquals(question.choices.size, question.choices.toSet().size)
                assertTrue(question.progression in question.choices)
                assertTrue(question.choices.all { it.mode == question.key.mode })
            }
        }
    }

    @Test
    fun commonSourceAsksOnlyCommonProgressionsThatFitTheVocabulary() {
        val settings = ProgressionSettings(modes = Mode.entries.toSet(), vocabulary = ChordVocabulary.COMMON, length = 4)
        generate(settings).forEach { question ->
            val fitting = ProgressionQuestionGenerator.commonProgressions(question.key.mode, ChordVocabulary.COMMON, 4)
            assertTrue(question.progression.label, question.progression in fitting)
            // Plenty fit, so every alternative is a common progression too.
            assertTrue(question.choices.all { it in fitting })
        }
    }

    @Test
    fun commonSourceFillsChoicesFromRandomWhenTooFewFit() {
        // i, iv and V allow only two common four-chord progressions in minor.
        val settings = ProgressionSettings(modes = setOf(Mode.MINOR), vocabulary = ChordVocabulary.PRIMARY, length = 4)
        val fitting = ProgressionQuestionGenerator.commonProgressions(Mode.MINOR, ChordVocabulary.PRIMARY, 4)
        assertEquals(2, fitting.size)
        generate(settings, count = 100).forEach { question ->
            assertTrue(question.progression in fitting)
            assertEquals(ProgressionSettings.CHOICE_COUNT, question.choices.toSet().size)
            assertTrue(question.choices.containsAll(fitting))
        }
    }

    @Test
    fun randomSourceCanAskAnyAllowedProgression() {
        val settings = ProgressionSettings(vocabulary = ChordVocabulary.PRIMARY, length = 4, source = ProgressionSource.RANDOM)
        val asked = generate(settings).map { it.progression }.toSet()
        assertEquals(ProgressionQuestionGenerator.allProgressions(Mode.MAJOR, ChordVocabulary.PRIMARY, 4).toSet(), asked)
    }

    @Test
    fun distractorsAreTheClosestAlternatives() {
        // With all seven chords there are always many random progressions one chord away.
        generate(ProgressionSettings(vocabulary = ChordVocabulary.ALL, source = ProgressionSource.RANDOM)).forEach { question ->
            (question.choices - question.progression).forEach { assertEquals(1, it.distanceTo(question.progression)) }
        }
    }

    @Test
    fun smallestVocabularyStillFillsEveryChoice() {
        // I-IV-V with three chords allows exactly four progressions: all of them are offered.
        val settings = ProgressionSettings(vocabulary = ChordVocabulary.PRIMARY, length = 3)
        generate(settings, count = 50).forEach { question ->
            assertEquals(ProgressionQuestionGenerator.allProgressions(Mode.MAJOR, ChordVocabulary.PRIMARY, 3).toSet(), question.choices.toSet())
        }
    }

    @Test
    fun answerPositionVariesAcrossQuestions() {
        val positions = generate(ProgressionSettings()).map { it.choices.indexOf(it.progression) }.toSet()
        assertEquals((0 until ProgressionSettings.CHOICE_COUNT).toSet(), positions)
    }

    @Test
    fun promptPlaysTheVoicedProgressionFlowing() {
        val key = Key(Pitch(62), Mode.MAJOR)
        val progression = ChordProgression(listOf(1, 4, 5, 1).map { DiatonicChord(Mode.MAJOR, it) })
        val options = ProgressionQuestionGenerator.allProgressions(Mode.MAJOR, ChordVocabulary.PRIMARY, 4)
        val question = ProgressionQuestion(key, progression, listOf(progression) + (options - progression).take(3))
        assertEquals(ListeningPrompt(progression.voicedIn(key), ListeningPrompt.Articulation.FLOWING), question.prompt)
    }

    @Test
    fun keysFollowTheKeyChoice() {
        assertTrue(generate(ProgressionSettings(keyChoice = KeyChoice.C_ONLY), count = 100).all { it.key.tonic == Pitch(60) })
        assertTrue(generate(ProgressionSettings()).all { it.key.tonic.midi in KeyChoice.TONIC_RANGE })
    }

    @Test
    fun settingsRules() {
        val defaults = ProgressionSettings()
        assertSame(defaults, defaults.withModeToggled(Mode.MAJOR))
        assertEquals(Mode.entries.toSet(), defaults.withModeToggled(Mode.MINOR).modes)
        assertThrows(IllegalArgumentException::class.java) { ProgressionSettings(length = 6) }
        assertThrows(IllegalArgumentException::class.java) { ProgressionSettings(modes = emptySet()) }
    }

    @Test
    fun attemptRecordsKeyProgressionAndAnswer() {
        val question = generate(ProgressionSettings(), count = 1).single()
        val wrong = question.choices.first { it != question.progression }
        val answered = ExerciseEngine<ChordProgression, ProgressionQuestion> { question }.start()
            .promptPlayed(1_000)
            .submitAnswer(wrong, 7_000)
        assertEquals(
            ProgressionAttempt("s", 7_000, question.key, question.progression, wrong, false, 1, 6_000),
            ProgressionAttempt.from(answered, "s"),
        )
    }
}
