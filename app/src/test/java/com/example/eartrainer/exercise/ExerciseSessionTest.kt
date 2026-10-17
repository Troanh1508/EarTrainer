package com.example.eartrainer.exercise

import com.example.eartrainer.music.Interval
import com.example.eartrainer.music.Pitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseSessionTest {

    private val choices = listOf(Interval.MINOR_THIRD, Interval.MAJOR_THIRD, Interval.PERFECT_FOURTH)
    private val question = IntervalQuestion(Pitch(60), Interval.MAJOR_THIRD, IntervalStyle.MELODIC_ASCENDING, choices)
    private val engine = ExerciseEngine<Interval, IntervalQuestion> { question }

    private fun heard() = engine.start().markPromptHeard()

    @Test
    fun correctnessComesFromTheQuestionNotTheChoicePosition() {
        assertTrue(question.isCorrect(Interval.MAJOR_THIRD))
        assertFalse(question.isCorrect(choices.first()))
        assertFalse(question.isCorrect(Interval.OCTAVE))
    }

    @Test
    fun answersAreIgnoredBeforeThePromptIsHeard() {
        val start = engine.start()
        assertSame(start, start.submitAnswer(Interval.MAJOR_THIRD))
    }

    @Test
    fun correctAnswerUpdatesScoreOnce() {
        val answered = heard().submitAnswer(Interval.MAJOR_THIRD)
        assertEquals(SessionScore(answered = 1, correct = 1), answered.score)
        assertEquals(ExercisePhase.Answered(Interval.MAJOR_THIRD, isCorrect = true), answered.phase)

        val again = answered.submitAnswer(Interval.MAJOR_THIRD).submitAnswer(Interval.MINOR_THIRD)
        assertEquals(SessionScore(answered = 1, correct = 1), again.score)
        assertEquals(answered, again)
    }

    @Test
    fun incorrectAnswerCountsAsAnsweredButNotCorrect() {
        val answered = heard().submitAnswer(Interval.PERFECT_FOURTH)
        assertEquals(SessionScore(answered = 1, correct = 0), answered.score)
        assertEquals(ExercisePhase.Answered(Interval.PERFECT_FOURTH, isCorrect = false), answered.phase)
    }

    @Test
    fun answerOutsideTheChoicesIsIgnored() {
        val state = heard()
        assertSame(state, state.submitAnswer(Interval.OCTAVE))
    }

    @Test
    fun hearingThePromptAgainDoesNotChangeTheQuestionOrScore() {
        val answered = heard().submitAnswer(Interval.MAJOR_THIRD)
        assertSame(answered, answered.markPromptHeard())
        val awaiting = heard()
        assertSame(awaiting, awaiting.markPromptHeard())
    }

    @Test
    fun advancingRequiresAnAnswer() {
        val state = heard()
        assertSame(state, engine.advance(state))
    }

    @Test
    fun advancingKeepsScoreAndResetsPhase() {
        val next = engine.advance(heard().submitAnswer(Interval.MAJOR_THIRD))
        assertEquals(2, next.questionNumber)
        assertEquals(ExercisePhase.Unheard, next.phase)
        assertEquals(SessionScore(answered = 1, correct = 1), next.score)
    }
}
