package com.example.eartrainer.ui.compare

import com.example.eartrainer.exercise.ChordTarget
import com.example.eartrainer.exercise.ExerciseKind
import com.example.eartrainer.exercise.IntervalStyle
import com.example.eartrainer.music.Mode
import com.example.eartrainer.ui.progress.AccuracyItem
import com.example.eartrainer.ui.progress.LabeledConfusion
import com.example.eartrainer.ui.progress.MistakesUiState
import com.example.eartrainer.ui.progress.ProgressFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComparisonRequestTest {

    private fun ComparisonRequest.roundTrip(): ComparisonRequest? {
        val parts = route.split("/")
        return ComparisonRequest.fromRoute(parts[1], parts[2], parts[3], parts[4])
    }

    @Test
    fun requestsSurviveTheRoute() {
        val withScope = ComparisonRequest(ExerciseKind.INTERVALS, "HARMONIC", "MINOR_SIXTH", "MAJOR_SIXTH")
        assertEquals(withScope, withScope.roundTrip())
        val everyStyle = ComparisonRequest(ExerciseKind.SCALE_DEGREES, null, "FIFTH", "SIXTH")
        assertEquals(everyStyle, everyStyle.roundTrip())
    }

    @Test
    fun routesThatCannotBuildADrillAreRejected() {
        assertNull(ComparisonRequest.fromRoute("PROGRESSIONS", "MAJOR", "4", "2"))
        assertNull(ComparisonRequest.fromRoute("NOT_AN_EXERCISE", "ALL", "A", "B"))
        assertNull(ComparisonRequest.fromRoute("INTERVALS", "ALL", "OCTAVE", "OCTAVE"))
        assertNull(ComparisonRequest.fromRoute("INTERVALS", "ALL", null, "OCTAVE"))
    }

    private fun mistakes(filter: ProgressFilter, first: Pair<String, String>, second: Pair<String, String>) = checkNotNull(
        MistakesUiState.from(
            filter,
            accuracy = listOf(AccuracyItem(first.first, 2, 1), AccuracyItem(second.first, 1, 1)),
            confusions = listOf(
                LabeledConfusion(first.first, first.first, 1, first.second, first.second),
                LabeledConfusion(first.first, second.first, 1, first.second, second.second),
                LabeledConfusion(second.first, second.first, 1, second.second, second.second),
            ),
            selectedLabel = first.first,
        ),
    )

    @Test
    fun mistakesBuildRequestsUnderTheirFilter() {
        val intervals = mistakes(ProgressFilter.IntervalStyleFilter(IntervalStyle.HARMONIC), "Minor sixth" to "MINOR_SIXTH", "Major sixth" to "MAJOR_SIXTH")
        assertEquals(
            ComparisonRequest(ExerciseKind.INTERVALS, "HARMONIC", "MINOR_SIXTH", "MAJOR_SIXTH"),
            intervals.comparisonFor("Minor sixth", "Major sixth"),
        )
        assertNull(intervals.comparisonFor("Minor sixth", "Not an interval"))

        val chords = mistakes(ProgressFilter.ChordTargetFilter(ChordTarget.INVERSION), "1st inversion" to "FIRST", "2nd inversion" to "SECOND")
        assertEquals(ComparisonRequest(ExerciseKind.CHORDS, "INVERSION", "FIRST", "SECOND"), chords.comparisonFor("1st inversion", "2nd inversion"))

        val dictation = mistakes(ProgressFilter.DictationModeFilter(Mode.MAJOR), "3 · Mi" to "THIRD", "4 · Fa" to "FOURTH")
        assertEquals(ComparisonRequest(ExerciseKind.DICTATION, "MAJOR", "THIRD", "FOURTH"), dictation.comparisonFor("3 · Mi", "4 · Fa"))
    }

    @Test
    fun exercisesWithoutADrillOfferNone() {
        val progressions = mistakes(ProgressFilter.ProgressionModeFilter(Mode.MAJOR), "IV" to "4", "ii" to "2")
        assertNull(progressions.comparisonFor("IV", "ii"))
    }
}
