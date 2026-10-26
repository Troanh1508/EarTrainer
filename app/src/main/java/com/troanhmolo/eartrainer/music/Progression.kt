package com.troanhmolo.eartrainer.music

/**
 * A triad built on one step of a key's scale, named with a Roman numeral (upper case for major,
 * lower case for minor, ° for diminished).
 *
 * Minor keys use the natural minor scale except for V, which takes the raised leading tone as in
 * common-practice harmony, so a minor-key cadence still pulls home.
 */
data class DiatonicChord(val mode: Mode, val step: Int) {

    init {
        require(step in 1..7) { "Scale step must be 1..7, was $step" }
    }

    val rootSemitonesAboveTonic: Int get() = roots(mode)[step - 1]
    val quality: ChordQuality get() = qualities(mode)[step - 1]
    val numeral: String get() = numerals(mode)[step - 1]

    /** Chord symbol in [key], e.g. "Am" for vi in C major or "A♭" for IV in E♭ major. */
    fun symbolIn(key: Key): String = key.noteName(key.tonic.transpose(rootSemitonesAboveTonic)) + quality.symbol

    /** Pitch classes of the chord tones, as semitones above the tonic. */
    val toneSemitones: List<Int> get() = quality.semitonesAboveRoot.map { (rootSemitonesAboveTonic + it) % 12 }

    companion object {
        fun all(mode: Mode): List<DiatonicChord> = (1..7).map { DiatonicChord(mode, it) }

        private fun roots(mode: Mode) = when (mode) {
            Mode.MAJOR -> listOf(0, 2, 4, 5, 7, 9, 11)
            Mode.MINOR -> listOf(0, 2, 3, 5, 7, 8, 10)
        }

        private fun qualities(mode: Mode) = when (mode) {
            Mode.MAJOR -> listOf(MAJ, MIN, MIN, MAJ, MAJ, MIN, DIM)
            Mode.MINOR -> listOf(MIN, DIM, MAJ, MIN, MAJ, MAJ, MAJ)
        }

        private fun numerals(mode: Mode) = when (mode) {
            Mode.MAJOR -> listOf("I", "ii", "iii", "IV", "V", "vi", "vii°")
            Mode.MINOR -> listOf("i", "ii°", "III", "iv", "V", "VI", "VII")
        }

        private val MAJ = ChordQuality.MAJOR
        private val MIN = ChordQuality.MINOR
        private val DIM = ChordQuality.DIMINISHED
    }
}

/** A sequence of diatonic chords in one mode, e.g. I – vi – IV – V. */
data class ChordProgression(val chords: List<DiatonicChord>) {

    init {
        require(chords.isNotEmpty()) { "A progression needs at least one chord" }
        require(chords.all { it.mode == chords.first().mode }) { "All chords must be in the same mode" }
    }

    val mode: Mode get() = chords.first().mode

    /** e.g. "I – vi – IV – V". */
    val label: String get() = chords.joinToString(" – ") { it.numeral }

    /** e.g. "C – Am – F – G" in C major. */
    fun symbolsIn(key: Key): String = chords.joinToString(" – ") { it.symbolIn(key) }

    /**
     * Keyboard voicing in [key]: each chord's root in the bass below the tonic, and its three
     * tones in the same one-octave window above. Keeping every chord in one window makes common
     * tones stay put and other voices move by small steps, much like a pianist would play it.
     */
    fun voicedIn(key: Key): List<List<Pitch>> = chords.map { chord ->
        val bass = key.tonic.transpose(BASS_LOWEST + (chord.rootSemitonesAboveTonic - BASS_LOWEST).mod(12))
        val upper = chord.toneSemitones.map { key.tonic.transpose(UPPER_LOWEST + (it - UPPER_LOWEST).mod(12)) }.sorted()
        listOf(bass) + upper
    }

    private companion object {
        /** Bass roots fall in the twelve semitones from 16 below the tonic up to 5 below it. */
        const val BASS_LOWEST = -16

        /** Upper voices fall in the twelve semitones from 3 below the tonic up to 8 above it. */
        const val UPPER_LOWEST = -3
    }
}
