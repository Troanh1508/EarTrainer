package com.example.eartrainer.music

import kotlin.math.abs

/**
 * Simple intervals up to an octave. This enum is the single source of truth for interval
 * names and semitone distances.
 */
enum class Interval(
    val semitones: Int,
    val displayName: String,
    val abbreviation: String,
) {
    MINOR_SECOND(1, "Minor second", "m2"),
    MAJOR_SECOND(2, "Major second", "M2"),
    MINOR_THIRD(3, "Minor third", "m3"),
    MAJOR_THIRD(4, "Major third", "M3"),
    PERFECT_FOURTH(5, "Perfect fourth", "P4"),
    TRITONE(6, "Tritone", "TT"),
    PERFECT_FIFTH(7, "Perfect fifth", "P5"),
    MINOR_SIXTH(8, "Minor sixth", "m6"),
    MAJOR_SIXTH(9, "Major sixth", "M6"),
    MINOR_SEVENTH(10, "Minor seventh", "m7"),
    MAJOR_SEVENTH(11, "Major seventh", "M7"),
    OCTAVE(12, "Octave", "P8");

    companion object {
        private val bySemitones = entries.associateBy { it.semitones }

        fun fromSemitones(semitones: Int): Interval? = bySemitones[semitones]

        /** The interval between two pitches regardless of their order, if it is a simple interval. */
        fun between(first: Pitch, second: Pitch): Interval? =
            fromSemitones(abs(second.midi - first.midi))
    }
}
