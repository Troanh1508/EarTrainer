package com.troanhmolo.eartrainer.music

import kotlin.math.pow

/**
 * A pitch identified by its MIDI note number (middle C = 60, concert A = 69).
 *
 * Pure Kotlin with no Android dependencies, so it can be shared with other platforms later.
 */
@JvmInline
value class Pitch(val midi: Int) : Comparable<Pitch> {

    init {
        require(midi in MIDI_RANGE) { "MIDI note $midi is outside $MIDI_RANGE" }
    }

    /** Twelve-tone equal temperament frequency, tuned to [CONCERT_A_HZ]. */
    val frequencyHz: Double
        get() = frequencyOf(midi)

    /** Scientific pitch notation using sharps, e.g. "C4", "F♯3". */
    val name: String
        get() = pitchClassName + octave

    /** Note name without octave, e.g. "F♯". */
    val pitchClassName: String
        get() = PITCH_CLASS_NAMES[midi % 12]

    /** Scientific pitch octave; MIDI 60 is C4. */
    val octave: Int
        get() = midi / 12 - 1

    fun transpose(semitones: Int): Pitch = Pitch(midi + semitones)

    override fun compareTo(other: Pitch): Int = midi.compareTo(other.midi)

    override fun toString(): String = name

    companion object {
        val MIDI_RANGE = 0..127
        const val CONCERT_A_MIDI = 69
        const val CONCERT_A_HZ = 440.0

        val MIDDLE_C = Pitch(60)

        private val PITCH_CLASS_NAMES =
            listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")

        fun frequencyOf(midi: Int, concertA: Double = CONCERT_A_HZ): Double =
            concertA * 2.0.pow((midi - CONCERT_A_MIDI) / 12.0)
    }
}
