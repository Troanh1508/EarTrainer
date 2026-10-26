package com.troanhmolo.eartrainer.exercise

import com.troanhmolo.eartrainer.music.Interval
import com.troanhmolo.eartrainer.music.Pitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseSessionTest {

    private val choices = listOf(Interval.MINOR_THIRD, Interval.MAJOR_THIRD, Interval.PERFECT_FOURTH)
    private val question = IntervalQuestion(Pitch(60), Interval.MAJOR_THIRD, IntervalStyle.MELODIC_ASCENDING, choices)
    private val engine = ExerciseEngine<Interval, IntervalQuestion> { question }

    private fun heard() = engine.start().promptPlayed(atMillis = 1_000)

    @Test
    fun correctnessComesFromTheQuestionNotTheChoicePosition() {
        assertTrue(question.isCorrect(Interval.MAJOR_THIRD))
        assertFalse(question.isCorrect(choices.first()))
        assertFalse(question.isCorrect(Interval.OCTAVE))
    }

    @Test
    fun answersAreIgnoredBeforeThePromptIsHeard() {
        val start = engine.start()
        assertSame(start, start.submitAnswer(Interval.MAJOR_THIRD, atMillis = 2_000))
    }

    @Test
    fun correctAnswerUpdatesScoreOnce() {
        val answered = heard().submitAnswer(Interval.MAJOR_THIRD, atMillis = 2_000)
        assertEquals(SessionScore(answered = 1, correct = 1), answered.score)
        assertEquals(ExercisePhase.Answered(Interval.MAJOR_THIRD, isCorrect = true, answeredAtMillis = 2_000), answered.phase)

        val again = answered.submitAnswer(Interval.MAJOR_THIRD, 3_000).submitAnswer(Interval.MINOR_THIRD, 4_000)
        assertSame(answered, again)
    }

    @Test
    fun incorrectAnswerCountsAsAnsweredButNotCorrect() {
        val answered = heard().submitAnswer(Interval.PERFECT_FOURTH, atMillis = 2_000)
        assertEquals(SessionScore(answered = 1, correct = 0), answered.score)
        assertFalse((answered.phase as ExercisePhase.Answered).isCorrect)
    }

    @Test
    fun answerOutsideTheChoicesIsIgnored() {
        val state = heard()
        assertSame(state, state.submitAnswer(Interval.OCTAVE, atMillis = 2_000))
    }

    @Test
    fun replaysCountPlaysButNeverChangePhaseOrScore() {
        val awaiting = heard().promptPlayed(atMillis = 1_500).promptPlayed(atMillis = 1_800)
        assertEquals(ExercisePhase.AwaitingAnswer, awaiting.phase)
        assertEquals(3, awaiting.playCount)
        assertEquals(1_000L, awaiting.firstPlayedAtMillis)

        val answered = awaiting.submitAnswer(Interval.MAJOR_THIRD, atMillis = 2_000)
        val replayedAfter = answered.promptPlayed(atMillis = 2_500)
        assertEquals(answered.phase, replayedAfter.phase)
        assertEquals(answered.score, replayedAfter.score)
        assertEquals(answered.question, replayedAfter.question)
    }

    @Test
    fun advancingRequiresAnAnswer() {
        val state = heard()
        assertSame(state, engine.advance(state))
    }

    @Test
    fun advancingKeepsScoreAndResetsQuestionTracking() {
        val next = engine.advance(heard().promptPlayed(1_200).submitAnswer(Interval.MAJOR_THIRD, 2_000))
        assertEquals(2, next.questionNumber)
        assertEquals(ExercisePhase.Unheard, next.phase)
        assertEquals(SessionScore(answered = 1, correct = 1), next.score)
        assertEquals(0, next.playCount)
        assertNull(next.firstPlayedAtMillis)
    }
}
