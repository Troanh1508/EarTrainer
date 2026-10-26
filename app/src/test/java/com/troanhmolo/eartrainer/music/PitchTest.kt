package com.troanhmolo.eartrainer.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PitchTest {

    @Test
    fun concertA_is440Hz() {
        assertEquals(440.0, Pitch(69).frequencyHz, 1e-9)
    }

    @Test
    fun octavesDoubleAndHalveFrequency() {
        assertEquals(880.0, Pitch(81).frequencyHz, 1e-9)
        assertEquals(220.0, Pitch(57).frequencyHz, 1e-9)
    }

    @Test
    fun middleC_matchesEqualTemperament() {
        assertEquals(261.6256, Pitch.MIDDLE_C.frequencyHz, 1e-4)
    }

    @Test
    fun semitoneRatioIsTwelfthRootOfTwo() {
        val ratio = Pitch(61).frequencyHz / Pitch(60).frequencyHz
        assertEquals(Math.pow(2.0, 1.0 / 12), ratio, 1e-12)
    }

    @Test
    fun midiRangeBoundsAreAcceptedAndOutsideRejected() {
        assertEquals(8.1758, Pitch(0).frequencyHz, 1e-4)
        assertEquals(12543.8540, Pitch(127).frequencyHz, 1e-3)
        assertThrows(IllegalArgumentException::class.java) { Pitch(-1) }
        assertThrows(IllegalArgumentException::class.java) { Pitch(128) }
    }

    @Test
    fun namesUseScientificPitchNotation() {
        assertEquals("C4", Pitch(60).name)
        assertEquals("A4", Pitch(69).name)
        assertEquals("F♯3", Pitch(54).name)
        assertEquals("C-1", Pitch(0).name)
        assertEquals("B3", Pitch(59).name)
    }

    @Test
    fun transposeMovesBySemitones() {
        assertEquals(Pitch(67), Pitch(60).transpose(7))
        assertEquals(Pitch(48), Pitch(60).transpose(-12))
        assertThrows(IllegalArgumentException::class.java) { Pitch(120).transpose(12) }
    }
}
