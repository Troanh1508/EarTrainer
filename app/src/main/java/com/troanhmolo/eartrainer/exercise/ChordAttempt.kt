package com.troanhmolo.eartrainer.exercise

import com.troanhmolo.eartrainer.music.ChordInversion
import com.troanhmolo.eartrainer.music.ChordQuality
import com.troanhmolo.eartrainer.music.Pitch

/** One answered chord question, as stored in practice history. */
data class ChordAttempt(
    val sessionId: String,
    val answeredAtMillis: Long,
    val quality: ChordQuality,
    val inversion: ChordInversion,
    val style: ChordStyle,
    val target: ChordTarget,
    val bass: Pitch,
    val answer: ChordAnswer,
    val isCorrect: Boolean,
    /** Times the prompt was started before answering; 1 means no replays. */
    val playCount: Int,
    /** From the first time the prompt started playing until the answer. */
    val responseMillis: Long,
) {
    companion object {
        /** The attempt for [state]'s current question, or null if it has not been answered. */
        fun from(state: ExerciseState<ChordAnswer, ChordQuestion>, sessionId: String): ChordAttempt? {
            val answered = state.phase as? ExercisePhase.Answered<ChordAnswer> ?: return null
            val question = state.question
            val firstPlayed = state.firstPlayedAtMillis ?: answered.answeredAtMillis
            return ChordAttempt(
                sessionId = sessionId,
                answeredAtMillis = answered.answeredAtMillis,
                quality = question.quality,
                inversion = question.inversion,
                style = question.style,
                target = question.target,
                bass = question.bass,
                answer = answered.selectedAnswer,
                isCorrect = answered.isCorrect,
                playCount = state.playCount,
                responseMillis = (answered.answeredAtMillis - firstPlayed).coerceAtLeast(0),
            )
        }
    }
}
