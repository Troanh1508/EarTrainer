package com.troanhmolo.eartrainer.ui.practice

import com.troanhmolo.eartrainer.exercise.ExercisePhase
import com.troanhmolo.eartrainer.exercise.ExerciseQuestion
import com.troanhmolo.eartrainer.exercise.SESSION_LENGTH
import com.troanhmolo.eartrainer.exercise.ExerciseState
import com.troanhmolo.eartrainer.music.Pitch

enum class PlaybackStatus { Idle, Playing, Failed }

/** How an answer button should look; derived here so composables contain no exercise logic. */
enum class ChoiceAppearance { Available, Disabled, SelectedCorrect, SelectedIncorrect, RevealedCorrect, Dimmed }

data class PracticeUiState<A, Q : ExerciseQuestion<A>>(
    val exercise: ExerciseState<A, Q>,
    val playback: PlaybackStatus = PlaybackStatus.Idle,
    /** This session's answered questions in order. */
    val answered: List<AnsweredQuestion<A, Q>> = emptyList(),
    /** Questions in this session. */
    val sessionLength: Int = SESSION_LENGTH,
    /** The results, once the last question has been answered and the listener moved on. */
    val summary: PracticeSummary? = null,
) {
    /** This session's answers in order, true where right; feeds the session strip. */
    val results: List<Boolean> get() = answered.map { it.isCorrect }
    val isLastQuestion: Boolean get() = exercise.questionNumber >= sessionLength

    val question: Q get() = exercise.question
    val isPlaying: Boolean get() = playback == PlaybackStatus.Playing
    val hasHeardPrompt: Boolean get() = exercise.phase != ExercisePhase.Unheard
    val canAnswer: Boolean get() = exercise.phase == ExercisePhase.AwaitingAnswer

    @Suppress("UNCHECKED_CAST") // Answered<A> is the only Answered type an ExerciseState<A, Q> holds.
    val answer: ExercisePhase.Answered<A>? get() = exercise.phase as? ExercisePhase.Answered<A>
    val canAdvance: Boolean get() = answer != null

    /** From first hearing the question to answering it, once answered. */
    val responseMillis: Long?
        get() = answer?.let { answered -> exercise.firstPlayedAtMillis?.let { answered.answeredAtMillis - it } }

    fun appearanceOf(choice: A): ChoiceAppearance {
        val answer = answer ?: return if (canAnswer) ChoiceAppearance.Available else ChoiceAppearance.Disabled
        return when {
            choice == answer.selectedAnswer && answer.isCorrect -> ChoiceAppearance.SelectedCorrect
            choice == answer.selectedAnswer -> ChoiceAppearance.SelectedIncorrect
            choice == question.correctAnswer -> ChoiceAppearance.RevealedCorrect
            else -> ChoiceAppearance.Dimmed
        }
    }

    /** The notes in the order they sounded, e.g. "C4 → E4" or "C4 + E4 + G4", named by [nameOf]. */
    fun notesAsPlayed(nameOf: (Pitch) -> String = { it.name }): String =
        question.prompt.moments.joinToString(" → ") { moment -> moment.joinToString(" + ", transform = nameOf) }
}
