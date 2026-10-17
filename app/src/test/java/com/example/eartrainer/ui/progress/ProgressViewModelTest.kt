package com.example.eartrainer.ui.progress

import com.example.eartrainer.exercise.IntervalAttempt
import com.example.eartrainer.exercise.IntervalStyle
import com.example.eartrainer.music.Interval
import com.example.eartrainer.music.Pitch
import com.example.eartrainer.testing.FakePracticeHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {

    private val history = FakePracticeHistory()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun attempt(session: String, at: Long, interval: Interval, style: IntervalStyle, correct: Boolean) =
        IntervalAttempt(session, at, interval, style, Pitch(60), if (correct) interval else Interval.OCTAVE, correct, 1, 1_000)

    @Test
    fun emptyHistoryShowsNoHistory() = runTest {
        val viewModel = ProgressViewModel(history)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val state = checkNotNull(viewModel.uiState.value)
        assertFalse(state.hasHistory)
        assertNull(state.overallAccuracy)
        assertEquals(Interval.entries, state.accuracy.map { it.interval })
    }

    @Test
    fun summarisesHistoryAndFiltersByStyle() = runTest {
        history.record(attempt("a", 1_000, Interval.MAJOR_THIRD, IntervalStyle.MELODIC_ASCENDING, correct = true))
        history.record(attempt("a", 2_000, Interval.MAJOR_THIRD, IntervalStyle.HARMONIC, correct = false))
        history.record(attempt("b", 9_000, Interval.OCTAVE, IntervalStyle.HARMONIC, correct = true))
        val viewModel = ProgressViewModel(history)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val all = checkNotNull(viewModel.uiState.value)
        assertTrue(all.hasHistory)
        assertEquals(3, all.totalAttempts)
        assertEquals(2, all.totalCorrect)
        assertEquals(listOf("b", "a"), all.recentSessions.map { it.sessionId })
        val thirds = all.accuracy.single { it.interval == Interval.MAJOR_THIRD }
        assertEquals(0.5f, thirds.accuracy!!, 0.0001f)

        viewModel.onStyleFilterSelected(IntervalStyle.HARMONIC)
        val harmonic = checkNotNull(viewModel.uiState.value)
        assertEquals(IntervalStyle.HARMONIC, harmonic.styleFilter)
        assertEquals(2, harmonic.totalAttempts)
        assertEquals(0f, harmonic.accuracy.single { it.interval == Interval.MAJOR_THIRD }.accuracy!!, 0.0001f)
        // Sessions are not filtered, so history is still reported as present.
        assertTrue(harmonic.hasHistory)
    }
}
