package com.troanhmolo.eartrainer.exercise

import com.troanhmolo.eartrainer.music.Key
import com.troanhmolo.eartrainer.music.ListeningPrompt
import com.troanhmolo.eartrainer.music.Mode
import com.troanhmolo.eartrainer.music.Pitch
import com.troanhmolo.eartrainer.music.ScaleDegree
import kotlin.random.Random

/** What is played before the note to establish the key. */
enum class KeyContext(val displayName: String) {
    CADENCE("Cadence (I–IV–V–I)"),
    TONIC_CHORD("Tonic chord"),
}

/** Which degrees questions can ask about. */
enum class DegreeSet(val displayName: String) {
    /** The seven notes of the question's mode. */
    DIATONIC("Notes in the key"),

    /** All twelve notes, including ones outside the key. */
    CHROMATIC("All 12 notes"),
}

data class ScaleDegreeQuestion(
    val key: Key,
    val degree: ScaleDegree,
    val context: KeyContext,
    override val choices: List<ScaleDegree>,
) : ExerciseQuestion<ScaleDegree> {

    init {
        requireValidChoices(choices, degree)
    }

    /** The note to identify, in the octave above the tonic. */
    val target: Pitch get() = key.pitchOf(degree)

    override val correctAnswer: ScaleDegree get() = degree

    override val prompt: ListeningPrompt
        get() = ListeningPrompt(
            moments = listOf(listOf(target)),
            context = when (context) {
                KeyContext.CADENCE -> key.cadence()
                KeyContext.TONIC_CHORD -> listOf(key.tonicChord())
            },
        )
}

data class ScaleDegreeSettings(
    val modes: Set<Mode> = setOf(Mode.MAJOR),
    val degreeSet: DegreeSet = DegreeSet.DIATONIC,
    val keyChoice: KeyChoice = KeyChoice.RANDOM,
    val context: KeyContext = KeyContext.CADENCE,
    /** Ask more often about degrees recently answered wrongly. */
    val focusOnWeakSpots: Boolean = true,
) {
    init {
        require(modes.isNotEmpty()) { "At least one mode must be enabled" }
    }

    /** Adds or removes [mode], refusing to remove the last one. */
    fun withModeToggled(mode: Mode): ScaleDegreeSettings = when {
        mode !in modes -> copy(modes = modes + mode)
        modes.size > 1 -> copy(modes = modes - mode)
        else -> this
    }

    /** The degrees a question in [mode] can ask about, which are also its answer choices. */
    fun degreesFor(mode: Mode): List<ScaleDegree> = when (degreeSet) {
        DegreeSet.DIATONIC -> mode.diatonicDegrees
        DegreeSet.CHROMATIC -> ScaleDegree.entries
    }
}

class ScaleDegreeQuestionGenerator(
    private val settings: ScaleDegreeSettings = ScaleDegreeSettings(),
    private val random: Random = Random.Default,
    /** Relative likelihood of asking each degree; null asks them all equally often. */
    private val weightOf: ((ScaleDegree) -> Double)? = null,
) {
    fun generate(): ScaleDegreeQuestion {
        val mode = settings.modes.random(random)
        val tonic = settings.keyChoice.pickTonic(random)
        val choices = settings.degreesFor(mode)
        val degree = weightOf?.let { choices.weightedRandom(random, it) } ?: choices.random(random)
        return ScaleDegreeQuestion(Key(tonic, mode), degree, settings.context, choices)
    }
}

/** One answered scale-degree question, as stored in practice history. */
data class ScaleDegreeAttempt(
    val sessionId: String,
    val answeredAtMillis: Long,
    val key: Key,
    val degree: ScaleDegree,
    val context: KeyContext,
    val answer: ScaleDegree,
    val isCorrect: Boolean,
    /** Times the prompt was started before answering; 1 means no replays. */
    val playCount: Int,
    /** From the first time the prompt started playing until the answer. */
    val responseMillis: Long,
) {
    companion object {
        /** The attempt for [state]'s current question, or null if it has not been answered. */
        fun from(state: ExerciseState<ScaleDegree, ScaleDegreeQuestion>, sessionId: String): ScaleDegreeAttempt? {
            val answered = state.phase as? ExercisePhase.Answered<ScaleDegree> ?: return null
            val firstPlayed = state.firstPlayedAtMillis ?: answered.answeredAtMillis
            return ScaleDegreeAttempt(
                sessionId = sessionId,
                answeredAtMillis = answered.answeredAtMillis,
                key = state.question.key,
                degree = state.question.degree,
                context = state.question.context,
                answer = answered.selectedAnswer,
                isCorrect = answered.isCorrect,
                playCount = state.playCount,
                responseMillis = (answered.answeredAtMillis - firstPlayed).coerceAtLeast(0),
            )
        }
    }
}
