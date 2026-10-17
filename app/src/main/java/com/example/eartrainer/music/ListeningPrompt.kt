package com.example.eartrainer.music

/**
 * What the listener hears, described musically rather than as audio: an ordered sequence of
 * moments, each sounding one or more pitches together.
 *
 * - Ascending melodic interval: `[[C4], [E4]]`
 * - Harmonic interval: `[[C4, E4]]`
 * - A chord progression later becomes several multi-pitch moments.
 *
 * Exact timing (durations, gaps) is decided by the audio layer.
 */
data class ListeningPrompt(val moments: List<List<Pitch>>) {

    init {
        require(moments.isNotEmpty()) { "A prompt needs at least one moment" }
        require(moments.all { it.isNotEmpty() }) { "Every moment must sound at least one pitch" }
    }

    companion object {
        /** Pitches played one after another. */
        fun sequential(pitches: List<Pitch>): ListeningPrompt =
            ListeningPrompt(pitches.map { listOf(it) })

        /** Pitches played together as a single moment. */
        fun simultaneous(pitches: List<Pitch>): ListeningPrompt =
            ListeningPrompt(listOf(pitches))
    }
}
