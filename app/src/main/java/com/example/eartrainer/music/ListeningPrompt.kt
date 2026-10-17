package com.example.eartrainer.music

/**
 * What the listener hears, described musically rather than as audio: an ordered sequence of
 * moments, each sounding one or more pitches together.
 *
 * - Ascending melodic interval: `[[C4], [E4]]`
 * - Harmonic interval or block chord: `[[C4, E4, G4]]`
 * - Arpeggiated chord: `[[C4], [E4], [G4]]` with [Articulation.ARPEGGIATED]
 * - Scale degree: a cadence as [context], then `[[E4]]`
 * - Chord progression: one multi-pitch moment per chord with [Articulation.FLOWING]
 * - Melody: one single-pitch moment per note with [Articulation.MELODIC], usually after a [context]
 * - Rhythm: one moment per note with exact [beatTiming] at a tempo, which overrides [articulation]
 *
 * Exact timing (durations, gaps) is decided by the audio layer.
 */
data class ListeningPrompt(
    val moments: List<List<Pitch>>,
    val articulation: Articulation = Articulation.SEPARATE,
    /**
     * Moments that set the scene before the question itself, such as a cadence establishing a
     * key. Played briskly, then a pause separates them from [moments].
     */
    val context: List<List<Pitch>> = emptyList(),
    /** Exact placement in beats at a tempo; when set, [articulation] is ignored. */
    val beatTiming: BeatTiming? = null,
) {

    /**
     * Moment i starts [startBeats] i beats in and lasts [lengthBeats] i beats, at [bpm] beats per
     * minute. Rests are simply gaps between moments.
     */
    data class BeatTiming(val bpm: Int, val startBeats: List<Double>, val lengthBeats: List<Double>) {
        init {
            require(bpm > 0) { "Tempo must be positive" }
            require(startBeats.size == lengthBeats.size) { "Every moment needs a start and a length" }
            require(startBeats.all { it >= 0 } && lengthBeats.all { it > 0 }) { "Starts must not be negative and lengths must be positive" }
        }
    }

    enum class Articulation {
        /** Each moment ends before the next one starts. */
        SEPARATE,

        /** Moments start in quick succession and keep ringing until the last one has sounded. */
        ARPEGGIATED,

        /** Moments follow each other without gaps at a moderate pace, as in a chord progression. */
        FLOWING,

        /** Single notes of a tune, back to back at a walking pace. */
        MELODIC,
    }

    init {
        require(moments.isNotEmpty()) { "A prompt needs at least one moment" }
        require(moments.all { it.isNotEmpty() } && context.all { it.isNotEmpty() }) { "Every moment must sound at least one pitch" }
        require(beatTiming == null || beatTiming.startBeats.size == moments.size) { "Beat timing must cover every moment" }
    }

    companion object {
        /** Pitches played one after another. */
        fun sequential(pitches: List<Pitch>): ListeningPrompt =
            ListeningPrompt(pitches.map { listOf(it) })

        /** Pitches played together as a single moment. */
        fun simultaneous(pitches: List<Pitch>): ListeningPrompt =
            ListeningPrompt(listOf(pitches))

        /** Pitches played from first to last, each held so the chord sounds whole at the end. */
        fun arpeggiated(pitches: List<Pitch>): ListeningPrompt =
            ListeningPrompt(pitches.map { listOf(it) }, Articulation.ARPEGGIATED)
    }
}
