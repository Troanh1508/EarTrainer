package com.troanhmolo.eartrainer.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordTest {

    @Test
    fun rootPositionSpellingsMatchMusicTheory() {
        val expected = mapOf(
            ChordQuality.MAJOR to listOf(0, 4, 7),
            ChordQuality.MINOR to listOf(0, 3, 7),
            ChordQuality.DIMINISHED to listOf(0, 3, 6),
            ChordQuality.AUGMENTED to listOf(0, 4, 8),
            ChordQuality.MAJOR_SEVENTH to listOf(0, 4, 7, 11),
            ChordQuality.DOMINANT_SEVENTH to listOf(0, 4, 7, 10),
            ChordQuality.MINOR_SEVENTH to listOf(0, 3, 7, 10),
            ChordQuality.HALF_DIMINISHED_SEVENTH to listOf(0, 3, 6, 10),
            ChordQuality.DIMINISHED_SEVENTH to listOf(0, 3, 6, 9),
        )
        assertEquals(expected.keys, ChordQuality.entries.toSet())
        expected.forEach { (quality, semitones) ->
            assertEquals(quality.name, semitones, quality.semitonesAboveRoot)
            assertEquals(semitones, quality.offsetsAboveBass(ChordInversion.ROOT_POSITION))
        }
    }

    @Test
    fun namesAndSymbolsAreUnique() {
        assertEquals(ChordQuality.entries.size, ChordQuality.entries.map { it.displayName }.toSet().size)
        assertEquals(ChordQuality.entries.size, ChordQuality.entries.map { it.symbol }.toSet().size)
    }

    @Test
    fun triadsHaveThreeInversionsAndSeventhsFour() {
        ChordQuality.entries.forEach { quality ->
            val expected = if (quality.isSeventh) 4 else 3
            assertEquals(quality.name, expected, quality.inversions.size)
        }
        assertThrows(IllegalArgumentException::class.java) { ChordQuality.MAJOR.offsetsAboveBass(ChordInversion.THIRD) }
    }

    @Test
    fun inversionsRaiseTheLowestTonesByAnOctave() {
        // C major: E-G-C and G-C-E.
        assertEquals(listOf(0, 3, 8), ChordQuality.MAJOR.offsetsAboveBass(ChordInversion.FIRST))
        assertEquals(listOf(0, 5, 9), ChordQuality.MAJOR.offsetsAboveBass(ChordInversion.SECOND))
        // Cmaj7 third inversion: B-C-E-G.
        assertEquals(listOf(0, 1, 5, 8), ChordQuality.MAJOR_SEVENTH.offsetsAboveBass(ChordInversion.THIRD))
        // Dominant 7th second inversion: G-B♭-C-E.
        assertEquals(listOf(0, 3, 5, 9), ChordQuality.DOMINANT_SEVENTH.offsetsAboveBass(ChordInversion.SECOND))
    }

    @Test
    fun everyInversionKeepsTheSamePitchClassesInCloseAscendingPosition() {
        ChordQuality.entries.forEach { quality ->
            val pitchClasses = quality.semitonesAboveRoot.map { it % 12 }.toSet()
            quality.inversions.forEach { inversion ->
                val offsets = quality.offsetsAboveBass(inversion)
                val root = quality.rootOffsetAboveBass(inversion)
                assertEquals("$quality $inversion", pitchClasses, offsets.map { (it - root).mod(12) }.toSet())
                assertTrue("$quality $inversion ascending", offsets.zipWithNext().all { (a, b) -> b > a })
                assertTrue("$quality $inversion within an octave", offsets.last() < 12)
            }
        }
    }

    @Test
    fun rootIsFoundAboveTheBass() {
        assertEquals(0, ChordQuality.MINOR.rootOffsetAboveBass(ChordInversion.ROOT_POSITION))
        assertEquals(8, ChordQuality.MAJOR.rootOffsetAboveBass(ChordInversion.FIRST)) // E up to C
        assertEquals(5, ChordQuality.MAJOR.rootOffsetAboveBass(ChordInversion.SECOND)) // G up to C
        assertEquals(1, ChordQuality.MAJOR_SEVENTH.rootOffsetAboveBass(ChordInversion.THIRD)) // B up to C
    }

    @Test
    fun pitchClassNamesOmitTheOctave() {
        assertEquals("C", Pitch(60).pitchClassName)
        assertEquals("F♯", Pitch(54).pitchClassName)
    }
}
