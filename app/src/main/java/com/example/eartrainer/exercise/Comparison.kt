package com.example.eartrainer.exercise

import com.example.eartrainer.music.ChordInversion
import com.example.eartrainer.music.ChordQuality
import com.example.eartrainer.music.ChordProgression
import com.example.eartrainer.music.DiatonicChord
import com.example.eartrainer.music.Interval
import com.example.eartrainer.music.Key
import com.example.eartrainer.music.ListeningPrompt
import com.example.eartrainer.music.Mode
import com.example.eartrainer.music.Pitch
import com.example.eartrainer.music.Rhythm
import com.example.eartrainer.music.RhythmCell
import com.example.eartrainer.music.ScaleDegree
import kotlin.random.Random

/**
 * A short drill on two answers that are easily mixed up: every question asks for one of the two,
 * with only those two as choices. The questions are the exercise's ordinary questions, so the
 * answers are recorded in history like any others.
 */
interface Comparison<A, Q : ExerciseQuestion<A>> {
    /** The two answers, in the order they are offered. */
    val answers: List<A>

    fun nextQuestion(): Q

    /**
     * One example of each of [answers], played exactly the same way apart from the difference
     * itself: from the same note, on the same bass or in the same key. Fixed for the drill.
     */
    val examples: List<ListeningPrompt>

    companion object {
        /** Questions in one round of a drill. */
        const val QUESTIONS_PER_ROUND = 10
    }
}

/** Two intervals, from random notes; [style] null mixes all three styles. */
class IntervalComparison(
    first: Interval,
    second: Interval,
    private val style: IntervalStyle?,
    private val random: Random = Random.Default,
    private val pitchRange: IntRange = 48..84,
) : Comparison<Interval, IntervalQuestion> {

    init {
        require(first != second) { "A comparison needs two different intervals" }
    }

    override val answers = listOf(first, second).sortedBy { it.semitones }

    private val widest = answers.maxOf { it.semitones }

    private fun randomLowerPitch() = Pitch(random.nextInt(pitchRange.first, pitchRange.last - widest + 1))

    override fun nextQuestion(): IntervalQuestion = IntervalQuestion(
        lowerPitch = randomLowerPitch(),
        interval = answers.random(random),
        style = style ?: IntervalStyle.entries.random(random),
        choices = answers,
    )

    override val examples: List<ListeningPrompt> = randomLowerPitch().let { lower ->
        answers.map { IntervalQuestion(lower, it, style ?: IntervalStyle.MELODIC_ASCENDING, answers).prompt }
    }
}

/** Two chord qualities, or two inversions, played as block chords from random bass notes. */
class ChordComparison(
    first: ChordAnswer,
    second: ChordAnswer,
    private val random: Random = Random.Default,
    private val pitchRange: IntRange = 48..84,
) : Comparison<ChordAnswer, ChordQuestion> {

    private val target: ChordTarget = when (first) {
        is ChordAnswer.Quality -> ChordTarget.QUALITY
        is ChordAnswer.Inversion -> ChordTarget.INVERSION
    }

    init {
        require(first != second) { "A comparison needs two different answers" }
        require(first::class == second::class) { "Both answers must be qualities, or both inversions" }
    }

    override val answers: List<ChordAnswer> = listOf(first, second).sortedBy {
        when (it) {
            is ChordAnswer.Quality -> it.quality.ordinal
            is ChordAnswer.Inversion -> it.inversion.ordinal
        }
    }

    /** Qualities that have both inversions being compared; triads first, so the common case stays simple. */
    private val qualitiesForInversions: List<ChordQuality> =
        if (target == ChordTarget.INVERSION) {
            val inversions = answers.map { (it as ChordAnswer.Inversion).inversion }
            ChordQuality.entries.filter { it.inversions.containsAll(inversions) }.sortedBy { it.inversions.size }
                .also { require(it.isNotEmpty()) { "No chord quality has both inversions" } }
        } else {
            emptyList()
        }

    private fun questionFor(answer: ChordAnswer, quality: ChordQuality?, bassPicker: (span: Int) -> Pitch): ChordQuestion {
        val (chordQuality, inversion) = when (answer) {
            is ChordAnswer.Quality -> answer.quality to ChordInversion.ROOT_POSITION
            is ChordAnswer.Inversion -> checkNotNull(quality) to answer.inversion
        }
        val bass = bassPicker(chordQuality.offsetsAboveBass(inversion).last())
        return ChordQuestion(bass, chordQuality, inversion, ChordStyle.BLOCK, target, answers)
    }

    private fun randomBass(span: Int) = Pitch(random.nextInt(pitchRange.first, pitchRange.last - span + 1))

    override fun nextQuestion(): ChordQuestion =
        questionFor(answers.random(random), qualitiesForInversions.randomOrNull(random), ::randomBass)

    override val examples: List<ListeningPrompt> = run {
        // One bass note low enough for every shape, so both examples share it.
        val bass = randomBass(ChordExerciseSettings.MAX_CHORD_SPAN)
        val quality = qualitiesForInversions.firstOrNull()
        answers.map { questionFor(it, quality) { bass }.prompt }
    }
}

