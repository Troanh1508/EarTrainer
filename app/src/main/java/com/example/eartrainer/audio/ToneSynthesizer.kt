package com.example.eartrainer.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Amplitude shape of a single note. Raised-cosine attack and release start and end exactly at
 * zero, which is what keeps note boundaries free of clicks. Between them the level decays
 * gently towards [sustainLevel], giving a softer, more instrument-like tone than a flat sine.
 */
data class ToneEnvelope(
    val attackSeconds: Double = 0.012,
    val decaySeconds: Double = 0.45,
    val sustainLevel: Double = 0.55,
    val releaseSeconds: Double = 0.08,
) {
    fun gainAt(frame: Int, noteFrames: Int, sampleRate: Int): Double {
        if (frame !in 0 until noteFrames) return 0.0
        // Very short notes split the available frames between attack and release.
        val attackFrames = min((attackSeconds * sampleRate).roundToInt(), noteFrames / 2).coerceAtLeast(1)
        val releaseFrames = min((releaseSeconds * sampleRate).roundToInt(), noteFrames - attackFrames).coerceAtLeast(1)

        val seconds = frame.toDouble() / sampleRate
        val body = sustainLevel + (1 - sustainLevel) * exp(-seconds / decaySeconds)
        val attack = if (frame < attackFrames) raisedCosine(frame.toDouble() / attackFrames) else 1.0
        val framesLeft = noteFrames - 1 - frame
        val release = if (framesLeft < releaseFrames) raisedCosine(framesLeft.toDouble() / releaseFrames) else 1.0
        return body * attack * release
    }

    /** Smooth 0 → 1 ramp for x in [0, 1]. */
    private fun raisedCosine(x: Double) = 0.5 - 0.5 * cos(PI * x)
}

/**
 * Renders a [NoteSchedule] to mono 16-bit PCM. Pure Kotlin, so it is unit-testable and can be
 * swapped for a sampled or more elaborate synthesizer without touching playback code.
 */
class ToneSynthesizer(
    /**
     * Relative amplitudes of the fundamental and its overtones. A pure sine is hard to judge
     * in harmonic intervals; a few weak overtones make consonance and dissonance clearer.
     */
    private val harmonics: List<Double> = listOf(1.0, 0.25, 0.1),
    private val envelope: ToneEnvelope = ToneEnvelope(),
    /** Peak level (0..1) when [NoteSchedule.maxPolyphony] voices are at full amplitude. */
    private val headroom: Double = 0.8,
) {
    init {
        require(harmonics.isNotEmpty() && harmonics.all { it >= 0 } && harmonics.sum() > 0) {
            "Harmonic amplitudes must be non-negative with at least one positive value"
        }
        require(headroom > 0 && headroom <= 1) { "Headroom must be in (0, 1]" }
    }

    fun render(schedule: NoteSchedule): ShortArray {
        val sampleRate = schedule.sampleRate
        val mix = DoubleArray(schedule.totalFrames)
        // Dividing by polyphony keeps chords at the same overall loudness as single notes and
        // guarantees the mix can never exceed full scale.
        val voiceGain = headroom / schedule.maxPolyphony / harmonics.sum()
        val nyquist = sampleRate / 2.0

        for (note in schedule.notes) {
            val fundamental = note.pitch.frequencyHz
            // Overtones at or above Nyquist would alias into audible, inharmonic tones.
            val partials = harmonics.withIndex()
                .filter { (index, amplitude) -> amplitude > 0 && fundamental * (index + 1) < nyquist }
            val multipliers = IntArray(partials.size) { partials[it].index + 1 }
            val amplitudes = DoubleArray(partials.size) { partials[it].value }
            val radiansPerFrame = 2 * PI * fundamental / sampleRate

            for (i in 0 until note.frameCount) {
                val target = note.startFrame + i
                if (target >= mix.size) break
                val phase = radiansPerFrame * i
                var sample = 0.0
                for (p in multipliers.indices) sample += amplitudes[p] * sin(phase * multipliers[p])
                mix[target] += sample * voiceGain * envelope.gainAt(i, note.frameCount, sampleRate)
            }
        }

        return ShortArray(mix.size) { i ->
            (mix[i] * Short.MAX_VALUE).roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }
}
