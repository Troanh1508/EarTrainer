package com.example.eartrainer.ui.practice

import com.example.eartrainer.exercise.ExercisePhase
import com.example.eartrainer.exercise.ExerciseQuestion
import com.example.eartrainer.exercise.ExerciseState

enum class PlaybackStatus { Idle, Playing, Failed }

/** How an answer button should look; derived here so composables contain no exercise logic. */
enum class ChoiceAppearance { Available, Disabled, SelectedCorrect, SelectedIncorrect, RevealedCorrect, Dimmed }

data class PracticeUiState<A, Q : ExerciseQuestion<A>>(
    val exercise: ExerciseState<A, Q>,
    val playback: PlaybackStatus = PlaybackStatus.Idle,
) {
    val question: Q get() = exercise.question
    val isPlaying: Boolean get() = playback == PlaybackStatus.Playing
    val hasHeardPrompt: Boolean get() = exercise.phase != ExercisePhase.Unheard
    val canAnswer: Boolean get() = exercise.phase == ExercisePhase.AwaitingAnswer

    @Suppress("UNCHECKED_CAST") // Answered<A> is the only Answered type an ExerciseState<A, Q> holds.
    val answer: ExercisePhase.Answered<A>? get() = exercise.phase as? ExercisePhase.Answered<A>
    val canAdvance: Boolean get() = answer != null

    fun appearanceOf(choice: A): ChoiceAppearance {
        val answer = answer ?: return if (canAnswer) ChoiceAppearance.Available else ChoiceAppearance.Disabled
        return when {
            choice == answer.selectedAnswer && answer.isCorrect -> ChoiceAppearance.SelectedCorrect
            choice == answer.selectedAnswer -> ChoiceAppearance.SelectedIncorrect
            choice == question.correctAnswer -> ChoiceAppearance.RevealedCorrect
            else -> ChoiceAppearance.Dimmed
        }
    }

    /** The notes in the order they sounded, e.g. "C4 → E4" or "C4 + E4 + G4". */
    val notesAsPlayed: String
        get() = question.prompt.moments.joinToString(" → ") { moment -> moment.joinToString(" + ") { it.name } }
}
