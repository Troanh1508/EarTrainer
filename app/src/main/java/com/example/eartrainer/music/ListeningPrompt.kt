package com.example.eartrainer.music

/**
 * What the listener hears, described musically rather than as audio: an ordered sequence of
 * moments, each sounding one or more pitches together.
 *
 * - Ascending melodic interval: `[[C4], [E4]]`
 * - Harmonic interval or block chord: `[[C4, E4, G4]]`
 * - Arpeggiated chord: `[[C4], [E4], [G4]]` with [Articulation.ARPEGGIATED]
 * - Scale degree: a cadence as [context], then `[[E4]]`
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
) {

    enum class Articulation {
        /** Each moment ends before the next one starts. */
        SEPARATE,

        /** Moments start in quick succession and keep ringing until the last one has sounded. */
        ARPEGGIATED,
    }

    init {
        require(moments.isNotEmpty()) { "A prompt needs at least one moment" }
        require(moments.all { it.isNotEmpty() } && context.all { it.isNotEmpty() }) { "Every moment must sound at least one pitch" }
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
