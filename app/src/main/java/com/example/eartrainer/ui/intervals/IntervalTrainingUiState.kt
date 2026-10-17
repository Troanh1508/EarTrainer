package com.example.eartrainer.ui.intervals

import com.example.eartrainer.exercise.ExercisePhase
import com.example.eartrainer.exercise.ExerciseState
import com.example.eartrainer.exercise.IntervalQuestion
import com.example.eartrainer.music.Interval

typealias IntervalExerciseState = ExerciseState<Interval, IntervalQuestion>

enum class PlaybackStatus { Idle, Playing, Failed }

/** How an answer button should look; derived here so composables contain no exercise logic. */
enum class ChoiceAppearance { Available, Disabled, SelectedCorrect, SelectedIncorrect, RevealedCorrect, Dimmed }

data class IntervalTrainingUiState(
    val exercise: IntervalExerciseState,
    val playback: PlaybackStatus = PlaybackStatus.Idle,
) {
    val question: IntervalQuestion get() = exercise.question
    val isPlaying: Boolean get() = playback == PlaybackStatus.Playing
    val hasHeardPrompt: Boolean get() = exercise.phase != ExercisePhase.Unheard
    val canAnswer: Boolean get() = exercise.phase == ExercisePhase.AwaitingAnswer
    val answer: ExercisePhase.Answered<Interval>? get() = exercise.phase as? ExercisePhase.Answered<Interval>
    val canAdvance: Boolean get() = answer != null

    fun appearanceOf(choice: Interval): ChoiceAppearance {
        val answer = answer ?: return if (canAnswer) ChoiceAppearance.Available else ChoiceAppearance.Disabled
        return when {
            choice == answer.selectedAnswer && answer.isCorrect -> ChoiceAppearance.SelectedCorrect
            choice == answer.selectedAnswer -> ChoiceAppearance.SelectedIncorrect
            choice == question.correctAnswer -> ChoiceAppearance.RevealedCorrect
            else -> ChoiceAppearance.Dimmed
        }
    }

    /** The notes in the order they sounded, e.g. "C4 → E4" or "C4 + E4". */
    val notesAsPlayed: String
        get() = question.prompt.moments.joinToString(" → ") { moment -> moment.joinToString(" + ") { it.name } }
}
