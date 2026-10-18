package com.example.eartrainer.ui.home

import com.example.eartrainer.data.settings.GoalSettingsStore
import com.example.eartrainer.exercise.ExerciseKind
import com.example.eartrainer.exercise.IntervalAttempt
import com.example.eartrainer.exercise.IntervalStyle
import com.example.eartrainer.music.Interval
import com.example.eartrainer.music.Pitch
import com.example.eartrainer.testing.FakePracticeHistory
import com.example.eartrainer.testing.InMemoryPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val day = 86_400_000L
    private val today = 20_744L

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    // Wrong answers are recorded as an octave.
    private fun answer(at: Long, interval: Interval, correct: Boolean) =
        IntervalAttempt("s", at, interval, IntervalStyle.HARMONIC, Pitch(60), if (correct) interval else Interval.OCTAVE, correct, 1, 1_000)

    @Test
    fun showsTodaysAnswersAndSuggestsTheWeakestIntervals() = runTest {
        val history = FakePracticeHistory()
        repeat(4) { index -> history.record(answer(today * day + index * 1_000L, Interval.MINOR_SIXTH, correct = index == 0)) }
        repeat(3) { index -> history.record(answer(today * day + 10_000L + index, Interval.PERFECT_FIFTH, correct = true)) }
        val goals = GoalSettingsStore(InMemoryPreferences())
        val viewModel = HomeViewModel(history, goals, clock = { today * day + 3_600_000 }, timeZone = { TimeZone.getTimeZone("UTC") })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val state = checkNotNull(viewModel.uiState.value)
        assertEquals(7, state.answersToday)
        assertEquals(1, state.currentStreak)
        assertEquals(Suggestion(ExerciseKind.INTERVALS, listOf("Minor sixth")), state.suggestion)
        assertEquals(ExerciseSummary(ExerciseKind.INTERVALS, 7, 4), state.exercises.first { it.exercise == ExerciseKind.INTERVALS })
        assertEquals(0, state.exercises.first { it.exercise == ExerciseKind.CHORDS }.attempts)
        assertEquals(30, state.dailyGoal)

        viewModel.onDailyGoalSelected(50)
        assertEquals(50, checkNotNull(viewModel.uiState.value).dailyGoal)
        assertEquals(43, checkNotNull(viewModel.uiState.value).answersToGoal)
        // Goals that are not offered are ignored.
        viewModel.onDailyGoalSelected(37)
        assertEquals(50, checkNotNull(viewModel.uiState.value).dailyGoal)
    }
}
