package com.example.eartrainer.exercise

import com.example.eartrainer.music.Interval
import com.example.eartrainer.music.Pitch
import kotlin.random.Random

data class IntervalExerciseSettings(
    val intervals: List<Interval> = Interval.entries,
    val styles: Set<IntervalStyle> = setOf(IntervalStyle.MELODIC_ASCENDING),
    /** Every sounded pitch stays within this range. Default C3..C6 is comfortable on small speakers. */
    val pitchRange: IntRange = 48..84,
    val choiceCount: Int = 6,
) {
    init {
        require(intervals.isNotEmpty()) { "At least one interval must be enabled" }
        require(intervals.toSet().size == intervals.size) { "Intervals must be distinct" }
        require(styles.isNotEmpty()) { "At least one interval style must be enabled" }
        require(pitchRange.first in Pitch.MIDI_RANGE && pitchRange.last in Pitch.MIDI_RANGE) {
            "Pitch range $pitchRange must be within ${Pitch.MIDI_RANGE}"
        }
        require(pitchRange.last - pitchRange.first >= intervals.maxOf { it.semitones }) {
            "Pitch range $pitchRange is too narrow for the widest enabled interval"
        }
        require(choiceCount >= 2) { "A multiple-choice question needs at least two choices" }
    }
}

class IntervalQuestionGenerator(
    private val settings: IntervalExerciseSettings = IntervalExerciseSettings(),
    private val random: Random = Random.Default,
) {
    private val answerPool = settings.intervals.sortedBy { it.semitones }

    fun generate(): IntervalQuestion {
        val interval = settings.intervals.random(random)
        val highestLowerNote = settings.pitchRange.last - interval.semitones
        val lowerPitch = Pitch(random.nextInt(settings.pitchRange.first, highestLowerNote + 1))
        return IntervalQuestion(
            lowerPitch = lowerPitch,
            interval = interval,
            style = settings.styles.random(random),
            choices = choicesFor(interval),
        )
    }

    /**
     * Picks a contiguous run of intervals (by size) that contains the correct one. Neighbouring
     * sizes make plausible distractors, and randomising where the run starts keeps the correct
     * answer from always sitting in the same position.
     */
    private fun choicesFor(correct: Interval): List<Interval> {
        val count = minOf(settings.choiceCount, answerPool.size)
        val correctIndex = answerPool.indexOf(correct)
        val firstStart = maxOf(0, correctIndex - count + 1)
        val lastStart = minOf(correctIndex, answerPool.size - count)
        val start = random.nextInt(firstStart, lastStart + 1)
        return answerPool.subList(start, start + count).toList()
    }
}
