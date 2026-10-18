package com.example.eartrainer.ui.home

import com.example.eartrainer.data.history.DayCount
import com.example.eartrainer.exercise.ExerciseKind
import com.example.eartrainer.ui.log.PracticeLogUiState
import com.example.eartrainer.ui.progress.AccuracyItem

/** Overall accuracy of one exercise, counted as on the Progress screen. */
data class ExerciseSummary(val exercise: ExerciseKind, val attempts: Int, val correct: Int) {
    val accuracy: Float? get() = if (attempts == 0) null else correct.toFloat() / attempts
    val needsWork: Boolean get() = accuracy?.let { it < AccuracyItem.WEAK_THRESHOLD } ?: false
}

/** What to practice next: the weakest items of the exercise practiced most recently. */
data class Suggestion(val exercise: ExerciseKind, val items: List<String>)

data class HomeUiState(
    val answersToday: Int,
    /** Answers a day that count as reaching today's goal. */
    val dailyGoal: Int,
    val currentStreak: Int,
    /** Every exercise, in menu order. */
    val exercises: List<ExerciseSummary>,
    val suggestion: Suggestion?,
) {
    val goalProgress: Float get() = (answersToday.toFloat() / dailyGoal).coerceAtMost(1f)
    val answersToGoal: Int get() = (dailyGoal - answersToday).coerceAtLeast(0)

    companion object {
        /** Fewer answers than this say too little about an item to call it weak. */
        const val MIN_ANSWERS_FOR_SUGGESTION = 3
        const val SUGGESTED_ITEMS = 3

        fun from(
            days: List<DayCount>,
            today: Long,
            dailyGoal: Int,
            lastExercise: ExerciseKind?,
            accuracy: Map<ExerciseKind, List<AccuracyItem>>,
        ): HomeUiState {
            val suggestion = lastExercise?.let { exercise ->
                val weakest = accuracy[exercise].orEmpty()
                    .filter { it.attempts >= MIN_ANSWERS_FOR_SUGGESTION && it.needsWork }
                    .sortedWith(compareBy<AccuracyItem> { it.accuracy }.thenByDescending { it.attempts })
                    .map { it.label }
                    // The same label can come from two modes, e.g. V in major and in minor.
                    .distinct()
                    .take(SUGGESTED_ITEMS)
                weakest.takeIf { it.isNotEmpty() }?.let { Suggestion(exercise, it) }
            }
            return HomeUiState(
                answersToday = days.firstOrNull { it.day == today }?.answers ?: 0,
                dailyGoal = dailyGoal,
                currentStreak = PracticeLogUiState.from(days, sessions = emptyList(), today = today).currentStreak,
                exercises = ExerciseKind.entries.map { exercise ->
                    val items = accuracy[exercise].orEmpty()
                    ExerciseSummary(exercise, items.sumOf { it.attempts }, items.sumOf { it.correct })
                },
                suggestion = suggestion,
            )
        }
    }
}
