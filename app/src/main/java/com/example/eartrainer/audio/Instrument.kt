package com.example.eartrainer.audio

/** Recordings bundled in assets: file names (without ".flac") mapped to their MIDI root pitch. */
class SampleSet(val assetDir: String, val recordings: Map<String, Int>)

/**
 * The sampled instruments prompts can be played with. Both come from the FreePats project under
 * CC0 (public domain); see the README and license next to each set of recordings
 * in shared/assets.
 */
enum class Instrument(val displayName: String, internal val samples: SampleSet) {
    /** "Upright Piano KW" (small), recorded roughly every minor third; roots from its SFZ map. */
    PIANO(
        "Piano",
        SampleSet(
            "instruments/upright-piano-kw",
            mapOf(
                "C1" to 24, "Fs1" to 30, "B1" to 35, "Ds2" to 39, "Fs2" to 42, "B2" to 47,
                "Ds3" to 51, "Fs3" to 54, "A3" to 57, "C4" to 60, "Ds4" to 63, "Fs4" to 66,
                "A4" to 69, "C5" to 72, "Ds5" to 75, "Fs5" to 78, "A5" to 81, "C6" to 84,
                "Ds6" to 87, "Fs6" to 90, "A6" to 93, "C7" to 96, "Ds7" to 99, "Fs7" to 102,
                "A7" to 105, "B7" to 107,
            ),
        ),
    ),

    /**
     * "Spanish Classical Guitar" (nylon strings): a subset of its recordings, about every three
     * semitones over the guitar's range (G1 to C6), so no note is shifted more than a semitone
     * and a half. Notes above the range, such as the rhythm count-in, are shifted further.
     */
    GUITAR(
        "Guitar",
        SampleSet(
            "instruments/spanish-classical-guitar",
            mapOf(
                "G1" to 31, "As1" to 34, "Cs2" to 37, "E2" to 40, "G2" to 43, "A2" to 45,
                "C3" to 48, "D3" to 50, "E3" to 52, "G3" to 55, "A3" to 57, "C4" to 60,
                "Ds4" to 63, "Fs4" to 66, "A4" to 69, "C5" to 72, "Ds5" to 75, "Fs5" to 78,
                "A5" to 81, "C6" to 84,
            ),
        ),
    ),
}

/** App-wide sound preferences; unlike exercise settings they apply to every exercise. */
data class SoundSettings(val instrument: Instrument = Instrument.PIANO)
