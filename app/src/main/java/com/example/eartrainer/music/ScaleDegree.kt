package com.example.eartrainer.music

/**
 * The twelve chromatic scale degrees, named relative to the major scale ("♭3" is a minor third
 * above the tonic) with movable-do solfège. Single source of truth for degree names.
 */
enum class ScaleDegree(val semitonesAboveTonic: Int, val label: String, val solfege: String) {
    TONIC(0, "1", "Do"),
    FLAT_SECOND(1, "♭2", "Ra"),
    SECOND(2, "2", "Re"),
    FLAT_THIRD(3, "♭3", "Me"),
    THIRD(4, "3", "Mi"),
    FOURTH(5, "4", "Fa"),
    SHARP_FOURTH(6, "♯4", "Fi"),
    FIFTH(7, "5", "Sol"),
    FLAT_SIXTH(8, "♭6", "Le"),
    SIXTH(9, "6", "La"),
    FLAT_SEVENTH(10, "♭7", "Te"),
    SEVENTH(11, "7", "Ti");

    companion object {
        fun fromSemitones(semitones: Int): ScaleDegree = entries.first { it.semitonesAboveTonic == semitones.mod(12) }
    }
}

enum class Mode(val displayName: String, val diatonicDegrees: List<ScaleDegree>) {
    MAJOR(
        "Major",
        listOf(ScaleDegree.TONIC, ScaleDegree.SECOND, ScaleDegree.THIRD, ScaleDegree.FOURTH, ScaleDegree.FIFTH, ScaleDegree.SIXTH, ScaleDegree.SEVENTH),
    ),

    /** Natural minor for the scale; cadences use the raised leading tone, as in common practice. */
    MINOR(
        "Minor",
        listOf(
            ScaleDegree.TONIC, ScaleDegree.SECOND, ScaleDegree.FLAT_THIRD, ScaleDegree.FOURTH,
            ScaleDegree.FIFTH, ScaleDegree.FLAT_SIXTH, ScaleDegree.FLAT_SEVENTH,
        ),
    ),
}

/** A key: a tonic pitch (which also fixes the register of cadences) and a mode. */
data class Key(val tonic: Pitch, val mode: Mode) {

    /** Conventional key name, e.g. "E♭ major" or "C♯ minor", using the usual flat or sharp spelling. */
    val name: String get() = "${tonicName()} ${mode.displayName.lowercase()}"

    fun pitchOf(degree: ScaleDegree): Pitch = tonic.transpose(degree.semitonesAboveTonic)

    /** The tonic triad with the root doubled an octave below. */
    fun tonicChord(): List<Pitch> = voiced(listOf(-12, 0, mode.third, 7))

    /**
     * I–IV–V–I (i–iv–V–i in minor) in a smooth keyboard voicing around the tonic, which firmly
     * establishes the key before a question.
     */
    fun cadence(): List<List<Pitch>> = listOf(
        voiced(listOf(-12, 0, mode.third, 7)),
        // IV (iv) in second inversion over its root: the tonic is held as a common tone.
        voiced(listOf(-7, 0, 5, 5 + mode.third)),
        // V in first inversion over its root, with the leading tone a semitone below the tonic.
        voiced(listOf(-5, -1, 2, 7)),
        voiced(listOf(-12, 0, mode.third, 7)),
    )

    private fun voiced(offsets: List<Int>): List<Pitch> = offsets.map(tonic::transpose)

    private val Mode.third: Int get() = if (this == Mode.MAJOR) 4 else 3

    private fun tonicName(): String {
        val names = when (mode) {
            Mode.MAJOR -> MAJOR_KEY_NAMES
            Mode.MINOR -> MINOR_KEY_NAMES
        }
        return names[tonic.midi % 12]
    }

    private companion object {
        val MAJOR_KEY_NAMES = listOf("C", "D♭", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B")
        val MINOR_KEY_NAMES = listOf("C", "C♯", "D", "E♭", "E", "F", "F♯", "G", "G♯", "A", "B♭", "B")
    }
}
