package com.example.eartrainer.audio

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/** One recorded note: mono 16-bit PCM and the MIDI pitch it was recorded at. */
class InstrumentSample(val rootMidi: Int, val sampleRate: Int, val pcm: ShortArray) {
    init {
        require(sampleRate > 0) { "Sample rate must be positive" }
        require(pcm.size >= 4) { "A sample needs at least four frames for interpolation" }
    }

    /** Loudest absolute value; recordings are scaled by it so every note plays at the same level. */
    val peak: Int = pcm.maxOf { abs(it.toInt()) }.coerceAtLeast(1)

    /**
     * Value at a fractional frame position, using 4-point Catmull-Rom interpolation. It keeps
     * noticeably more high-frequency detail than linear interpolation when a recording is
     * pitch-shifted or resampled to the output rate.
     */
    internal fun valueAt(position: Double): Double {
        val index = floor(position).toInt()
        val t = position - index
        val y0 = pcm[(index - 1).coerceAtLeast(0)].toDouble()
        val y1 = pcm[index].toDouble()
        val y2 = pcm[index + 1].toDouble()
        val y3 = pcm[index + 2].toDouble()
        val c1 = 0.5 * (y2 - y0)
        val c2 = y0 - 2.5 * y1 + 2 * y2 - 0.5 * y3
        val c3 = 0.5 * (y3 - y0) + 1.5 * (y1 - y2)
        return ((c3 * t + c2) * t + c1) * t + y1
    }

    /** Last position [valueAt] can read, leaving room for the two frames ahead it needs. */
    internal val lastReadablePosition: Int get() = pcm.size - 3
}

/** Recordings covering a pitch range; each note is played from the closest recording. */
class SampleBank(samples: List<InstrumentSample>) {
    private val samples = samples.sortedBy { it.rootMidi }

    init {
        require(samples.isNotEmpty()) { "A sample bank needs at least one recording" }
        require(samples.map { it.rootMidi }.toSet().size == samples.size) { "Each recording needs a distinct root pitch" }
    }

    val rootPitches: List<Int> get() = samples.map { it.rootMidi }

    /** Nearest recording by pitch; on a tie the lower one wins (list is sorted, minBy keeps the first). */
    fun sampleFor(midi: Int): InstrumentSample = samples.minBy { abs(it.rootMidi - midi) }
}

/**
 * Plays notes from recorded samples. Each note reads the nearest recording at a rate that both
 * shifts it to the requested pitch and converts its sample rate to the output rate, so one
 * recording serves the neighbouring semitones.
 */
class SampledInstrumentRenderer(
    private val bank: SampleBank,
    /** Damper-like fade at the end of each note; shorter than a real piano so notes stay distinct. */
    private val releaseSeconds: Double = 0.25,
    /** Peak level (0..1) when [NoteSchedule.maxPolyphony] voices are at full amplitude. */
    private val headroom: Double = 0.8,
) : PcmRenderer {

    init {
        require(releaseSeconds > 0) { "Release must be positive" }
        require(headroom > 0 && headroom <= 1) { "Headroom must be in (0, 1]" }
    }

    override fun render(schedule: NoteSchedule): ShortArray {
        val outputRate = schedule.sampleRate
        val mix = DoubleArray(schedule.totalFrames)
        // Same gain staging as the synthesizer: normalised recordings divided across voices can
        // never sum past full scale.
        val voiceGain = headroom / schedule.maxPolyphony
        val fadeInFrames = (FADE_IN_SECONDS * outputRate).roundToInt().coerceAtLeast(1)

        for (note in schedule.notes) {
            val sample = bank.sampleFor(note.pitch.midi)
            val step = 2.0.pow((note.pitch.midi - sample.rootMidi) / 12.0) * sample.sampleRate / outputRate
            // A note ends early if its recording runs out; the release then fades the tail instead
            // of cutting it off mid-waveform.
            val playable = (sample.lastReadablePosition / step).toInt()
            val length = minOf(note.frameCount, playable, mix.size - note.startFrame)
            if (length <= 0) continue
            val releaseFrames = (releaseSeconds * outputRate).roundToInt().coerceIn(1, length)
            val gain = voiceGain / sample.peak

            for (i in 0 until length) {
                val fadeIn = if (i < fadeInFrames) raisedCosine(i.toDouble() / fadeInFrames) else 1.0
                val framesLeft = length - 1 - i
                val release = if (framesLeft < releaseFrames) raisedCosine(framesLeft.toDouble() / releaseFrames) else 1.0
                mix[note.startFrame + i] += sample.valueAt(i * step) * gain * fadeIn * release
            }
        }
        return mix.toPcm16()
    }

    private companion object {
        /**
         * Recordings start at the hammer strike, so only a tiny fade-in is needed to guarantee the
         * first frame is exactly zero without softening the attack.
         */
        const val FADE_IN_SECONDS = 0.002
    }
}
