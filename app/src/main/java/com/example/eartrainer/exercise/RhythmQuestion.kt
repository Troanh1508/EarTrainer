package com.example.eartrainer.exercise

import com.example.eartrainer.music.ListeningPrompt
import com.example.eartrainer.music.Pitch
import com.example.eartrainer.music.Rhythm
import com.example.eartrainer.music.RhythmCell
import kotlin.random.Random

/** Which rhythm cells questions may use, from simplest to hardest. */
enum class RhythmVocabulary(val displayName: String, val cells: Set<RhythmCell>) {
    BASIC("Quarters, eighths and rests", setOf(RhythmCell.QUARTER, RhythmCell.EIGHTHS, RhythmCell.QUARTER_REST, RhythmCell.HALF)),
    SYNCOPATION(
        "Add dotted and off-beat rhythms",
        BASIC.cells + setOf(RhythmCell.DOTTED_QUARTER_EIGHTH, RhythmCell.EIGHTH_REST_EIGHTH),
    ),
    SIXTEENTHS(
        "Add sixteenths",
        SYNCOPATION.cells + setOf(RhythmCell.SIXTEENTHS, RhythmCell.EIGHTH_TWO_SIXTEENTHS, RhythmCell.TWO_SIXTEENTHS_EIGHTH),
    ),
}

enum class RhythmTempo(val displayName: String, val bpm: Int) {
    SLOW("Slow", 70),
    MEDIUM("Medium", 90),
    FAST("Fast", 120),
}

data class RhythmQuestion(
    val rhythm: Rhythm,
    val tempo: RhythmTempo,
    override val choices: List<Rhythm>,
) : ExerciseQuestion<Rhythm> {

    init {
        requireValidChoices(choices, rhythm)
    }

    override val correctAnswer: Rhythm get() = rhythm

    /**
     * One bar of count-in clicks on a high note, then the rhythm on a lower one, all placed
     * exactly in beats so the timing comes from the audio clock.
     */
    override val prompt: ListeningPrompt
        get() {
            val countIn = Rhythm.BEATS_PER_BAR
            val notes = rhythm.timeline.filter { (_, event) -> !event.isRest }
            return ListeningPrompt(
                moments = List(countIn) { listOf(COUNT_IN_PITCH) } + notes.map { listOf(RHYTHM_PITCH) },
                beatTiming = ListeningPrompt.BeatTiming(
                    bpm = tempo.bpm,
                    startBeats = List(countIn) { it.toDouble() } + notes.map { (start, _) -> countIn + start },
                    lengthBeats = List(countIn) { COUNT_IN_CLICK_BEATS } + notes.map { (_, event) -> event.beats },
                ),
            )
        }

    companion object {
        val COUNT_IN_PITCH = Pitch(96)
        val RHYTHM_PITCH = Pitch(72)

        /** Count-in clicks are short so they read as a metronome rather than as notes. */
        const val COUNT_IN_CLICK_BEATS = 0.25
    }
}

data class RhythmSettings(
    val vocabulary: RhythmVocabulary = RhythmVocabulary.BASIC,
    val bars: Int = 1,
    val tempo: RhythmTempo = RhythmTempo.MEDIUM,
    /** Ask more often about rhythm figures recently misheard. */
    val focusOnWeakSpots: Boolean = true,
) {
    init {
        require(bars in BAR_OPTIONS) { "Bars must be one of $BAR_OPTIONS" }
    }

    companion object {
        val BAR_OPTIONS = listOf(1, 2)
        const val CHOICE_COUNT = 4
    }
}

