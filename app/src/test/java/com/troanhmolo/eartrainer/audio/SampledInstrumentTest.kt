package com.troanhmolo.eartrainer.audio

import com.troanhmolo.eartrainer.music.ListeningPrompt
import com.troanhmolo.eartrainer.music.Pitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

class SampledInstrumentTest {

    private val recordingRate = 44_100
    private val outputRate = 48_000

    /** A pure sine "recording" of [rootMidi], so pitch can be measured by zero crossings. */
    private fun sineSample(rootMidi: Int, seconds: Double = 3.0, amplitude: Double = 0.5): InstrumentSample {
        val frequency = Pitch(rootMidi).frequencyHz
        val pcm = ShortArray((seconds * recordingRate).toInt()) { i ->
            (amplitude * Short.MAX_VALUE * sin(2 * PI * frequency * i / recordingRate)).roundToInt().toShort()
        }
        return InstrumentSample(rootMidi, recordingRate, pcm)
    }

    private fun schedule(prompt: ListeningPrompt) = NoteSchedule.from(prompt, outputRate)

    private fun measuredFrequency(pcm: ShortArray, from: Int, frames: Int): Double {
        val rising = (from + 1 until from + frames).count { pcm[it - 1] < 0 && pcm[it] >= 0 }
        return rising * outputRate.toDouble() / frames
    }

    @Test
    fun bankPicksTheNearestRecordingAndPrefersTheLowerOnTies() {
        val bank = SampleBank(listOf(sineSample(60, 0.1), sineSample(66, 0.1), sineSample(63, 0.1)))
        assertEquals(listOf(60, 63, 66), bank.rootPitches)
        assertEquals(60, bank.sampleFor(48).rootMidi)
        assertEquals(63, bank.sampleFor(64).rootMidi)
        assertEquals(66, bank.sampleFor(90).rootMidi)
        assertSame(bank.sampleFor(61), bank.sampleFor(60))
        assertEquals(60, SampleBank(listOf(sineSample(60, 0.1), sineSample(62, 0.1))).sampleFor(61).rootMidi)
    }

    @Test
    fun bankRejectsDuplicateRoots() {
        assertThrows(IllegalArgumentException::class.java) { SampleBank(listOf(sineSample(60, 0.1), sineSample(60, 0.1))) }
    }

    @Test
    fun recordingIsResampledToTheOutputRateWithoutChangingPitch() {
        val prompt = ListeningPrompt.sequential(listOf(Pitch(69)))
        val s = schedule(prompt)
        val pcm = SampledInstrumentRenderer(SampleBank(listOf(sineSample(69)))).render(s)
        val note = s.notes.single()
        assertEquals(440.0, measuredFrequency(pcm, note.startFrame + 2_000, outputRate / 2), 4.0)
    }

    @Test
    fun neighboringNotesArePitchShiftedFromTheNearestRecording() {
        val bank = SampleBank(listOf(sineSample(57), sineSample(60), sineSample(63)))
        for (midi in listOf(58, 59, 61, 62)) {
            val s = schedule(ListeningPrompt.sequential(listOf(Pitch(midi))))
            val pcm = SampledInstrumentRenderer(bank).render(s)
            val expected = Pitch(midi).frequencyHz
            // Half a second of a few-hundred-Hz tone: ±2 crossings is about ±1%, far below a semitone (6%).
            assertEquals("midi $midi", expected, measuredFrequency(pcm, s.notes.single().startFrame + 2_000, outputRate / 2), expected * 0.01)
        }
    }

    @Test
    fun notesStartAndEndInSilenceAndGapsStaySilent() {
        val s = schedule(ListeningPrompt.sequential(listOf(Pitch(60), Pitch(67))))
        val pcm = SampledInstrumentRenderer(SampleBank(listOf(sineSample(60), sineSample(67)))).render(s)
        for (note in s.notes) {
            assertTrue(abs(pcm[note.startFrame].toInt()) <= 1)
            assertTrue(abs(pcm[note.endFrame - 1].toInt()) <= 1)
        }
        assertTrue((s.notes[0].endFrame until s.notes[1].startFrame).all { pcm[it] == 0.toShort() })
        assertTrue((s.contentEndFrame until s.totalFrames).all { pcm[it] == 0.toShort() })
    }

    @Test
    fun shortRecordingsEndEarlyWithAFadeRatherThanACut() {
        val s = schedule(ListeningPrompt.sequential(listOf(Pitch(60))))
        val pcm = SampledInstrumentRenderer(SampleBank(listOf(sineSample(60, seconds = 0.4)))).render(s)
        val note = s.notes.single()
        val lastSounding = (note.startFrame until note.endFrame).last { pcm[it] != 0.toShort() }
        val soundingSeconds = (lastSounding - note.startFrame).toDouble() / outputRate
        assertEquals(0.4, soundingSeconds, 0.01)
        assertTrue("tail should be faded", abs(pcm[lastSounding].toInt()) < 200)
    }

    @Test
    fun recordingsAreLeveledAndChordsStayWithinHeadroom() {
        val quiet = sineSample(60, amplitude = 0.1)
        val loud = sineSample(64, amplitude = 0.9)
        val renderer = SampledInstrumentRenderer(SampleBank(listOf(quiet, loud)), headroom = 0.8)

        val melodic = schedule(ListeningPrompt.sequential(listOf(Pitch(60), Pitch(64))))
        val pcm = renderer.render(melodic)
        fun peakOf(note: ScheduledNote) = (note.startFrame until note.endFrame).maxOf { abs(pcm[it].toInt()) }
        val (first, second) = melodic.notes.map(::peakOf)
        assertEquals("both notes should reach the same level", first.toDouble(), second.toDouble(), first * 0.05)

        val chord = renderer.render(schedule(ListeningPrompt.simultaneous(listOf(Pitch(60), Pitch(64)))))
        assertTrue(chord.maxOf { abs(it.toInt()) } <= (0.8 * Short.MAX_VALUE).toInt() + 1)
    }
}
