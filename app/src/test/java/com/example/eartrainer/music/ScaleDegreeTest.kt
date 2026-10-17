package com.example.eartrainer.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ScaleDegreeTest {

    @Test
    fun degreesCoverEverySemitoneOnceWithUniqueNames() {
        assertEquals((0..11).toList(), ScaleDegree.entries.map { it.semitonesAboveTonic })
        assertEquals(12, ScaleDegree.entries.map { it.label }.toSet().size)
        assertEquals(12, ScaleDegree.entries.map { it.solfege }.toSet().size)
        assertEquals(ScaleDegree.FLAT_THIRD, ScaleDegree.fromSemitones(3))
        assertEquals(ScaleDegree.TONIC, ScaleDegree.fromSemitones(12))
        assertEquals("♭3", ScaleDegree.FLAT_THIRD.label)
        assertEquals("Sol", ScaleDegree.FIFTH.solfege)
    }

    @Test
    fun modesHaveTheirDiatonicDegrees() {
        assertEquals(listOf(0, 2, 4, 5, 7, 9, 11), Mode.MAJOR.diatonicDegrees.map { it.semitonesAboveTonic })
        assertEquals(listOf(0, 2, 3, 5, 7, 8, 10), Mode.MINOR.diatonicDegrees.map { it.semitonesAboveTonic })
    }

    @Test
    fun keysUseConventionalSpellings() {
        assertEquals("C major", Key(Pitch(60), Mode.MAJOR).name)
        assertEquals("E♭ major", Key(Pitch(63), Mode.MAJOR).name)
        assertEquals("C♯ minor", Key(Pitch(61), Mode.MINOR).name)
        assertEquals("B♭ minor", Key(Pitch(58), Mode.MINOR).name)
        assertEquals("F♯ major", Key(Pitch(66), Mode.MAJOR).name)
    }

    @Test
    fun degreesArePlacedAboveTheTonic() {
        val key = Key(Pitch(62), Mode.MAJOR)
        assertEquals(Pitch(62), key.pitchOf(ScaleDegree.TONIC))
        assertEquals(Pitch(66), key.pitchOf(ScaleDegree.THIRD))
        assertEquals(Pitch(73), key.pitchOf(ScaleDegree.SEVENTH))
    }

    /** Pitch classes of a chord relative to the tonic, ignoring octaves and doublings. */
    private fun Key.degreesOf(chord: List<Pitch>) = chord.map { ScaleDegree.fromSemitones(it.midi - tonic.midi) }.toSet()

    @Test
    fun majorCadenceIsOneFourFiveOne() {
        val key = Key(Pitch(60), Mode.MAJOR)
        val cadence = key.cadence()
        val tonic = setOf(ScaleDegree.TONIC, ScaleDegree.THIRD, ScaleDegree.FIFTH)
        assertEquals(
            listOf(tonic, setOf(ScaleDegree.FOURTH, ScaleDegree.SIXTH, ScaleDegree.TONIC), setOf(ScaleDegree.FIFTH, ScaleDegree.SEVENTH, ScaleDegree.SECOND), tonic),
            cadence.map { key.degreesOf(it) },
        )
        // Each chord's lowest note is its root: C, F, G, C.
        assertEquals(listOf(48, 53, 55, 48), cadence.map { chord -> chord.minOf { it.midi } })
        assertEquals(tonic, key.degreesOf(key.tonicChord()))
    }

    @Test
    fun minorCadenceUsesMinorChordsAndARaisedLeadingTone() {
        val key = Key(Pitch(57), Mode.MINOR)
        val cadence = key.cadence()
        val tonic = setOf(ScaleDegree.TONIC, ScaleDegree.FLAT_THIRD, ScaleDegree.FIFTH)
        assertEquals(
            listOf(tonic, setOf(ScaleDegree.FOURTH, ScaleDegree.FLAT_SIXTH, ScaleDegree.TONIC), setOf(ScaleDegree.FIFTH, ScaleDegree.SEVENTH, ScaleDegree.SECOND), tonic),
            cadence.map { key.degreesOf(it) },
        )
    }
}
