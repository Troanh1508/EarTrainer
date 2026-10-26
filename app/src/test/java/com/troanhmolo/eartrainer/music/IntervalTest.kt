package com.troanhmolo.eartrainer.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntervalTest {

    @Test
    fun semitoneValuesMatchMusicTheory() {
        val expected = mapOf(
            Interval.MINOR_SECOND to 1,
            Interval.MAJOR_SECOND to 2,
            Interval.MINOR_THIRD to 3,
            Interval.MAJOR_THIRD to 4,
            Interval.PERFECT_FOURTH to 5,
            Interval.TRITONE to 6,
            Interval.PERFECT_FIFTH to 7,
            Interval.MINOR_SIXTH to 8,
            Interval.MAJOR_SIXTH to 9,
            Interval.MINOR_SEVENTH to 10,
            Interval.MAJOR_SEVENTH to 11,
            Interval.OCTAVE to 12,
        )
        assertEquals(expected.keys, Interval.entries.toSet())
        expected.forEach { (interval, semitones) -> assertEquals(interval.name, semitones, interval.semitones) }
    }

    @Test
    fun namesAndAbbreviationsAreUnique() {
        assertEquals(Interval.entries.size, Interval.entries.map { it.displayName }.toSet().size)
        assertEquals(Interval.entries.size, Interval.entries.map { it.abbreviation }.toSet().size)
    }

    @Test
    fun namesAreHumanReadable() {
        assertEquals("Minor second", Interval.MINOR_SECOND.displayName)
        assertEquals("Tritone", Interval.TRITONE.displayName)
        assertEquals("Perfect fifth", Interval.PERFECT_FIFTH.displayName)
        assertEquals("P5", Interval.PERFECT_FIFTH.abbreviation)
        assertEquals("Octave", Interval.OCTAVE.displayName)
    }

    @Test
    fun fromSemitonesRoundTrips() {
        Interval.entries.forEach { assertEquals(it, Interval.fromSemitones(it.semitones)) }
        assertNull(Interval.fromSemitones(0))
        assertNull(Interval.fromSemitones(13))
    }

    @Test
    fun betweenIgnoresDirection() {
        assertEquals(Interval.MAJOR_THIRD, Interval.between(Pitch(60), Pitch(64)))
        assertEquals(Interval.MAJOR_THIRD, Interval.between(Pitch(64), Pitch(60)))
        assertEquals(Interval.OCTAVE, Interval.between(Pitch(48), Pitch(60)))
        assertNull(Interval.between(Pitch(60), Pitch(60)))
    }
}
