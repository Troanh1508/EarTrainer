package com.example.eartrainer.exercise

import com.example.eartrainer.music.Interval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class IntervalExerciseSettingsTest {

    private val thirds = IntervalExerciseSettings(intervals = listOf(Interval.MINOR_THIRD, Interval.MAJOR_THIRD))

    @Test
    fun fewerThanTwoIntervalsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { IntervalExerciseSettings(intervals = listOf(Interval.OCTAVE)) }
    }

    @Test
    fun togglingAddsIntervalsInSizeOrder() {
        val updated = thirds.withIntervalToggled(Interval.MINOR_SECOND).withIntervalToggled(Interval.OCTAVE)
        assertEquals(listOf(Interval.MINOR_SECOND, Interval.MINOR_THIRD, Interval.MAJOR_THIRD, Interval.OCTAVE), updated.intervals)
        assertEquals(listOf(Interval.MINOR_SECOND, Interval.MAJOR_THIRD, Interval.OCTAVE), updated.withIntervalToggled(Interval.MINOR_THIRD).intervals)
    }

    @Test
    fun cannotDropBelowTwoIntervals() {
        assertSame(thirds, thirds.withIntervalToggled(Interval.MINOR_THIRD))
        assertSame(thirds, thirds.withIntervalToggled(Interval.MAJOR_THIRD))
    }

    @Test
    fun stylesToggleButNeverBecomeEmpty() {
        val defaults = IntervalExerciseSettings()
        assertSame(defaults, defaults.withStyleToggled(IntervalStyle.MELODIC_ASCENDING))

        val both = defaults.withStyleToggled(IntervalStyle.HARMONIC)
        assertEquals(setOf(IntervalStyle.MELODIC_ASCENDING, IntervalStyle.HARMONIC), both.styles)
        assertEquals(setOf(IntervalStyle.HARMONIC), both.withStyleToggled(IntervalStyle.MELODIC_ASCENDING).styles)
    }

    @Test
    fun choiceCountOptionsIncludeTheDefaultAndShowAll() {
        assertTrue(IntervalExerciseSettings().choiceCount in IntervalExerciseSettings.CHOICE_COUNT_OPTIONS)
        assertEquals(Interval.entries.size, IntervalExerciseSettings.CHOICE_COUNT_OPTIONS.max())
    }

    @Test
    fun choiceCountLargerThanSelectionShowsEverySelectedInterval() {
        val question = IntervalQuestionGenerator(thirds.copy(choiceCount = 12)).generate()
        assertEquals(thirds.intervals, question.choices)
    }
}
