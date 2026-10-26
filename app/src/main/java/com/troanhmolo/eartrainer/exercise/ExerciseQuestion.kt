package com.troanhmolo.eartrainer.exercise

import com.troanhmolo.eartrainer.music.ListeningPrompt

/**
 * A single multiple-choice listening question. Interval, chord and scale-degree exercises
 * each provide their own implementation with their own answer type [A].
 *
 * Correctness is always judged against [correctAnswer], never against a label or position
 * shown in the UI.
 */
interface ExerciseQuestion<A> {
    val prompt: ListeningPrompt
    val choices: List<A>
    val correctAnswer: A

    fun isCorrect(answer: A): Boolean = answer == correctAnswer

    /**
     * Whether [answer] is a well-formed answer to this question; anything else is ignored rather
     * than scored. Multiple-choice questions accept their choices; exercises where the answer is
     * built up (like dictation) define their own rule.
     */
    fun accepts(answer: A): Boolean = answer in choices
}

/** Validates the invariants every generated question must satisfy. */
internal fun <A> requireValidChoices(choices: List<A>, correctAnswer: A) {
    require(choices.isNotEmpty()) { "A question needs answer choices" }
    require(correctAnswer in choices) { "Choices $choices must contain the correct answer $correctAnswer" }
    require(choices.toSet().size == choices.size) { "Choices must not contain duplicates: $choices" }
}
