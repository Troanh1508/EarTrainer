package com.example.eartrainer.exercise

import com.example.eartrainer.music.Interval
import com.example.eartrainer.music.Pitch

/** One answered interval question, as stored in practice history. */
data class IntervalAttempt(
    val sessionId: String,
    val answeredAtMillis: Long,
    val interval: Interval,
    val style: IntervalStyle,
    val lowerPitch: Pitch,
    val answer: Interval,
    val isCorrect: Boolean,
    /** Times the prompt was started before answering; 1 means no replays. */
    val playCount: Int,
    /** From the first time the prompt started playing until the answer. */
    val responseMillis: Long,
) {
    companion object {
        /** The attempt for [state]'s current question, or null if it has not been answered. */
        fun from(state: ExerciseState<Interval, IntervalQuestion>, sessionId: String): IntervalAttempt? {
            val answered = state.phase as? ExercisePhase.Answered<Interval> ?: return null
            val firstPlayed = state.firstPlayedAtMillis ?: answered.answeredAtMillis
            return IntervalAttempt(
                sessionId = sessionId,
                answeredAtMillis = answered.answeredAtMillis,
                interval = state.question.interval,
                style = state.question.style,
                lowerPitch = state.question.lowerPitch,
                answer = answered.selectedAnswer,
                isCorrect = answered.isCorrect,
                playCount = state.playCount,
                responseMillis = (answered.answeredAtMillis - firstPlayed).coerceAtLeast(0),
            )
        }
    }
}
