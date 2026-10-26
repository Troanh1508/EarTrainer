package com.troanhmolo.eartrainer.data.history

import com.troanhmolo.eartrainer.data.history.WeakSpots.Companion.asWeightLookup
import com.troanhmolo.eartrainer.exercise.ChordAnswer
import com.troanhmolo.eartrainer.exercise.ChordAttempt
import com.troanhmolo.eartrainer.exercise.ChordStyle
import com.troanhmolo.eartrainer.exercise.ChordTarget
import com.troanhmolo.eartrainer.exercise.IntervalAttempt
import com.troanhmolo.eartrainer.exercise.IntervalStyle
import com.troanhmolo.eartrainer.exercise.ProgressionAttempt
import com.troanhmolo.eartrainer.exercise.WeakSpotWeighting
import com.troanhmolo.eartrainer.music.ChordInversion
import com.troanhmolo.eartrainer.music.ChordProgression
import com.troanhmolo.eartrainer.music.ChordQuality
import com.troanhmolo.eartrainer.music.DiatonicChord
import com.troanhmolo.eartrainer.music.Interval
import com.troanhmolo.eartrainer.music.Key
import com.troanhmolo.eartrainer.music.Mode
import com.troanhmolo.eartrainer.music.Pitch
import com.troanhmolo.eartrainer.testing.FakePracticeHistory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeakSpotsTest {

    private val history = FakePracticeHistory()

    private fun interval(interval: Interval, correct: Boolean) =
        IntervalAttempt("s", 0, interval, IntervalStyle.MELODIC_ASCENDING, Pitch(60), interval, correct, 1, 1_000)

    @Test
    fun weightsFollowNewAnswersAsTheyAreRecorded() = runTest {
        val weakSpots = WeakSpots(history, backgroundScope)
        val lookup = weakSpots.intervals.asWeightLookup()
        // The collector runs in the background scope, which runCurrent() includes and advanceUntilIdle() does not.
        testScheduler.runCurrent()
        assertEquals(WeakSpotWeighting.UNSEEN_WEIGHT, lookup(Interval.MINOR_SIXTH), 1e-12)

        repeat(4) { history.record(interval(Interval.MINOR_SIXTH, correct = false)) }
        repeat(10) { history.record(interval(Interval.OCTAVE, correct = true)) }
        testScheduler.runCurrent()

        assertTrue(lookup(Interval.MINOR_SIXTH) > WeakSpotWeighting.UNSEEN_WEIGHT)
        assertTrue(lookup(Interval.OCTAVE) < WeakSpotWeighting.UNSEEN_WEIGHT)
        // Never-practiced intervals keep the neutral weight.
        assertEquals(WeakSpotWeighting.UNSEEN_WEIGHT, lookup(Interval.TRITONE), 1e-12)
    }

    @Test
    fun chordWeightsAreKeptPerQualityAndPerInversion() = runTest(UnconfinedTestDispatcher()) {
        history.record(
            ChordAttempt(
                "c", 0, ChordQuality.MINOR, ChordInversion.FIRST, ChordStyle.BLOCK, ChordTarget.INVERSION, Pitch(55),
                ChordAnswer.Inversion(ChordInversion.ROOT_POSITION), false, 1, 1_000,
            ),
        )
        val weakSpots = WeakSpots(history, backgroundScope)
        val weights = weakSpots.chords.value
        assertTrue(ChordAnswer.Inversion(ChordInversion.FIRST) in weights)
        // The question asked for the inversion, so the quality is not scored.
        assertTrue(ChordAnswer.Quality(ChordQuality.MINOR) !in weights)
    }

    @Test
    fun progressionWeightsScoreEachChordAfterTheTonic() = runTest(UnconfinedTestDispatcher()) {
        fun progression(vararg steps: Int) = ChordProgression(steps.map { DiatonicChord(Mode.MAJOR, it) })
        history.record(
            ProgressionAttempt("p", 0, Key(Pitch(60), Mode.MAJOR), progression(1, 6, 4, 5), progression(1, 2, 4, 5), false, 1, 4_000),
        )
        val weights = WeakSpots(history, backgroundScope).progressionChords.value
        assertEquals(setOf(6, 4, 5), weights.keys.map { it.step }.toSet())
        assertTrue(weights.getValue(DiatonicChord(Mode.MAJOR, 6)) > weights.getValue(DiatonicChord(Mode.MAJOR, 4)))
    }
}
