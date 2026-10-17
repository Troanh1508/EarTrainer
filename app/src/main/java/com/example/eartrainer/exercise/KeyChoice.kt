package com.example.eartrainer.exercise

import com.example.eartrainer.music.Pitch
import kotlin.random.Random

/** How exercises that work in a key choose it. */
enum class KeyChoice(val displayName: String) {
    RANDOM("Random key"),
    C_ONLY("Always C");

    fun pickTonic(random: Random): Pitch = when (this) {
        RANDOM -> Pitch(random.nextInt(TONIC_RANGE.first, TONIC_RANGE.last + 1))
        C_ONLY -> C_TONIC
    }

    companion object {
        /**
         * Tonics are drawn from G3..F♯4: every key is available, and both key-setting chords
         * (down to about an octave below the tonic) and notes above it stay in a clear register.
         */
        val TONIC_RANGE = 55..66
        val C_TONIC = Pitch(60)
    }
}
