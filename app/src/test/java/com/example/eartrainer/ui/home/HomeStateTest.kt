package com.example.eartrainer.ui.home

import com.example.eartrainer.data.history.DayCount
import com.example.eartrainer.exercise.ExerciseKind
import com.example.eartrainer.ui.progress.AccuracyItem
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
    fun todaysAnswersAndTheStreakComeFromTheDays() {
        val state = HomeUiState.from(listOf(DayCount(today - 2, 4), DayCount(today - 1, 9), DayCount(today, 12)), today, 30, null, emptyMap())
        assertEquals(12, state.answersToday)
        assertEquals(3, state.currentStreak)
        assertEquals(18, state.answersToGoal)
        assertEquals(0.4f, state.goalProgress, 0.001f)
        // The goal ring stops at full.
        assertEquals(1f, HomeUiState.from(listOf(DayCount(today, 45)), today, 30, null, emptyMap()).goalProgress, 0f)
        assertEquals(0, HomeUiState.from(listOf(DayCount(today, 45)), today, 30, null, emptyMap()).answersToGoal)
        // The goal is whatever was chosen.
        assertEquals(38, HomeUiState.from(listOf(DayCount(today, 12)), today, 50, null, emptyMap()).answersToGoal)
    }

    @Test
    fun theSuggestionNamesTheWeakestWellPracticedItemsOfTheLastExercise() {
        val state = HomeUiState.from(emptyList(), today, 30, ExerciseKind.INTERVALS, mapOf(ExerciseKind.INTERVALS to intervals))
        // Minor second has too few answers to judge; octave is not weak.
        assertEquals(Suggestion(ExerciseKind.INTERVALS, listOf("Minor sixth", "Major seventh", "Tritone")), state.suggestion)
    }

    @Test
    fun noSuggestionWithoutWeakItemsOrRecentPractice() {
        assertNull(HomeUiState.from(emptyList(), today, 30, null, mapOf(ExerciseKind.INTERVALS to intervals)).suggestion)
        val strong = listOf(AccuracyItem("Octave", 10, 9))
        assertNull(HomeUiState.from(emptyList(), today, 30, ExerciseKind.INTERVALS, mapOf(ExerciseKind.INTERVALS to strong)).suggestion)
    }

    @Test
    fun aLabelFromTwoModesIsSuggestedOnce() {
        val progressions = listOf(AccuracyItem("V", 10, 3), AccuracyItem("V", 6, 2), AccuracyItem("IV", 5, 2))
        val state = HomeUiState.from(emptyList(), today, 30, ExerciseKind.PROGRESSIONS, mapOf(ExerciseKind.PROGRESSIONS to progressions))
        assertEquals(listOf("V", "IV"), state.suggestion?.items)
    }

    @Test
    fun everyExerciseIsSummarizedInMenuOrder() {
        val state = HomeUiState.from(emptyList(), today, 30, null, mapOf(ExerciseKind.INTERVALS to intervals))
        assertEquals(ExerciseKind.entries, state.exercises.map { it.exercise })
        assertEquals(ExerciseSummary(ExerciseKind.INTERVALS, 42, 23), state.exercises.first())
        assertNull(state.exercises.last().accuracy)
        assertEquals(true, state.exercises.first().needsWork)
    }
}
