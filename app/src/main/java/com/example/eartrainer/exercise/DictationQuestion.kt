package com.example.eartrainer.exercise

import com.example.eartrainer.music.Key
import com.example.eartrainer.music.ListeningPrompt
import com.example.eartrainer.music.Melody
import com.example.eartrainer.music.Mode
import com.example.eartrainer.music.ScaleDegree
import kotlin.math.abs
import kotlin.random.Random

/**
 * A short melody to write down by ear. It starts on the tonic, which is given; the listener
 * enters the remaining notes as scale degrees. Notes stay within the octave above the tonic, so
 * every degree names exactly one pitch.
 */
data class DictationQuestion(
    val key: Key,
    val melody: Melody,
    val context: KeyContext,
) : ExerciseQuestion<Melody> {

    init {
        require(melody.degrees.first() == ScaleDegree.TONIC) { "Dictation melodies start on the tonic" }
        require(melody.degrees.all { it in key.mode.diatonicDegrees }) { "Dictation melodies use only notes of the key" }
    }

    /** Answers are entered note by note rather than picked from a list. */
    override val choices: List<Melody> get() = emptyList()

    /** The notes that can be entered: the seven degrees of the key. */
    val keypad: List<ScaleDegree> get() = key.mode.diatonicDegrees

    override val correctAnswer: Melody get() = melody

    /** A complete entry: the right length, the given tonic first, and only notes of the key. */
    override fun accepts(answer: Melody): Boolean =
        answer.degrees.size == melody.degrees.size &&
            answer.degrees.first() == ScaleDegree.TONIC &&
            answer.degrees.all { it in keypad }

    override val prompt: ListeningPrompt
        get() = ListeningPrompt(
            moments = melody.pitchesIn(key).map { listOf(it) },
            articulation = ListeningPrompt.Articulation.MELODIC,
            context = when (context) {
                KeyContext.CADENCE -> key.cadence()
                KeyContext.TONIC_CHORD -> listOf(key.tonicChord())
            },
        )
}

data class DictationSettings(
    val modes: Set<Mode> = setOf(Mode.MAJOR),
    /** Notes per melody, including the given opening tonic. */
    val length: Int = 4,
    val keyChoice: KeyChoice = KeyChoice.RANDOM,
    val context: KeyContext = KeyContext.CADENCE,
    /** Steer melodies towards degrees recently written down wrongly. */
    val focusOnWeakSpots: Boolean = true,
) {
    init {
        require(modes.isNotEmpty()) { "At least one mode must be enabled" }
        require(length in LENGTH_OPTIONS) { "Melody length must be one of $LENGTH_OPTIONS" }
    }

    /** Adds or removes [mode], refusing to remove the last one. */
    fun withModeToggled(mode: Mode): DictationSettings = when {
        mode !in modes -> copy(modes = modes + mode)
        modes.size > 1 -> copy(modes = modes - mode)
        else -> this
    }

    companion object {
        val LENGTH_OPTIONS = listOf(3, 4, 5, 6)
    }
}

class DictationQuestionGenerator(
    private val settings: DictationSettings = DictationSettings(),
    private val random: Random = Random.Default,
    /** Relative likelihood of each degree appearing; null leaves the melody shape alone. */
    private val weightOf: ((ScaleDegree) -> Double)? = null,
) {
    fun generate(): DictationQuestion {
        val mode = settings.modes.random(random)
        val key = Key(settings.keyChoice.pickTonic(random), mode)
        return DictationQuestion(key, melody(mode), settings.context)
    }

    /**
     * A random walk over the scale that sounds like a simple tune: mostly steps, sometimes a
     * third, rarely a wider leap, never a repeated note. Weak-spot weights multiply in, so
     * degrees the listener misses turn up more often without making the line jumpy.
     */
    private fun melody(mode: Mode): Melody {
        val scale = mode.diatonicDegrees
        val steps = mutableListOf(0)
        repeat(settings.length - 1) {
            val current = steps.last()
            val candidates = scale.indices.filter { it != current }
            val next = candidates.weightedRandom(random) { candidate ->
                val shape = MOTION_WEIGHTS.getOrElse(abs(candidate - current)) { 0.0 }
                shape * (weightOf?.invoke(scale[candidate]) ?: 1.0)
            }
            steps += next
        }
        return Melody(steps.map { scale[it] })
    }

    private companion object {
        /** Likelihood by distance in scale steps: steps 4, thirds 2, fourths 1, fifths 0.5, wider 0.25. */
        val MOTION_WEIGHTS = listOf(0.0, 4.0, 2.0, 1.0, 0.5, 0.25, 0.25)
    }
}

/** One answered dictation question, as stored in practice history. */
data class DictationAttempt(
    val sessionId: String,
    val answeredAtMillis: Long,
    val key: Key,
    val melody: Melody,
    val answer: Melody,
    val isCorrect: Boolean,
    /** Times the prompt was started before answering; 1 means no replays. */
    val playCount: Int,
    /** From the first time the prompt started playing until the answer. */
    val responseMillis: Long,
) {
    companion object {
        /** The attempt for [state]'s current question, or null if it has not been answered. */
        fun from(state: ExerciseState<Melody, DictationQuestion>, sessionId: String): DictationAttempt? {
            val answered = state.phase as? ExercisePhase.Answered<Melody> ?: return null
            val firstPlayed = state.firstPlayedAtMillis ?: answered.answeredAtMillis
            return DictationAttempt(
                sessionId = sessionId,
                answeredAtMillis = answered.answeredAtMillis,
                key = state.question.key,
                melody = state.question.melody,
                answer = answered.selectedAnswer,
                isCorrect = answered.isCorrect,
                playCount = state.playCount,
                responseMillis = (answered.answeredAtMillis - firstPlayed).coerceAtLeast(0),
            )
        }
    }
}
