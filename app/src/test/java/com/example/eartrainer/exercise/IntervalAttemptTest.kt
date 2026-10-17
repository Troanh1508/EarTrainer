package com.example.eartrainer.exercise

import com.example.eartrainer.music.Interval
import com.example.eartrainer.music.Pitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntervalAttemptTest {

    private val question = IntervalQuestion(
        Pitch(55), Interval.PERFECT_FIFTH, IntervalStyle.HARMONIC,
        listOf(Interval.PERFECT_FOURTH, Interval.TRITONE, Interval.PERFECT_FIFTH),
    )
    private val start = ExerciseEngine<Interval, IntervalQuestion> { question }.start()

    @Test
    fun unansweredQuestionsProduceNoAttempt() {
        assertNull(IntervalAttempt.from(start, "s"))
        assertNull(IntervalAttempt.from(start.promptPlayed(10), "s"))
    }

    @Test
    fun attemptCapturesQuestionAnswerReplaysAndResponseTime() {
        val answered = start
            .promptPlayed(atMillis = 10_000)
            .promptPlayed(atMillis = 12_000)
            .submitAnswer(Interval.TRITONE, atMillis = 14_500)

        assertEquals(
            IntervalAttempt(
                sessionId = "session-1",
                answeredAtMillis = 14_500,
                interval = Interval.PERFECT_FIFTH,
                style = IntervalStyle.HARMONIC,
                lowerPitch = Pitch(55),
                answer = Interval.TRITONE,
                isCorrect = false,
                playCount = 2,
                responseMillis = 4_500,
            ),
            IntervalAttempt.from(answered, "session-1"),
        )
    }
}
