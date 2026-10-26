package com.troanhmolo.eartrainer.exercise

import com.troanhmolo.eartrainer.music.ChordInversion
import com.troanhmolo.eartrainer.music.ChordQuality
import com.troanhmolo.eartrainer.music.Pitch
import kotlin.random.Random

data class ChordExerciseSettings(
    val qualities: List<ChordQuality> = listOf(ChordQuality.MAJOR, ChordQuality.MINOR, ChordQuality.DIMINISHED, ChordQuality.AUGMENTED),
    val styles: Set<ChordStyle> = setOf(ChordStyle.BLOCK),
    val target: ChordTarget = ChordTarget.QUALITY,
    /** When naming qualities, also play chords in inversions. Naming inversions always uses them. */
    val includeInversions: Boolean = false,
    /** Every sounded pitch stays within this range. */
    val pitchRange: IntRange = 48..84,
    /** Ask more often about qualities or inversions recently answered wrongly. */
    val focusOnWeakSpots: Boolean = true,
) {
    init {
        require(qualities.size >= MIN_QUALITIES) { "At least $MIN_QUALITIES chord qualities must be enabled" }
        require(qualities.toSet().size == qualities.size) { "Chord qualities must be distinct" }
        require(styles.isNotEmpty()) { "At least one chord style must be enabled" }
        require(pitchRange.first in Pitch.MIDI_RANGE && pitchRange.last in Pitch.MIDI_RANGE) {
            "Pitch range $pitchRange must be within ${Pitch.MIDI_RANGE}"
        }
        require(pitchRange.last - pitchRange.first >= MAX_CHORD_SPAN) { "Pitch range $pitchRange is too narrow for every chord shape" }
    }

    /** Inversions a question for [quality] may use under these settings. */
    fun inversionsFor(quality: ChordQuality): List<ChordInversion> =
        if (target == ChordTarget.INVERSION || includeInversions) quality.inversions else listOf(ChordInversion.ROOT_POSITION)

    /** Adds or removes [quality], refusing to drop below [MIN_QUALITIES]. Keeps catalog order. */
    fun withQualityToggled(quality: ChordQuality): ChordExerciseSettings = when {
        quality !in qualities -> copy(qualities = ChordQuality.entries.filter { it in qualities || it == quality })
        qualities.size > MIN_QUALITIES -> copy(qualities = qualities - quality)
        else -> this
    }

    /** Adds or removes [style], refusing to remove the last one. */
    fun withStyleToggled(style: ChordStyle): ChordExerciseSettings = when {
        style !in styles -> copy(styles = styles + style)
        styles.size > 1 -> copy(styles = styles - style)
        else -> this
    }

    companion object {
        /** Fewer than two qualities would make quality questions a giveaway. */
        const val MIN_QUALITIES = 2

        /** Widest close-position shape in semitones (a major 7th chord in root position: 11). */
        val MAX_CHORD_SPAN: Int = ChordQuality.entries.maxOf { quality ->
            quality.inversions.maxOf { quality.offsetsAboveBass(it).last() }
        }
    }
}

class ChordQuestionGenerator(
    private val settings: ChordExerciseSettings = ChordExerciseSettings(),
    private val random: Random = Random.Default,
    /** Relative likelihood of asking about each answer; null asks them all equally often. */
    private val weightOf: ((ChordAnswer) -> Double)? = null,
) {
    fun generate(): ChordQuestion {
        // Weights apply to whatever is being named: the quality, or the inversion.
        val quality = when {
            weightOf != null && settings.target == ChordTarget.QUALITY ->
                settings.qualities.weightedRandom(random) { weightOf(ChordAnswer.Quality(it)) }
            else -> settings.qualities.random(random)
        }
        val inversions = settings.inversionsFor(quality)
        val inversion = when {
            weightOf != null && settings.target == ChordTarget.INVERSION ->
                inversions.weightedRandom(random) { weightOf(ChordAnswer.Inversion(it)) }
            else -> inversions.random(random)
        }
        val span = quality.offsetsAboveBass(inversion).last()
        val bass = Pitch(random.nextInt(settings.pitchRange.first, settings.pitchRange.last - span + 1))
        val choices = when (settings.target) {
            ChordTarget.QUALITY -> settings.qualities.map { ChordAnswer.Quality(it) }
            ChordTarget.INVERSION -> quality.inversions.map { ChordAnswer.Inversion(it) }
        }
        return ChordQuestion(bass, quality, inversion, settings.styles.random(random), settings.target, choices)
    }
}
