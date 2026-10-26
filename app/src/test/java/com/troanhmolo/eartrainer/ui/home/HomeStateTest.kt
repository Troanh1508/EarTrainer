package com.troanhmolo.eartrainer.ui.home

import com.troanhmolo.eartrainer.data.history.DayCount
import com.troanhmolo.eartrainer.data.history.DaySessions
import com.troanhmolo.eartrainer.exercise.ExerciseKind
import com.troanhmolo.eartrainer.ui.progress.AccuracyItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeStateTest {

    private val today = 20_744L

    private val intervals = listOf(
        AccuracyItem("Octave", 10, 9),
        AccuracyItem("Minor sixth", 10, 4),
        AccuracyItem("Tritone", 8, 4),
        AccuracyItem("Major seventh", 12, 6),
        AccuracyItem("Minor second", 2, 0),
        AccuracyItem("Major third", 0, 0),
    )

    @Test
    fun theGoalCountsSessionsFinishedTodayAndTheStreakAnyAnswers() {
        val days = listOf(DayCount(today - 2, 4), DayCount(today - 1, 9), DayCount(today, 32))
        val finished = listOf(DaySessions(today - 1, 3), DaySessions(today, 1))
        val state = HomeUiState.from(days, finished, today, 2, null, emptyMap())
        assertEquals(32, state.answersToday)
        assertEquals(1, state.sessionsToday)
        assertEquals(3, state.currentStreak)
        assertEquals(1, state.sessionsToGoal)
        assertEquals(0.5f, state.goalProgress, 0.001f)
        // The goal ring stops at full.
        val beyond = HomeUiState.from(days, listOf(DaySessions(today, 4)), today, 2, null, emptyMap())
        assertEquals(1f, beyond.goalProgress, 0f)
        assertEquals(0, beyond.sessionsToGoal)
        // Answers without a finished session make no progress towards the goal.
        assertEquals(0, HomeUiState.from(days, emptyList(), today, 1, null, emptyMap()).sessionsToday)
        // The goal is whatever was chosen.
        assertEquals(4, HomeUiState.from(days, finished, today, 5, null, emptyMap()).sessionsToGoal)
    }

    @Test
    fun theSuggestionNamesTheWeakestWellPracticedItemsOfTheLastExercise() {
        val state = HomeUiState.from(emptyList(), emptyList(), today, 2, ExerciseKind.INTERVALS, mapOf(ExerciseKind.INTERVALS to intervals))
        // Minor second has too few answers to judge; octave is not weak.
        assertEquals(Suggestion(ExerciseKind.INTERVALS, listOf("Minor sixth", "Major seventh", "Tritone")), state.suggestion)
    }

    @Test
    fun noSuggestionWithoutWeakItemsOrRecentPractice() {
        assertNull(HomeUiState.from(emptyList(), emptyList(), today, 2, null, mapOf(ExerciseKind.INTERVALS to intervals)).suggestion)
        val strong = listOf(AccuracyItem("Octave", 10, 9))
        assertNull(HomeUiState.from(emptyList(), emptyList(), today, 2, ExerciseKind.INTERVALS, mapOf(ExerciseKind.INTERVALS to strong)).suggestion)
    }

    @Test
    fun aLabelFromTwoModesIsSuggestedOnce() {
        val progressions = listOf(AccuracyItem("V", 10, 3), AccuracyItem("V", 6, 2), AccuracyItem("IV", 5, 2))
        val state = HomeUiState.from(emptyList(), emptyList(), today, 2, ExerciseKind.PROGRESSIONS, mapOf(ExerciseKind.PROGRESSIONS to progressions))
        assertEquals(listOf("V", "IV"), state.suggestion?.items)
    }

    @Test
    fun everyExerciseIsSummarizedInMenuOrder() {
        val state = HomeUiState.from(emptyList(), emptyList(), today, 2, null, mapOf(ExerciseKind.INTERVALS to intervals))
        assertEquals(ExerciseKind.entries, state.exercises.map { it.exercise })
        assertEquals(ExerciseSummary(ExerciseKind.INTERVALS, 42, 23), state.exercises.first())
        assertNull(state.exercises.last().accuracy)
        assertEquals(true, state.exercises.first().needsWork)
    }
}
