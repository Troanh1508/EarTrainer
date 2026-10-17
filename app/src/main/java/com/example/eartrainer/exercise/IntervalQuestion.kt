package com.example.eartrainer.exercise

import com.example.eartrainer.music.Interval
import com.example.eartrainer.music.ListeningPrompt
import com.example.eartrainer.music.Pitch

/** How the two notes of an interval are presented. */
enum class IntervalStyle(val displayName: String) {
    MELODIC_ASCENDING("Ascending melodic"),
    MELODIC_DESCENDING("Descending melodic"),
    HARMONIC("Harmonic"),
}

data class IntervalQuestion(
    val lowerPitch: Pitch,
    val interval: Interval,
    val style: IntervalStyle,
    override val choices: List<Interval>,
) : ExerciseQuestion<Interval> {

    init {
        requireValidChoices(choices, interval)
        require(lowerPitch.midi + interval.semitones in Pitch.MIDI_RANGE) {
            "${interval.displayName} above $lowerPitch is outside the MIDI range"
        }
    }

    val upperPitch: Pitch get() = lowerPitch.transpose(interval.semitones)

    override val correctAnswer: Interval get() = interval

    override val prompt: ListeningPrompt
        get() = when (style) {
            IntervalStyle.MELODIC_ASCENDING -> ListeningPrompt.sequential(listOf(lowerPitch, upperPitch))
            IntervalStyle.MELODIC_DESCENDING -> ListeningPrompt.sequential(listOf(upperPitch, lowerPitch))
            IntervalStyle.HARMONIC -> ListeningPrompt.simultaneous(listOf(lowerPitch, upperPitch))
        }
}
