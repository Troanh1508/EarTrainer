package com.example.eartrainer.exercise

import com.example.eartrainer.music.ChordProgression
import com.example.eartrainer.music.CommonProgressions
import com.example.eartrainer.music.DiatonicChord
import com.example.eartrainer.music.Key
import com.example.eartrainer.music.ListeningPrompt
import com.example.eartrainer.music.Mode
import kotlin.random.Random

/** Which chords progressions may use, by scale step. */
enum class ChordVocabulary(val displayName: String, val steps: Set<Int>) {
    PRIMARY("I, IV and V", setOf(1, 4, 5)),
    COMMON("Add ii and vi", setOf(1, 2, 4, 5, 6)),
    ALL("All seven chords", (1..7).toSet()),
}

/** Where questions come from. */
enum class ProgressionSource(val displayName: String) {
    /** Well-known progressions from popular and classical music. */
    COMMON("Common progressions"),

    /** Any order of the allowed chords, including unusual ones. */
    RANDOM("Random"),
}

data class ProgressionQuestion(
    val key: Key,
    val progression: ChordProgression,
    override val choices: List<ChordProgression>,
) : ExerciseQuestion<ChordProgression> {

    init {
        require(progression.mode == key.mode) { "The progression must be in the key's mode" }
        requireValidChoices(choices, progression)
    }

    override val correctAnswer: ChordProgression get() = progression

    override val prompt: ListeningPrompt
        get() = ListeningPrompt(progression.voicedIn(key), ListeningPrompt.Articulation.FLOWING)
}

data class ProgressionSettings(
    val modes: Set<Mode> = setOf(Mode.MAJOR),
    val vocabulary: ChordVocabulary = ChordVocabulary.COMMON,
    val length: Int = 4,
    val keyChoice: KeyChoice = KeyChoice.RANDOM,
    val source: ProgressionSource = ProgressionSource.COMMON,
) {
    init {
        require(modes.isNotEmpty()) { "At least one mode must be enabled" }
        require(length in LENGTH_OPTIONS) { "Progression length must be one of $LENGTH_OPTIONS" }
    }

    /** Adds or removes [mode], refusing to remove the last one. */
    fun withModeToggled(mode: Mode): ProgressionSettings = when {
        mode !in modes -> copy(modes = modes + mode)
        modes.size > 1 -> copy(modes = modes - mode)
        else -> this
    }

    companion object {
        val LENGTH_OPTIONS = listOf(3, 4, 5)

        /** Multiple-choice options per question, including the correct one. */
        const val CHOICE_COUNT = 4
    }
}

class ProgressionQuestionGenerator(
    private val settings: ProgressionSettings = ProgressionSettings(),
    private val random: Random = Random.Default,
) {
    /** Every allowed progression per mode, computed once; at most 7^4 = 2,401 per mode. */
    private val catalogue: Map<Mode, List<ChordProgression>> = settings.modes.associateWith { mode ->
        allProgressions(mode, settings.vocabulary, settings.length)
    }

    /** The common progressions that fit the settings, per mode; empty in random mode. */
    private val common: Map<Mode, List<ChordProgression>> = settings.modes.associateWith { mode ->
        if (settings.source == ProgressionSource.COMMON) commonProgressions(mode, settings.vocabulary, settings.length) else emptyList()
    }

    fun generate(): ProgressionQuestion {
        val mode = settings.modes.random(random)
        val tonic = settings.keyChoice.pickTonic(random)
        val everything = catalogue.getValue(mode)
        // Falls back to random progressions if no common one fits the chosen chords and length.
        val preferred = common.getValue(mode).ifEmpty { everything }
        val correct = preferred.random(random)
        return ProgressionQuestion(Key(tonic, mode), correct, choicesFor(correct, preferred, everything))
    }

    /**
     * The correct progression plus the closest alternatives: those differing in the fewest
     * chords (ties broken randomly). Near-misses make the listener follow every chord rather
     * than just the overall shape. Alternatives come from [preferred] first (common progressions
     * when enabled) and only then from [everything]. Shuffled so the answer's position carries
     * no clue.
     */
    private fun choicesFor(
        correct: ChordProgression,
        preferred: List<ChordProgression>,
        everything: List<ChordProgression>,
    ): List<ChordProgression> {
        val needed = ProgressionSettings.CHOICE_COUNT - 1
        val fromPreferred = closest(correct, preferred - correct).take(needed)
        val fromEverything = closest(correct, everything - correct - fromPreferred.toSet()).take(needed - fromPreferred.size)
        return (fromPreferred + fromEverything + correct).shuffled(random)
    }

    private fun closest(correct: ChordProgression, candidates: List<ChordProgression>): List<ChordProgression> =
        candidates.shuffled(random).sortedBy { candidate -> candidate.chords.indices.count { candidate.chords[it] != correct.chords[it] } }

    companion object {
        /**
         * Progressions that start on the tonic chord (so the key is heard first) and never repeat
         * a chord back to back (which would sound like one long chord).
         */
        fun allProgressions(mode: Mode, vocabulary: ChordVocabulary, length: Int): List<ChordProgression> {
            val chords = DiatonicChord.all(mode).filter { it.step in vocabulary.steps }
            var sequences = listOf(listOf(DiatonicChord(mode, 1)))
            repeat(length - 1) {
                sequences = sequences.flatMap { sequence -> chords.filter { it != sequence.last() }.map { sequence + it } }
            }
            return sequences.map(::ChordProgression)
        }

        /** Common progressions in [mode] with [length] chords that use only the [vocabulary]. */
        fun commonProgressions(mode: Mode, vocabulary: ChordVocabulary, length: Int): List<ChordProgression> =
            CommonProgressions.forMode(mode, length).filter { progression -> progression.chords.all { it.step in vocabulary.steps } }
    }
}

/** One answered progression question, as stored in practice history. */
data class ProgressionAttempt(
    val sessionId: String,
    val answeredAtMillis: Long,
    val key: Key,
    val progression: ChordProgression,
    val answer: ChordProgression,
    val isCorrect: Boolean,
    /** Times the prompt was started before answering; 1 means no replays. */
    val playCount: Int,
    /** From the first time the prompt started playing until the answer. */
    val responseMillis: Long,
) {
    companion object {
        /** The attempt for [state]'s current question, or null if it has not been answered. */
        fun from(state: ExerciseState<ChordProgression, ProgressionQuestion>, sessionId: String): ProgressionAttempt? {
            val answered = state.phase as? ExercisePhase.Answered<ChordProgression> ?: return null
            val firstPlayed = state.firstPlayedAtMillis ?: answered.answeredAtMillis
            return ProgressionAttempt(
                sessionId = sessionId,
                answeredAtMillis = answered.answeredAtMillis,
                key = state.question.key,
                progression = state.question.progression,
                answer = answered.selectedAnswer,
                isCorrect = answered.isCorrect,
                playCount = state.playCount,
                responseMillis = (answered.answeredAtMillis - firstPlayed).coerceAtLeast(0),
            )
        }
    }
}