/** Two scale degrees in [mode], each question in a random key after a cadence. */
class ScaleDegreeComparison(
    first: ScaleDegree,
    second: ScaleDegree,
    private val mode: Mode,
    private val random: Random = Random.Default,
) : Comparison<ScaleDegree, ScaleDegreeQuestion> {

    init {
        require(first != second) { "A comparison needs two different degrees" }
    }

    override val answers = listOf(first, second).sortedBy { it.ordinal }

    private fun randomKey() = Key(KeyChoice.RANDOM.pickTonic(random), mode)

    override fun nextQuestion() = ScaleDegreeQuestion(randomKey(), answers.random(random), KeyContext.CADENCE, answers)

    override val examples: List<ListeningPrompt> = randomKey().let { key ->
        answers.map { ScaleDegreeQuestion(key, it, KeyContext.CADENCE, answers).prompt }
    }

    companion object {
        /**
         * The mode to compare [first] and [second] in when the history mixes modes: the first
         * mode in which both are notes of the key, or major if none is.
         */
        fun modeFor(first: ScaleDegree, second: ScaleDegree): Mode =
            Mode.entries.firstOrNull { it.diatonicDegrees.containsAll(listOf(first, second)) } ?: Mode.MAJOR
    }
}

/**
 * Two chords of a key, each question being the tonic chord and then one of them, in a random key.
 * Answers are recorded as two-chord progressions; progression scoring skips the opening tonic,
 * so only the compared chord counts. When one of the two is the tonic itself, the question is
 * whether the chord changes at all.
 */
class ProgressionChordComparison(
    first: DiatonicChord,
    second: DiatonicChord,
    private val random: Random = Random.Default,
) : Comparison<ChordProgression, ProgressionQuestion> {

    init {
        require(first != second) { "A comparison needs two different chords" }
        require(first.mode == second.mode) { "Both chords must be in the same mode" }
    }

    private val mode = first.mode

    override val answers: List<ChordProgression> =
        listOf(first, second).sortedBy { it.step }.map { ChordProgression(listOf(DiatonicChord(mode, 1), it)) }

    private fun randomKey() = Key(KeyChoice.RANDOM.pickTonic(random), mode)

    override fun nextQuestion() = ProgressionQuestion(randomKey(), answers.random(random), answers)

    override val examples: List<ListeningPrompt> = randomKey().let { key ->
        answers.map { ProgressionQuestion(key, it, answers).prompt }
    }
}

/**
 * Two rhythm figures, as one bar that alternates them, against the same bar in the opposite
 * order. Every figure in either rhythm is one of the two, so recording answers only ever scores
 * the figures being compared.
 */
class RhythmCellComparison(
    first: RhythmCell,
    second: RhythmCell,
    private val tempo: RhythmTempo = RhythmTempo.MEDIUM,
    private val random: Random = Random.Default,
) : Comparison<Rhythm, RhythmQuestion> {

    init {
        require(first != second) { "A comparison needs two different figures" }
    }

    /** The figure listed first in the rhythm settings leads the first rhythm. */
    private val cells = listOf(first, second).sortedBy { it.ordinal }

    override val answers: List<Rhythm> = listOf(alternating(cells[0], cells[1]), alternating(cells[1], cells[0]))

    override fun nextQuestion() = RhythmQuestion(answers.random(random), tempo, answers)

    override val examples: List<ListeningPrompt> = answers.map { RhythmQuestion(it, tempo, answers).prompt }

    companion object {
        /**
         * One bar taking [lead] and [other] in turn. A two-beat figure must start on beat 1 or 3
         * and fit before the barline; where it cannot, the other figure goes there instead.
         */
        fun alternating(lead: RhythmCell, other: RhythmCell): Rhythm {
            val cells = mutableListOf<RhythmCell>()
            var beat = 0
            var next = lead
            while (beat < Rhythm.BEATS_PER_BAR) {
                val alternative = if (next == lead) other else lead
                val fits = { cell: RhythmCell -> cell.beats == 1 || (beat % 2 == 0 && beat + cell.beats <= Rhythm.BEATS_PER_BAR) }
                val cell = if (fits(next)) next else alternative.takeIf(fits) ?: RhythmCell.QUARTER
                cells += cell
                beat += cell.beats
                next = if (cell == lead) other else lead
            }
            return Rhythm(cells)
        }
    }
}