class RhythmQuestionGenerator(
    private val settings: RhythmSettings = RhythmSettings(),
    private val random: Random = Random.Default,
    /** Relative likelihood of each cell appearing; null picks cells evenly. */
    private val weightOf: ((RhythmCell) -> Double)? = null,
) {
    private val cells = settings.vocabulary.cells.toList()

    fun generate(): RhythmQuestion {
        val rhythm = randomRhythm()
        return RhythmQuestion(rhythm, settings.tempo, choicesFor(rhythm))
    }

    private fun randomRhythm(): Rhythm {
        while (true) {
            val candidate = Rhythm((0 until settings.bars).flatMap { randomBar() })
            if (isPleasant(candidate)) return candidate
        }
    }

    /** Fills one bar beat by beat; two-beat cells only start on beat 1 or 3 so they notate cleanly. */
    private fun randomBar(): List<RhythmCell> {
        val bar = mutableListOf<RhythmCell>()
        var beat = 0
        while (beat < Rhythm.BEATS_PER_BAR) {
            val fitting = cells.filter { cell -> cell.beats == 1 || (beat % 2 == 0 && beat + cell.beats <= Rhythm.BEATS_PER_BAR) }
            val cell = weightOf?.let { weight -> fitting.weightedRandom(random, weight) } ?: fitting.random(random)
            bar += cell
            beat += cell.beats
        }
        return bar
    }

    /**
     * The correct rhythm plus alternatives that each change one cell (a beat or two-beat
     * figure), so the listener has to follow the whole rhythm. Shuffled so position is no clue.
     */
    private fun choicesFor(correct: Rhythm): List<Rhythm> {
        // Closest first: a swapped cell, then a re-split half bar; random rhythms only as a last resort.
        val nearMisses = oneCellChanges(correct).filter(::isPleasant).shuffled(random) +
            halfBarChanges(correct).filter(::isPleasant).shuffled(random)
        val distractors = (nearMisses + generateSequence { randomRhythm() }.take(50))
            .distinct()
            .filter { it != correct }
            .take(RhythmSettings.CHOICE_COUNT - 1)
        return (distractors + correct).shuffled(random)
    }

    /** Every rhythm that replaces exactly one cell with a different cell of the same length. */
    private fun oneCellChanges(rhythm: Rhythm): List<Rhythm> =
        rhythm.cells.indices.flatMap { index ->
            val current = rhythm.cells[index]
            cells.filter { it != current && it.beats == current.beats }.map { replacement ->
                Rhythm(rhythm.cells.toMutableList().also { it[index] = replacement })
            }
        }

    /**
     * Rhythms that change one half bar (beats 1–2 or 3–4) by splitting a two-beat cell into two
     * one-beat cells, or merging two one-beat cells into a two-beat cell.
     */
    private fun halfBarChanges(rhythm: Rhythm): List<Rhythm> {
        val single = cells.filter { it.beats == 1 }
        val double = cells.filter { it.beats == 2 }
        return rhythm.startBeats.indices.filter { rhythm.startBeats[it] % 2 == 0 }.flatMap { index ->
            val cell = rhythm.cells[index]
            val (replaced, replacements) = if (cell.beats == 2) {
                1 to single.flatMap { first -> single.map { second -> listOf(first, second) } }
            } else {
                2 to double.map { listOf(it) }
            }
            replacements.map { replacement ->
                Rhythm(rhythm.cells.take(index) + replacement + rhythm.cells.drop(index + replaced))
            }
        }
    }

    /** Rhythms that start with a note and have at least two notes per bar are easier to follow. */
    private fun isPleasant(rhythm: Rhythm): Boolean {
        if (rhythm.cells.first().events.first().isRest) return false
        val notesPerBar = IntArray(rhythm.bars)
        rhythm.timeline.filter { !it.second.isRest }.forEach { (start, _) -> notesPerBar[(start / Rhythm.BEATS_PER_BAR).toInt()]++ }
        return notesPerBar.all { it >= 2 }
    }
}

/** One answered rhythm question, as stored in practice history. */
data class RhythmAttempt(
    val sessionId: String,
    val answeredAtMillis: Long,
    val rhythm: Rhythm,
    val tempo: RhythmTempo,
    val answer: Rhythm,
    val isCorrect: Boolean,
    /** Times the prompt was started before answering; 1 means no replays. */
    val playCount: Int,
    /** From the first time the prompt started playing until the answer. */
    val responseMillis: Long,
) {
    companion object {
        /** The attempt for [state]'s current question, or null if it has not been answered. */
        fun from(state: ExerciseState<Rhythm, RhythmQuestion>, sessionId: String): RhythmAttempt? {
            val answered = state.phase as? ExercisePhase.Answered<Rhythm> ?: return null
            val firstPlayed = state.firstPlayedAtMillis ?: answered.answeredAtMillis
            return RhythmAttempt(
                sessionId = sessionId,
                answeredAtMillis = answered.answeredAtMillis,
                rhythm = state.question.rhythm,
                tempo = state.question.tempo,
                answer = answered.selectedAnswer,
                isCorrect = answered.isCorrect,
                playCount = state.playCount,
                responseMillis = (answered.answeredAtMillis - firstPlayed).coerceAtLeast(0),
            )
        }
    }
}

/**
 * Scores a rhythm answer cell by cell: each cell of the played rhythm is one result, correct when
 * the chosen rhythm has the same cell starting on the same beat.
 */
fun rhythmCellResults(played: Rhythm, chosen: Rhythm): List<ItemResult<RhythmCell>> =
    rhythmCellPairs(played, chosen).map { (cell, answer) -> ItemResult(cell, answer == cell) }

/**
 * Pairs each cell of the played rhythm with the chosen rhythm's cell starting on the same beat,
 * or null when no chosen cell starts there (the beat was split differently).
 */
fun rhythmCellPairs(played: Rhythm, chosen: Rhythm): List<Pair<RhythmCell, RhythmCell?>> {
    val chosenByBeat = chosen.startBeats.zip(chosen.cells).toMap()
    return played.startBeats.zip(played.cells).map { (beat, cell) -> cell to chosenByBeat[beat] }
}
