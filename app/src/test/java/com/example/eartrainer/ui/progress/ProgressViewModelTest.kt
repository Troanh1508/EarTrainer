package com.example.eartrainer.ui.progress

import com.example.eartrainer.exercise.ChordAnswer
import com.example.eartrainer.exercise.ChordAttempt
import com.example.eartrainer.exercise.ChordStyle
import com.example.eartrainer.exercise.ChordTarget
import com.example.eartrainer.exercise.ExerciseKind
import com.example.eartrainer.exercise.IntervalAttempt
import com.example.eartrainer.exercise.IntervalStyle
import com.example.eartrainer.music.ChordInversion
import com.example.eartrainer.music.ChordQuality
import com.example.eartrainer.music.Interval
import com.example.eartrainer.music.Pitch
import com.example.eartrainer.testing.FakePracticeHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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

    private fun interval(session: String, at: Long, interval: Interval, style: IntervalStyle, correct: Boolean) =
        IntervalAttempt(session, at, interval, style, Pitch(60), if (correct) interval else Interval.OCTAVE, correct, 1, 1_000)

    private fun chord(session: String, at: Long, quality: ChordQuality, inversion: ChordInversion, target: ChordTarget, correct: Boolean) =
        ChordAttempt(
            session, at, quality, inversion, ChordStyle.BLOCK, target, Pitch(60),
            answer = if (target == ChordTarget.QUALITY) ChordAnswer.Quality(quality) else ChordAnswer.Inversion(inversion),
            isCorrect = correct, playCount = 1, responseMillis = 1_000,
        )

    private fun TestScope.collected(viewModel: ProgressViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

    private val ProgressViewModel.state get() = checkNotNull(uiState.value)

    @Test
    fun emptyHistoryShowsNoHistory() = runTest {
        val viewModel = ProgressViewModel(history).also { collected(it) }

        assertFalse(viewModel.state.hasHistory)
        assertNull(viewModel.state.overallAccuracy)
        assertEquals(Interval.entries.map { it.displayName }, viewModel.state.accuracy.map { it.label })
    }

    @Test
    fun summarisesIntervalsAndFiltersByStyle() = runTest {
        history.record(interval("a", 1_000, Interval.MAJOR_THIRD, IntervalStyle.MELODIC_ASCENDING, correct = true))
        history.record(interval("a", 2_000, Interval.MAJOR_THIRD, IntervalStyle.HARMONIC, correct = false))
        history.record(interval("b", 9_000, Interval.OCTAVE, IntervalStyle.HARMONIC, correct = true))
        val viewModel = ProgressViewModel(history).also { collected(it) }

        val all = viewModel.state
        assertEquals(ExerciseKind.INTERVALS, all.exercise)
        assertEquals(3, all.totalAttempts)
        assertEquals(2, all.totalCorrect)
        assertEquals(listOf("b", "a"), all.recentSessions.map { it.sessionId })
        assertEquals(0.5f, all.accuracy.single { it.label == "Major third" }.accuracy!!, 0.0001f)

        viewModel.onFilterSelected(ProgressFilter.IntervalStyleFilter(IntervalStyle.HARMONIC))
        assertEquals(2, viewModel.state.totalAttempts)
        assertEquals(0f, viewModel.state.accuracy.single { it.label == "Major third" }.accuracy!!, 0.0001f)
    }

    @Test
    fun switchingToChordsShowsQualityThenInversionAccuracy() = runTest {
        history.record(interval("i", 1_000, Interval.OCTAVE, IntervalStyle.HARMONIC, correct = true))
        history.record(chord("c", 5_000, ChordQuality.MINOR, ChordInversion.ROOT_POSITION, ChordTarget.QUALITY, correct = true))
        history.record(chord("c", 6_000, ChordQuality.MINOR, ChordInversion.FIRST, ChordTarget.QUALITY, correct = false))
        history.record(chord("d", 8_000, ChordQuality.MAJOR, ChordInversion.SECOND, ChordTarget.INVERSION, correct = true))
        val viewModel = ProgressViewModel(history).also { collected(it) }

        viewModel.onExerciseSelected(ExerciseKind.CHORDS)
        val qualities = viewModel.state
        assertEquals(ProgressFilter.ChordTargetFilter(ChordTarget.QUALITY), qualities.filter)
        assertEquals(ChordQuality.entries.map { it.displayName }, qualities.accuracy.map { it.label })
        assertEquals(2, qualities.totalAttempts)
        assertEquals(AccuracyItem("Minor", attempts = 2, correct = 1), qualities.accuracy.single { it.label == "Minor" })

        viewModel.onFilterSelected(ProgressFilter.ChordTargetFilter(ChordTarget.INVERSION))
        val inversions = viewModel.state
        assertEquals(ChordInversion.entries.map { it.displayName }, inversions.accuracy.map { it.label })
        assertEquals(AccuracyItem("2nd inversion", attempts = 1, correct = 1), inversions.accuracy.single { it.label == "2nd inversion" })

        // Sessions from both exercises are listed, newest first.
        assertEquals(
            listOf("d" to ExerciseKind.CHORDS, "c" to ExerciseKind.CHORDS, "i" to ExerciseKind.INTERVALS),
            inversions.recentSessions.map { it.sessionId to it.exercise },
        )
        assertTrue(inversions.hasHistory)
    }

    @Test
    fun reselectingTheCurrentExerciseKeepsItsFilter() = runTest {
        val viewModel = ProgressViewModel(history).also { collected(it) }
        viewModel.onFilterSelected(ProgressFilter.IntervalStyleFilter(IntervalStyle.HARMONIC))
        viewModel.onExerciseSelected(ExerciseKind.INTERVALS)
        assertEquals(ProgressFilter.IntervalStyleFilter(IntervalStyle.HARMONIC), viewModel.state.filter)
    }
}
