package com.troanhmolo.eartrainer.ui.home

import com.troanhmolo.eartrainer.data.settings.GoalSettingsStore
import com.troanhmolo.eartrainer.exercise.ExerciseKind
import com.troanhmolo.eartrainer.exercise.IntervalAttempt
import com.troanhmolo.eartrainer.exercise.IntervalStyle
import com.troanhmolo.eartrainer.exercise.SESSION_LENGTH
import com.troanhmolo.eartrainer.music.Interval
import com.troanhmolo.eartrainer.music.Pitch
import com.troanhmolo.eartrainer.testing.FakePracticeHistory
import com.troanhmolo.eartrainer.testing.InMemoryPreferences
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
    private fun answer(at: Long, interval: Interval, correct: Boolean, session: String = "s") =
        IntervalAttempt(session, at, interval, IntervalStyle.HARMONIC, Pitch(60), if (correct) interval else Interval.OCTAVE, correct, 1, 1_000)

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
        assertEquals(2, state.dailyGoal)
        // Seven answers are not a finished session.
        assertEquals(0, state.sessionsToday)

        repeat(SESSION_LENGTH) { index -> history.record(answer(today * day + 20_000L + index, Interval.OCTAVE, correct = true, session = "full")) }
        assertEquals(1, checkNotNull(viewModel.uiState.value).sessionsToday)
        assertEquals(1, checkNotNull(viewModel.uiState.value).sessionsToGoal)

        viewModel.onDailyGoalSelected(3)
        assertEquals(3, checkNotNull(viewModel.uiState.value).dailyGoal)
        assertEquals(2, checkNotNull(viewModel.uiState.value).sessionsToGoal)
        // Goals that are not offered are ignored.
        viewModel.onDailyGoalSelected(30)
        assertEquals(3, checkNotNull(viewModel.uiState.value).dailyGoal)
    }
}
