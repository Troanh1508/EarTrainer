package com.example.eartrainer.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.eartrainer.music.ListeningPrompt
import com.example.eartrainer.music.Pitch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.ln

/** Decodes the bundled piano with the device's real codecs and checks every recording. */
@RunWith(AndroidJUnit4::class)
class UprightPianoSamplesTest {

    companion object {
        private lateinit var bank: SampleBank

        @BeforeClass
        @JvmStatic
        fun decodeOnce() {
            val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
            bank = runBlocking { AssetSampleLoader(assets).loadUprightPiano(maxSeconds = 3.0) }
        }
    }

    @Test
    fun everyRecordingDecodesAsAudibleMono44k() {
        assertEquals(UprightPianoKw.recordings.values.sorted(), bank.rootPitches)
        for (root in bank.rootPitches) {
            val sample = bank.sampleFor(root)
            assertEquals(44_100, sample.sampleRate)
            // At least 0.6 s each (the shortest recording), at most the 3 s cap.
            assertTrue("$root has ${sample.pcm.size} frames", sample.pcm.size in 26_000..132_300)
            assertTrue("$root is nearly silent", sample.peak > 3_000)
        }
    }

    /**
     * Checks each recording's actual pitch against the root in the sample map, using
     * autocorrelation around the expected period. A wrong map entry would be off by at least a
     * semitone (100 cents).
     */
    @Test
    fun recordingsAreAtTheirMappedPitch() {
        for (root in bank.rootPitches.filter { it in 36..96 }) {
            val sample = bank.sampleFor(root)
            val measured = estimateFrequency(sample.pcm, sample.sampleRate, start = sample.sampleRate / 5, expected = Pitch(root).frequencyHz)
            val cents = 1200 * ln(measured / Pitch(root).frequencyHz) / ln(2.0)
            assertTrue("MIDI $root measured %.1f Hz (%.0f cents off)".format(measured, cents), abs(cents) < 35)
        }
    }

    @Test
    fun rendersAHarmonicIntervalWithinHeadroom() {
        val schedule = NoteSchedule.from(ListeningPrompt.simultaneous(listOf(Pitch(48), Pitch(84))), sampleRate = 48_000)
        val pcm = SampledInstrumentRenderer(bank, headroom = 0.8).render(schedule)
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertTrue("peak $peak", peak in 5_000..(0.8 * Short.MAX_VALUE).toInt() + 1)
    }

    private fun estimateFrequency(pcm: ShortArray, sampleRate: Int, start: Int, expected: Double): Double {
        val window = 4_096
        val period = sampleRate / expected
        val lags = (period * 0.8).toInt()..(period * 1.25).toInt() + 1
        fun correlation(lag: Int): Double {
            var sum = 0.0
            for (i in start until start + window) sum += pcm[i].toDouble() * pcm[i + lag]
            return sum
        }
        val scores = lags.associateWith(::correlation)
        val best = scores.maxBy { it.value }.key
        // Parabolic interpolation around the best lag for sub-sample precision.
        val left = scores[best - 1] ?: correlation(best - 1)
        val centre = scores.getValue(best)
        val right = scores[best + 1] ?: correlation(best + 1)
        val offset = 0.5 * (left - right) / (left - 2 * centre + right)
        return sampleRate / (best + offset)
    }
}
