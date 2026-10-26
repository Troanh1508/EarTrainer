package com.troanhmolo.eartrainer.music

/** A tune written as scale degrees, e.g. 1 – 2 – 3 – 1, independent of key. */
data class Melody(val degrees: List<ScaleDegree>) {

    init {
        require(degrees.isNotEmpty()) { "A melody needs at least one note" }
    }

    /** e.g. "1 – 2 – ♭3 – 1". */
    val label: String get() = degrees.joinToString(" – ") { it.label }

    /** e.g. "Do Re Me Do". */
    val solfege: String get() = degrees.joinToString(" ") { it.solfege }

    /** The notes in [key], each in the octave from the tonic up. */
    fun pitchesIn(key: Key): List<Pitch> = degrees.map(key::pitchOf)
}
