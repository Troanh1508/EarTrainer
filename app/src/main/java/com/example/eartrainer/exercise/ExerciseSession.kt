package com.example.eartrainer.exercise

/** Where the current question is in its lifecycle. Playback status is tracked separately. */
sealed interface ExercisePhase<out A> {
    /** A new question whose prompt has not been played yet; answers are not accepted. */
    data object Unheard : ExercisePhase<Nothing>

    /** The prompt has been played at least once and an answer can be submitted. */
    data object AwaitingAnswer : ExercisePhase<Nothing>

    /** An answer was submitted. The question is locked until the session advances. */
    data class Answered<A>(
        val selectedAnswer: A,
        val isCorrect: Boolean,
        val answeredAtMillis: Long,
    ) : ExercisePhase<A>
}

data class SessionScore(val answered: Int = 0, val correct: Int = 0) {
    fun record(isCorrect: Boolean): SessionScore =
        SessionScore(answered = answered + 1, correct = correct + if (isCorrect) 1 else 0)
}

/**
 * Immutable snapshot of an exercise session. Transitions that are not valid in the current
 * phase return the same state unchanged, so duplicate taps or out-of-order events are no-ops
 * instead of corrupting the score.
 *
 * Times are passed in by the caller rather than read from a clock, keeping this class pure.
 */
data class ExerciseState<A, Q : ExerciseQuestion<A>>(
    val question: Q,
    /** 1-based; also identifies the question so stale async results can be discarded. */
    val questionNumber: Int,
    val phase: ExercisePhase<A>,
    val score: SessionScore,
    /** How many times the current question's prompt has been started, including replays. */
    val playCount: Int = 0,
    val firstPlayedAtMillis: Long? = null,
) {
    /** Records that the prompt started playing. Replays never touch the phase or score. */
    fun promptPlayed(atMillis: Long): ExerciseState<A, Q> = copy(
        phase = if (phase == ExercisePhase.Unheard) ExercisePhase.AwaitingAnswer else phase,
        playCount = playCount + 1,
        firstPlayedAtMillis = firstPlayedAtMillis ?: atMillis,
    )

    fun submitAnswer(answer: A, atMillis: Long): ExerciseState<A, Q> {
        if (phase != ExercisePhase.AwaitingAnswer || !question.accepts(answer)) return this
        val isCorrect = question.isCorrect(answer)
        return copy(
            phase = ExercisePhase.Answered(answer, isCorrect, atMillis),
            score = score.record(isCorrect),
        )
    }
}

/** Creates sessions and moves them on to new questions supplied by [nextQuestion]. */
class ExerciseEngine<A, Q : ExerciseQuestion<A>>(
    private val nextQuestion: () -> Q,
) {
    fun start(): ExerciseState<A, Q> = ExerciseState(
        question = nextQuestion(),
        questionNumber = 1,
        phase = ExercisePhase.Unheard,
        score = SessionScore(),
    )

    /** Moves to a fresh question, keeping the score. Only allowed once the current one is answered. */
    fun advance(state: ExerciseState<A, Q>): ExerciseState<A, Q> {
        if (state.phase !is ExercisePhase.Answered) return state
        return state.copy(
            question = nextQuestion(),
            questionNumber = state.questionNumber + 1,
            phase = ExercisePhase.Unheard,
            playCount = 0,
            firstPlayedAtMillis = null,
        )
    }
}
