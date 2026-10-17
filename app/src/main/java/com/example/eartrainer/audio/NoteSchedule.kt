package com.example.eartrainer.audio

import com.example.eartrainer.music.ListeningPrompt
import com.example.eartrainer.music.Pitch
import kotlin.math.roundToInt

/** Musical timing used to turn a [ListeningPrompt] into sample-accurate note positions. */
data class PlaybackTiming(
    /** Length of a single-note moment, including its release. */
    val noteSeconds: Double = 0.9,
    /** Length of a moment with several simultaneous pitches; longer so the harmony can settle. */
    val chordSeconds: Double = 1.6,
    /** Silence between consecutive moments. */
    val gapSeconds: Double = 0.15,
    /**
     * Silence before the first note. Some devices swallow the first few milliseconds after a
     * track starts, which would otherwise clip the attack.
     */
    val leadInSeconds: Double = 0.03,
    /** Silence after the last note so the release is never cut off by the output stopping. */
    val tailSeconds: Double = 0.1,
) {
    init {
        require(noteSeconds > 0 && chordSeconds > 0) { "Note durations must be positive" }
        require(gapSeconds >= 0 && leadInSeconds >= 0 && tailSeconds >= 0) { "Silences must not be negative" }
    }
}

data class ScheduledNote(val pitch: Pitch, val startFrame: Int, val frameCount: Int) {
    val endFrame: Int get() = startFrame + frameCount
}

/**
 * Every note of a prompt placed on a frame timeline. Timing lives entirely in these frame
 * offsets; the renderer writes notes into one buffer, so the audio hardware's sample clock (not
 * coroutine delays or UI timers) decides when each note sounds.
 */
data class NoteSchedule(
    val sampleRate: Int,
    val notes: List<ScheduledNote>,
    /** Frame at which the last note has fully decayed. */
    val contentEndFrame: Int,
    /** Buffer length including trailing silence. */
    val totalFrames: Int,
    /** Largest number of notes that sound at the same time, used for gain staging. */
    val maxPolyphony: Int,
) {
    val durationSeconds: Double get() = totalFrames.toDouble() / sampleRate

    companion object {
        fun from(prompt: ListeningPrompt, sampleRate: Int, timing: PlaybackTiming = PlaybackTiming()): NoteSchedule {
            require(sampleRate > 0) { "Sample rate must be positive" }
            fun frames(seconds: Double) = (seconds * sampleRate).roundToInt()

            val notes = mutableListOf<ScheduledNote>()
            var cursor = frames(timing.leadInSeconds)
            prompt.moments.forEachIndexed { index, pitches ->
                if (index > 0) cursor += frames(timing.gapSeconds)
                val length = frames(if (pitches.size > 1) timing.chordSeconds else timing.noteSeconds)
                pitches.forEach { notes += ScheduledNote(it, cursor, length) }
                cursor += length
            }
            return NoteSchedule(
                sampleRate = sampleRate,
                notes = notes,
                contentEndFrame = cursor,
                totalFrames = cursor + frames(timing.tailSeconds),
                maxPolyphony = prompt.moments.maxOf { it.size },
            )
        }
    }
}
