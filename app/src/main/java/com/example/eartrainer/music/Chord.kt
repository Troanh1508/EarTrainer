package com.example.eartrainer.music

/**
 * Chord qualities as semitone offsets of each chord tone above the root, in root position.
 * Single source of truth for chord names, symbols and spellings.
 */
enum class ChordQuality(
    val semitonesAboveRoot: List<Int>,
    val displayName: String,
    /** Suffix after the root in chord symbols, e.g. "m7" in "Cm7". */
    val symbol: String,
) {
    MAJOR(listOf(0, 4, 7), "Major", ""),
    MINOR(listOf(0, 3, 7), "Minor", "m"),
    DIMINISHED(listOf(0, 3, 6), "Diminished", "dim"),
    AUGMENTED(listOf(0, 4, 8), "Augmented", "aug"),
    MAJOR_SEVENTH(listOf(0, 4, 7, 11), "Major 7th", "maj7"),
    DOMINANT_SEVENTH(listOf(0, 4, 7, 10), "Dominant 7th", "7"),
    MINOR_SEVENTH(listOf(0, 3, 7, 10), "Minor 7th", "m7"),
    HALF_DIMINISHED_SEVENTH(listOf(0, 3, 6, 10), "Half-diminished 7th", "m7♭5"),
    DIMINISHED_SEVENTH(listOf(0, 3, 6, 9), "Diminished 7th", "dim7");

    val noteCount: Int get() = semitonesAboveRoot.size
    val isSeventh: Boolean get() = noteCount == 4

    /** Inversions this chord can take: three for triads, four for seventh chords. */
    val inversions: List<ChordInversion> get() = ChordInversion.entries.take(noteCount)

    /**
     * Close-position shape for [inversion], as semitone offsets above the bass note. Each
     * inversion moves the lowest chord tone up an octave, e.g. a major triad in first inversion
     * is E–G–C: [0, 3, 8].
     */
    fun offsetsAboveBass(inversion: ChordInversion): List<Int> {
        require(inversion in inversions) { "$displayName chords have no ${inversion.displayName.lowercase()}" }
        val raised = semitonesAboveRoot.drop(inversion.ordinal) + semitonesAboveRoot.take(inversion.ordinal).map { it + 12 }
        return raised.map { it - raised.first() }
    }

    /** Semitones from the bass note up to the root in [inversion]'s close-position shape. */
    fun rootOffsetAboveBass(inversion: ChordInversion): Int = offsetsAboveBass(inversion)[(noteCount - inversion.ordinal) % noteCount]
}

/** Which chord tone is in the bass. */
enum class ChordInversion(val displayName: String) {
    ROOT_POSITION("Root position"),
    FIRST("1st inversion"),
    SECOND("2nd inversion"),
    THIRD("3rd inversion"),
}
