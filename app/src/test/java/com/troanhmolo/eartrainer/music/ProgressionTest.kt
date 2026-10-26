package com.troanhmolo.eartrainer.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {

    private fun chords(mode: Mode, vararg steps: Int) = ChordProgression(steps.map { DiatonicChord(mode, it) })

    @Test
    fun diatonicTriadsHaveTheRightNumeralsAndQualities() {
        assertEquals(listOf("I", "ii", "iii", "IV", "V", "vi", "vii°"), DiatonicChord.all(Mode.MAJOR).map { it.numeral })
        assertEquals(listOf("i", "ii°", "III", "iv", "V", "VI", "VII"), DiatonicChord.all(Mode.MINOR).map { it.numeral })
        assertEquals(ChordQuality.DIMINISHED, DiatonicChord(Mode.MAJOR, 7).quality)
        // Minor keys borrow the raised leading tone for a major V.
        assertEquals(ChordQuality.MAJOR, DiatonicChord(Mode.MINOR, 5).quality)
        assertEquals(listOf(7, 11, 2), DiatonicChord(Mode.MINOR, 5).toneSemitones)
        assertThrows(IllegalArgumentException::class.java) { DiatonicChord(Mode.MAJOR, 8) }
    }

    @Test
    fun everyDiatonicChordUsesOnlyScaleTonesExceptMinorV() {
        Mode.entries.forEach { mode ->
            val scale = mode.diatonicDegrees.map { it.semitonesAboveTonic }.toSet()
            DiatonicChord.all(mode).filterNot { mode == Mode.MINOR && it.step == 5 }.forEach { chord ->
                assertTrue("$mode ${chord.numeral}", scale.containsAll(chord.toneSemitones))
            }
        }
    }

    @Test
    fun labelsAndSymbolsUseTheKeysSpelling() {
        val progression = chords(Mode.MAJOR, 1, 6, 4, 5)
        assertEquals("I – vi – IV – V", progression.label)
        assertEquals("C – Am – F – G", progression.symbolsIn(Key(Pitch(60), Mode.MAJOR)))
        assertEquals("E♭ – Cm – A♭ – B♭", progression.symbolsIn(Key(Pitch(63), Mode.MAJOR)))
        assertEquals("F♯m – Bm – C♯", chords(Mode.MINOR, 1, 4, 5).symbolsIn(Key(Pitch(66), Mode.MINOR)))
        assertEquals("Bdim", DiatonicChord(Mode.MAJOR, 7).symbolIn(Key(Pitch(60), Mode.MAJOR)))
    }

    @Test
    fun voicingPutsTheRootInTheBassAndChordTonesInOneWindowAbove() {
        val key = Key(Pitch(60), Mode.MAJOR)
        val progression = chords(Mode.MAJOR, 1, 6, 4, 5, 7)
        val voiced = progression.voicedIn(key)
        voiced.zip(progression.chords).forEach { (pitches, chord) ->
            val bass = pitches.first()
            assertEquals(chord.rootSemitonesAboveTonic, (bass.midi - key.tonic.midi).mod(12))
            assertTrue(bass.midi in 44..55)
            val upper = pitches.drop(1)
            assertEquals(chord.toneSemitones.toSet(), upper.map { (it.midi - key.tonic.midi).mod(12) }.toSet())
            assertTrue(upper.all { it.midi in 57..68 })
        }
        // I in C major: C3 in the bass under C4-E4-G4.
        assertEquals(listOf(48, 60, 64, 67), voiced.first().map { it.midi })
    }

    @Test
    fun progressionsStayInOneMode() {
        assertThrows(IllegalArgumentException::class.java) {
            ChordProgression(listOf(DiatonicChord(Mode.MAJOR, 1), DiatonicChord(Mode.MINOR, 4)))
        }
    }
}
