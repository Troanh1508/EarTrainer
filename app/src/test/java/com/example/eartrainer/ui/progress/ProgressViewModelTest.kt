package com.example.eartrainer.ui.progress

import com.example.eartrainer.exercise.ChordAnswer
import com.example.eartrainer.exercise.ChordAttempt
import com.example.eartrainer.exercise.ChordStyle
import com.example.eartrainer.data.history.SessionSummary
import com.example.eartrainer.exercise.ChordTarget
import com.example.eartrainer.exercise.DictationAttempt
import com.example.eartrainer.exercise.ExerciseKind
import com.example.eartrainer.exercise.IntervalAttempt
import com.example.eartrainer.exercise.IntervalStyle
import com.example.eartrainer.exercise.KeyContext
import com.example.eartrainer.exercise.ProgressionAttempt
import com.example.eartrainer.exercise.RhythmAttempt
import com.example.eartrainer.exercise.RhythmTempo
import com.example.eartrainer.exercise.ScaleDegreeAttempt
import com.example.eartrainer.music.ChordInversion
import com.example.eartrainer.music.ChordProgression
import com.example.eartrainer.music.ChordQuality
import com.example.eartrainer.music.DiatonicChord
import com.example.eartrainer.music.Interval
import com.example.eartrainer.music.Key
import com.example.eartrainer.music.Melody
import com.example.eartrainer.music.Mode
import com.example.eartrainer.music.Pitch
import com.example.eartrainer.music.Rhythm
import com.example.eartrainer.music.RhythmCell
import com.example.eartrainer.music.ScaleDegree
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
    fun summarizesIntervalsAndFiltersByStyle() = runTest {
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
    fun scaleDegreesTabShowsEveryDegreeAndFiltersByMode() = runTest {
        val cMajor = Key(Pitch(60), Mode.MAJOR)
        val aMinor = Key(Pitch(57), Mode.MINOR)
        history.record(ScaleDegreeAttempt("m", 1_000, cMajor, ScaleDegree.THIRD, KeyContext.CADENCE, ScaleDegree.THIRD, true, 1, 900))
        history.record(ScaleDegreeAttempt("n", 2_000, aMinor, ScaleDegree.FLAT_THIRD, KeyContext.CADENCE, ScaleDegree.THIRD, false, 1, 900))
        val viewModel = ProgressViewModel(history).also { collected(it) }

        viewModel.onExerciseSelected(ExerciseKind.SCALE_DEGREES)
        val all = viewModel.state
        assertEquals(ProgressFilter.ModeFilter(null), all.filter)
        assertEquals(ScaleDegree.entries.map { "${it.label} · ${it.solfege}" }, all.accuracy.map { it.label })
        assertEquals(AccuracyItem("3 · Mi", attempts = 1, correct = 1), all.accuracy.single { it.label == "3 · Mi" })
        assertEquals(2, all.totalAttempts)

        viewModel.onFilterSelected(ProgressFilter.ModeFilter(Mode.MINOR))
        assertEquals(1, viewModel.state.totalAttempts)
        assertEquals(0, viewModel.state.totalCorrect)
        assertEquals(ExerciseKind.SCALE_DEGREES, viewModel.state.recentSessions.first().exercise)
    }

    @Test
    fun progressionsTabScoresEachChordAfterTheTonic() = runTest {
        fun progression(vararg steps: Int) = ChordProgression(steps.map { DiatonicChord(Mode.MAJOR, it) })
        val key = Key(Pitch(60), Mode.MAJOR)
        // Asked I-vi-IV-V, answered I-vi-ii-V: vi and V right, IV wrong.
        history.record(ProgressionAttempt("p", 1_000, key, progression(1, 6, 4, 5), progression(1, 6, 2, 5), false, 1, 5_000))
        // Asked I-IV-V-I, answered correctly: IV, V and the closing I right.
        history.record(ProgressionAttempt("p", 2_000, key, progression(1, 4, 5, 1), progression(1, 4, 5, 1), true, 1, 4_000))
        val viewModel = ProgressViewModel(history).also { collected(it) }

        viewModel.onExerciseSelected(ExerciseKind.PROGRESSIONS)
        val state = viewModel.state
        assertEquals(ProgressFilter.ProgressionModeFilter(Mode.MAJOR), state.filter)
        assertEquals(listOf("I", "ii", "iii", "IV", "V", "vi", "vii°"), state.accuracy.map { it.label })
        assertEquals(AccuracyItem("IV", attempts = 2, correct = 1), state.accuracy.single { it.label == "IV" })
        assertEquals(AccuracyItem("V", attempts = 2, correct = 2), state.accuracy.single { it.label == "V" })
        // Only the closing tonic counts; the opening one is given.
        assertEquals(AccuracyItem("I", attempts = 1, correct = 1), state.accuracy.single { it.label == "I" })
        assertEquals(6, state.totalAttempts)
        // The trend point and the session list score the session the same way: 5 of 6 chords,
        // while 1 of its 2 progressions was fully right.
        val session = SessionSummary("p", ExerciseKind.PROGRESSIONS, 1_000, attempts = 2, correct = 1, items = 6, itemsCorrect = 5)
        assertEquals(listOf(session), state.trend)
        assertEquals(listOf(session), state.recentSessions)

        viewModel.onFilterSelected(ProgressFilter.ProgressionModeFilter(Mode.MINOR))
        assertEquals(0, viewModel.state.totalAttempts)
        assertEquals(emptyList<SessionSummary>(), viewModel.state.trend)
    }

    @Test
    fun dictationTabScoresEachNoteAfterTheTonic() = runTest {
        fun melody(vararg degrees: ScaleDegree) = Melody(degrees.toList())
        val key = Key(Pitch(60), Mode.MAJOR)
        // Played 1-2-3-5, written 1-2-4-5: 2 and 5 right, 3 wrong.
        history.record(
            DictationAttempt(
                "d", 1_000, key,
                melody(ScaleDegree.TONIC, ScaleDegree.SECOND, ScaleDegree.THIRD, ScaleDegree.FIFTH),
                melody(ScaleDegree.TONIC, ScaleDegree.SECOND, ScaleDegree.FOURTH, ScaleDegree.FIFTH),
                false, 2, 9_000,
            ),
        )
        val viewModel = ProgressViewModel(history).also { collected(it) }

        viewModel.onExerciseSelected(ExerciseKind.DICTATION)
        val state = viewModel.state
        assertEquals(ProgressFilter.DictationModeFilter(Mode.MAJOR), state.filter)
        assertEquals(Mode.MAJOR.diatonicDegrees.map { "${it.label} · ${it.solfege}" }, state.accuracy.map { it.label })
        assertEquals(AccuracyItem("3 · Mi", attempts = 1, correct = 0), state.accuracy.single { it.label == "3 · Mi" })
        assertEquals(3, state.totalAttempts)
        assertEquals(2, state.totalCorrect)
    }

    @Test
    fun rhythmTabScoresEachFigureOnItsBeat() = runTest {
        val played = Rhythm(listOf(RhythmCell.QUARTER, RhythmCell.EIGHTHS, RhythmCell.HALF))
        val chosen = Rhythm(listOf(RhythmCell.QUARTER, RhythmCell.QUARTER_REST, RhythmCell.HALF))
        history.record(RhythmAttempt("r", 1_000, played, RhythmTempo.MEDIUM, chosen, false, 1, 4_000))
        val viewModel = ProgressViewModel(history).also { collected(it) }

        viewModel.onExerciseSelected(ExerciseKind.RHYTHM)
        val state = viewModel.state
        assertEquals(ProgressFilter.RhythmFilter, state.filter)
        assertEquals(RhythmCell.entries.size, state.accuracy.size)
        assertEquals(AccuracyItem("Two eighths", attempts = 1, correct = 0), state.accuracy.single { it.label == "Two eighths" })
        assertEquals(AccuracyItem("Half", attempts = 1, correct = 1), state.accuracy.single { it.label == "Half" })
        assertEquals(3, state.totalAttempts)
        assertEquals(2, state.totalCorrect)
    }

    @Test
    fun trendHoldsTheSelectedExercisesLatestSessionsOldestFirst() = runTest {
        // 14 interval sessions, one answer each, plus a chord session in between.
        repeat(14) { index ->
            history.record(interval("i$index", 1_000L * (index + 1), Interval.MAJOR_THIRD, IntervalStyle.HARMONIC, correct = index % 2 == 0))
        }
        history.record(chord("c", 5_500, ChordQuality.MAJOR, ChordInversion.ROOT_POSITION, ChordTarget.QUALITY, correct = true))
        val viewModel = ProgressViewModel(history).also { collected(it) }

        val trend = viewModel.state.trend
        assertEquals(ProgressViewModel.TREND_SESSION_LIMIT, trend.size)
        assertEquals((2 until 14).map { "i$it" }, trend.map { it.sessionId })

        viewModel.onExerciseSelected(ExerciseKind.CHORDS)
        assertEquals(listOf("c"), viewModel.state.trend.map { it.sessionId })
    }

    @Test
    fun trendCountsOnlyAnswersMatchingTheFilter() = runTest {
        // "up" has only ascending answers; "mixed" has one of each style.
        history.record(interval("up", 1_000, Interval.MAJOR_THIRD, IntervalStyle.MELODIC_ASCENDING, correct = true))
        history.record(interval("mixed", 2_000, Interval.MAJOR_THIRD, IntervalStyle.MELODIC_ASCENDING, correct = true))
        history.record(interval("mixed", 3_000, Interval.OCTAVE, IntervalStyle.HARMONIC, correct = false))
        val viewModel = ProgressViewModel(history).also { collected(it) }

        assertEquals(listOf("up" to 1, "mixed" to 2), viewModel.state.trend.map { it.sessionId to it.items })

        viewModel.onFilterSelected(ProgressFilter.IntervalStyleFilter(IntervalStyle.HARMONIC))
        assertEquals(
            listOf(SessionSummary("mixed", ExerciseKind.INTERVALS, 3_000, attempts = 1, correct = 0)),
            viewModel.state.trend,
        )
    }

    @Test
    fun mistakesShowWhatTheSelectedItemWasAnsweredAsUnderTheFilter() = runTest {
        // Wrong answers are recorded as an octave by the helper.
        history.record(interval("a", 1_000, Interval.MAJOR_THIRD, IntervalStyle.HARMONIC, correct = false))
        history.record(interval("a", 2_000, Interval.MAJOR_THIRD, IntervalStyle.HARMONIC, correct = true))
        history.record(interval("a", 3_000, Interval.MAJOR_THIRD, IntervalStyle.MELODIC_ASCENDING, correct = false))
        val viewModel = ProgressViewModel(history).also { collected(it) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.mistakes.collect {} }

        assertNull(viewModel.mistakes.value)
        viewModel.onItemSelected("Major third")
        val mistakes = checkNotNull(viewModel.mistakes.value)
        assertEquals("Major third", viewModel.mistakesItem.value)
        assertEquals(
            listOf(AnswerShare("Major third", 1, isRight = true), AnswerShare("Octave", 2, isRight = false)),
            mistakes.answers,
        )
        assertEquals(listOf(ConfusedPair("Major third", "Octave", 2)), mistakes.confusedPairs)
        // Intervals have short names for tight spaces, and a distance for the tip.
        assertEquals("M3", mistakes.shortLabelOf("Major third"))
        assertEquals("P8", mistakes.shortLabelOf("Octave"))
        assertEquals(8, mistakes.semitonesBetween("Major third", "Octave"))

        viewModel.onMistakesClosed()
        assertNull(viewModel.mistakes.value)

        // Changing the filter returns to the overview.
        viewModel.onItemSelected("Major third")
        viewModel.onFilterSelected(ProgressFilter.IntervalStyleFilter(IntervalStyle.HARMONIC))
        assertNull(viewModel.mistakesItem.value)
    }

    @Test
    fun trendChangeComparesTheNewerHalfWithTheOlderHalf() {
        fun state(vararg correctOutOfTen: Int) = ProgressUiState(
            filter = ProgressFilter.IntervalStyleFilter(null),
            accuracy = emptyList(),
            recentSessions = emptyList(),
            trend = correctOutOfTen.mapIndexed { index, correct -> SessionSummary("s$index", ExerciseKind.INTERVALS, index.toLong(), 10, correct) },
        )
        assertNull(state(5, 6, 7).trendChange)
        // Older half 50% and 60% pools to 55%; newer 80% and 90% to 85%.
        assertEquals(30, state(5, 6, 8, 9).trendChange)
        // With an odd count the middle session belongs to neither half.
        assertEquals(-20, state(8, 8, 1, 6, 6).trendChange)
        assertEquals(0, state(7, 7, 7, 7).trendChange)
    }

    @Test
    fun weakestFirstSortsPracticedItemsByAccuracyThenLeavesUnpracticedLast() {
        val state = ProgressUiState(
            filter = ProgressFilter.IntervalStyleFilter(null),
            accuracy = listOf(
                AccuracyItem("Never", 0, 0),
                AccuracyItem("Strong", 10, 9),
                AccuracyItem("Weak, few", 2, 1),
                AccuracyItem("Weak, many", 10, 5),
                AccuracyItem("Also never", 0, 0),
            ),
            recentSessions = emptyList(),
        )
        assertEquals(listOf("Weak, many", "Weak, few", "Strong", "Never", "Also never"), state.weakestFirst.map { it.label })
        // Strongest first also puts more answers first on a tie, and unpracticed items still last.
        assertEquals(listOf("Strong", "Weak, many", "Weak, few", "Never", "Also never"), state.strongestFirst.map { it.label })
        assertEquals(state.weakestFirst, state.orderedItems)
        assertEquals(state.strongestFirst, state.copy(order = ItemOrder.STRONGEST_FIRST).orderedItems)
        assertEquals(listOf(false, false, true, true, false), state.accuracy.map { it.needsWork })
    }

    @Test
    fun theChosenOrderIsKeptAcrossExercisesAndFilters() = runTest {
        history.record(interval("a", 1_000, Interval.MAJOR_THIRD, IntervalStyle.HARMONIC, correct = false))
        history.record(interval("a", 2_000, Interval.OCTAVE, IntervalStyle.HARMONIC, correct = true))
        val viewModel = ProgressViewModel(history).also { collected(it) }
        assertEquals(ItemOrder.WEAKEST_FIRST, viewModel.state.order)
        assertEquals("Major third", viewModel.state.orderedItems.first().label)

        viewModel.onOrderSelected(ItemOrder.STRONGEST_FIRST)
        assertEquals("Octave", viewModel.state.orderedItems.first().label)
        viewModel.onFilterSelected(ProgressFilter.IntervalStyleFilter(IntervalStyle.HARMONIC))
        assertEquals(ItemOrder.STRONGEST_FIRST, viewModel.state.order)
        viewModel.onExerciseSelected(ExerciseKind.CHORDS)
        assertEquals(ItemOrder.STRONGEST_FIRST, viewModel.state.order)
    }

    @Test
    fun reselectingTheCurrentExerciseKeepsItsFilter() = runTest {
        val viewModel = ProgressViewModel(history).also { collected(it) }
        viewModel.onFilterSelected(ProgressFilter.IntervalStyleFilter(IntervalStyle.HARMONIC))
        viewModel.onExerciseSelected(ExerciseKind.INTERVALS)
        assertEquals(ProgressFilter.IntervalStyleFilter(IntervalStyle.HARMONIC), viewModel.state.filter)
    }
}
