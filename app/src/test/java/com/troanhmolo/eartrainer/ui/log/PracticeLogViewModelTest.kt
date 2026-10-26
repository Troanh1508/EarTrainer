package com.troanhmolo.eartrainer.ui.log

import com.troanhmolo.eartrainer.exercise.IntervalAttempt
import com.troanhmolo.eartrainer.exercise.IntervalStyle
import com.troanhmolo.eartrainer.music.Interval
import com.troanhmolo.eartrainer.music.Pitch
import com.troanhmolo.eartrainer.testing.FakePracticeHistory
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
class PracticeLogViewModelTest {

    private val day = 86_400_000L
    private val utc = TimeZone.getTimeZone("UTC")

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun answer(session: String, at: Long) =
        IntervalAttempt(session, at, Interval.OCTAVE, IntervalStyle.HARMONIC, Pitch(60), Interval.OCTAVE, true, 1, 1_000)

    @Test
    fun summarizesPracticeUpToTheClocksToday() = runTest {
        val today = 20_744L
        val history = FakePracticeHistory()
        history.record(answer("a", (today - 2) * day + 1_000))
        history.record(answer("b", (today - 1) * day + 1_000))
        history.record(answer("b", (today - 1) * day + 2_000))
        val viewModel = PracticeLogViewModel(history, clock = { today * day + 3_600_000 }, timeZone = { utc })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val state = checkNotNull(viewModel.uiState.value)
        assertEquals(2, state.currentStreak)
        assertEquals(today, state.calendar.single { it.isToday }.day)
        assertEquals(listOf(1, 2), state.calendar.filter { it.answers > 0 }.map { it.answers })
        assertEquals(listOf("b", "a"), state.sessions.map { it.sessionId })
    }
}
