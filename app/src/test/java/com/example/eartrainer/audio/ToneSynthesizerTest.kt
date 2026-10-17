package com.example.eartrainer.audio

import com.example.eartrainer.music.ListeningPrompt
import com.example.eartrainer.music.Pitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ToneSynthesizerTest {

    private val sampleRate = 44_100

    private fun schedule(prompt: ListeningPrompt) = NoteSchedule.from(prompt, sampleRate)

    private val melodic = schedule(ListeningPrompt.sequential(listOf(Pitch(60), Pitch(67))))
    private val harmonic = schedule(ListeningPrompt.simultaneous(listOf(Pitch(60), Pitch(67))))

    @Test
    fun outputLengthMatchesSchedule() {
        assertEquals(melodic.totalFrames, ToneSynthesizer().render(melodic).size)
    }

    @Test
    fun envelopeStartsAndEndsAtSilence() {
        val envelope = ToneEnvelope()
        val frames = 40_000
        assertEquals(0.0, envelope.gainAt(0, frames, sampleRate), 1e-12)
        assertEquals(0.0, envelope.gainAt(frames - 1, frames, sampleRate), 1e-12)
        assertEquals(0.0, envelope.gainAt(frames, frames, sampleRate), 1e-12)
        val gains = (0 until frames).map { envelope.gainAt(it, frames, sampleRate) }
        assertTrue(gains.all { it in 0.0..1.0 })
        val attackFrames = (0.012 * sampleRate).toInt()
        assertTrue("attack must rise monotonically", gains.take(attackFrames).zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun noteBoundariesAreClickFree() {
        val pcm = ToneSynthesizer().render(melodic)
        for (note in melodic.notes) {
            assertTrue(abs(pcm[note.startFrame].toInt()) <= 1)
            assertTrue(abs(pcm[note.endFrame - 1].toInt()) <= 1)
        }
        // Lead-in, gap and tail must be true silence.
        val silentRanges = listOf(
            0 until melodic.notes[0].startFrame,
            melodic.notes[0].endFrame until melodic.notes[1].startFrame,
            melodic.contentEndFrame until melodic.totalFrames,
        )
        silentRanges.forEach { range -> assertTrue(range.all { pcm[it] == 0.toShort() }) }
    }

    @Test
    fun noAbruptJumpsAnywhereInTheSignal() {
        // The highest note here is G4 (≈392 Hz). At 0.8 of full scale a sine changes by at most
        // 2π·f/sampleRate·0.8·32767 ≈ 1,460 per sample; a discontinuity would be far larger.
        val pcm = ToneSynthesizer(harmonics = listOf(1.0)).render(melodic)
        val largestStep = pcm.toList().zipWithNext().maxOf { (a, b) -> abs(b - a) }
        assertTrue("largest step $largestStep", largestStep < 1_600)
    }

    @Test
    fun pureToneHasExpectedFrequency() {
        val schedule = schedule(ListeningPrompt.sequential(listOf(Pitch(69))))
        val pcm = ToneSynthesizer(harmonics = listOf(1.0)).render(schedule)
        val note = schedule.notes.single()
        // Count rising zero crossings across half a second inside the note body.
        val window = (note.startFrame + 2_000) until (note.startFrame + 2_000 + sampleRate / 2)
        val risingCrossings = window.count { pcm[it - 1] < 0 && pcm[it] >= 0 }
        assertEquals(220.0, risingCrossings.toDouble(), 1.0) // 440 Hz over half a second
    }

    @Test
    fun simultaneousNotesStayWithinHeadroomWithoutClipping() {
        val pcm = ToneSynthesizer(headroom = 0.8).render(harmonic)
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertTrue("peak $peak", peak <= (0.8 * Short.MAX_VALUE).toInt() + 1)
        assertTrue("signal should be clearly audible, peak $peak", peak > Short.MAX_VALUE / 4)
    }

    @Test
    fun renderingIsDeterministic() {
        val synth = ToneSynthesizer()
        assertTrue(synth.render(harmonic).contentEquals(synth.render(harmonic)))
    }
}
