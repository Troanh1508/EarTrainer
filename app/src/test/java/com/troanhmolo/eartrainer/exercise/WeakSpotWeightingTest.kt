package com.troanhmolo.eartrainer.exercise

import com.troanhmolo.eartrainer.music.ChordInversion
import com.troanhmolo.eartrainer.music.ChordQuality
import com.troanhmolo.eartrainer.music.DiatonicChord
import com.troanhmolo.eartrainer.music.Interval
import com.troanhmolo.eartrainer.music.Mode
import com.troanhmolo.eartrainer.music.ScaleDegree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WeakSpotWeightingTest {

    @Test
    fun weakItemsWeighMoreThanUnseenWhichWeighMoreThanMastered() {
        val mastered = WeakSpotWeighting.weightOf(correct = 20, attempts = 20)
        val unseen = WeakSpotWeighting.weightOf(correct = 0, attempts = 0)
        val weak = WeakSpotWeighting.weightOf(correct = 0, attempts = 5)
        assertTrue(weak > unseen && unseen > mastered)
        assertEquals(0.245, mastered, 0.001)
        assertEquals(0.7, unseen, 0.001)
        assertEquals(1.057, weak, 0.001)
        assertEquals(unseen, WeakSpotWeighting.UNSEEN_WEIGHT, 1e-12)
    }

    @Test
    fun nothingDropsBelowTheFloor() {
        assertTrue(WeakSpotWeighting.weightOf(correct = 10_000, attempts = 10_000) > WeakSpotWeighting.MIN_WEIGHT)
    }

    @Test
    fun aSingleEarlyResultDoesNotSwingToExtremes() {
        val oneMiss = WeakSpotWeighting.weightOf(correct = 0, attempts = 1)
        val oneHit = WeakSpotWeighting.weightOf(correct = 1, attempts = 1)
        assertEquals(0.867, oneMiss, 0.001)
        assertEquals(0.533, oneHit, 0.001)
    }

    @Test
    fun weightsAreComputedPerItem() {
        val results = List(4) { ItemResult(Interval.OCTAVE, true) } + listOf(ItemResult(Interval.TRITONE, false), ItemResult(Interval.TRITONE, false))
        val weights = WeakSpotWeighting.weights(results)
        assertEquals(setOf(Interval.OCTAVE, Interval.TRITONE), weights.keys)
        assertTrue(weights.getValue(Interval.TRITONE) > weights.getValue(Interval.OCTAVE))
    }

    @Test
    fun weightedRandomPicksInProportionToWeight() {
        val random = Random(1)
        val picks = List(30_000) { listOf("a", "b").weightedRandom(random) { if (it == "a") 1.0 else 3.0 } }
        val ratio = picks.count { it == "b" }.toDouble() / picks.count { it == "a" }
        assertEquals(3.0, ratio, 0.15)
    }

    @Test
    fun weightedRandomFallsBackToUniformWhenAllWeightsAreZero() {
        val random = Random(2)
        val picks = List(1_000) { listOf("a", "b").weightedRandom(random) { 0.0 } }.toSet()
        assertEquals(setOf("a", "b"), picks)
    }

    /** Fraction of [count] generated questions for which [isFavored] holds. */
    private fun <Q> share(count: Int, generate: () -> Q, isFavored: (Q) -> Boolean) =
        List(count) { generate() }.count(isFavored).toDouble() / count

    @Test
    fun intervalGeneratorFavorsWeakIntervals() {
        val weights: (Interval) -> Double = { if (it == Interval.TRITONE) 10.0 else 0.2 }
        val generator = IntervalQuestionGenerator(IntervalExerciseSettings(), Random(3), weights)
        // 10 vs 11 × 0.2 = 2.2: the tritone should be asked about 82% of the time.
        assertEquals(0.82, share(4_000, generator::generate) { it.interval == Interval.TRITONE }, 0.03)
    }

    @Test
    fun chordGeneratorWeightsWhateverIsBeingNamed() {
        val qualities = ChordExerciseSettings(qualities = ChordQuality.entries)
        val favorMinor7: (ChordAnswer) -> Double = { if (it == ChordAnswer.Quality(ChordQuality.MINOR_SEVENTH)) 10.0 else 0.1 }
        val byQuality = ChordQuestionGenerator(qualities, Random(4), favorMinor7)
        assertTrue(share(2_000, byQuality::generate) { it.quality == ChordQuality.MINOR_SEVENTH } > 0.85)

        val inversions = qualities.copy(target = ChordTarget.INVERSION)
        val favorSecond: (ChordAnswer) -> Double = {
            if (it == ChordAnswer.Inversion(ChordInversion.SECOND)) 10.0 else 0.1
        }
        val byInversion = ChordQuestionGenerator(inversions, Random(5), favorSecond)
        assertTrue(share(2_000, byInversion::generate) { it.inversion == ChordInversion.SECOND } > 0.9)
    }

    @Test
    fun scaleDegreeGeneratorFavorsWeakDegrees() {
        val weights: (ScaleDegree) -> Double = { if (it == ScaleDegree.SIXTH) 10.0 else 0.2 }
        val generator = ScaleDegreeQuestionGenerator(ScaleDegreeSettings(), Random(6), weights)
        // 10 vs 6 × 0.2 = 1.2: the sixth should be asked about 89% of the time.
        assertEquals(0.89, share(4_000, generator::generate) { it.degree == ScaleDegree.SIXTH }, 0.03)
    }

    @Test
    fun progressionGeneratorFavorsProgressionsWithWeakChords() {
        val vi = DiatonicChord(Mode.MAJOR, 6)
        val weights: (DiatonicChord) -> Double = { if (it == vi) 10.0 else 0.2 }
        val settings = ProgressionSettings(source = ProgressionSource.RANDOM)
        val weighted = ProgressionQuestionGenerator(settings, Random(7), weights)
        val uniform = ProgressionQuestionGenerator(settings, Random(7))
        val withVi: (ProgressionQuestion) -> Boolean = { vi in it.progression.chords }
        assertTrue(share(2_000, weighted::generate, withVi) > share(2_000, uniform::generate, withVi) + 0.2)
    }
}
