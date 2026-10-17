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
    /** Time between onsets in an arpeggio; short enough that the notes are heard as one chord. */
    val arpeggioStepSeconds: Double = 0.3,
    /** Length of each chord in a flowing progression, played back to back. */
    val progressionChordSeconds: Double = 1.1,
    /** Length of each context chord (e.g. in a key-setting cadence), played back to back. */
    val contextChordSeconds: Double = 0.7,
    /** Silence between the context and the question, so the question stands apart. */
    val contextPauseSeconds: Double = 0.7,
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
        require(arpeggioStepSeconds > 0 && contextChordSeconds > 0 && progressionChordSeconds > 0) { "Durations must be positive" }
        require(contextPauseSeconds >= 0) { "Silences must not be negative" }
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
            var leadIn = frames(timing.leadInSeconds)
            if (prompt.context.isNotEmpty()) {
                val chordLength = frames(timing.contextChordSeconds)
                prompt.context.forEachIndexed { index, pitches ->
                    pitches.forEach { notes += ScheduledNote(it, leadIn + index * chordLength, chordLength) }
                }
                leadIn += prompt.context.size * chordLength + frames(timing.contextPauseSeconds)
            }
            val contentEnd = when (prompt.articulation) {
                ListeningPrompt.Articulation.SEPARATE -> {
                    var cursor = leadIn
                    prompt.moments.forEachIndexed { index, pitches ->
                        if (index > 0) cursor += frames(timing.gapSeconds)
                        val length = frames(if (pitches.size > 1) timing.chordSeconds else timing.noteSeconds)
                        pitches.forEach { notes += ScheduledNote(it, cursor, length) }
                        cursor += length
                    }
                    cursor
                }
                ListeningPrompt.Articulation.FLOWING -> {
                    val length = frames(timing.progressionChordSeconds)
                    prompt.moments.forEachIndexed { index, pitches ->
                        pitches.forEach { notes += ScheduledNote(it, leadIn + index * length, length) }
                    }
                    leadIn + prompt.moments.size * length
                }
                ListeningPrompt.Articulation.ARPEGGIATED -> {
                    // Every note holds until the shared end, like a sustain pedal, so the last
                    // onset is followed by the whole chord for a full chord length.
                    val step = frames(timing.arpeggioStepSeconds)
                    val end = leadIn + step * (prompt.moments.size - 1) + frames(timing.chordSeconds)
                    prompt.moments.forEachIndexed { index, pitches ->
                        val start = leadIn + step * index
                        pitches.forEach { notes += ScheduledNote(it, start, end - start) }
                    }
                    end
                }
            }
            return NoteSchedule(
                sampleRate = sampleRate,
                notes = notes,
                contentEndFrame = contentEnd,
                totalFrames = contentEnd + frames(timing.tailSeconds),
                maxPolyphony = maxOverlap(notes),
            )
        }

        /** Most notes sounding at any one frame: a sweep over note starts and ends. */
        private fun maxOverlap(notes: List<ScheduledNote>): Int {
            // At equal frames, ends (-1) sort before starts (+1): a note ending exactly where
            // another begins does not overlap it.
            val events = notes.flatMap { listOf(it.startFrame to 1, it.endFrame to -1) }
                .sortedWith(compareBy({ it.first }, { it.second }))
            var current = 0
            return events.maxOf { (_, change) -> current += change; current }
        }
    }
}
