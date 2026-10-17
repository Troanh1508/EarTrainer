package com.example.eartrainer.data.history

import android.util.Log
import com.example.eartrainer.exercise.ChordAnswer
import com.example.eartrainer.exercise.ChordAttempt
import com.example.eartrainer.exercise.ChordTarget
import com.example.eartrainer.exercise.ExerciseKind
import com.example.eartrainer.exercise.IntervalAttempt
import com.example.eartrainer.exercise.IntervalStyle
import com.example.eartrainer.exercise.ProgressionAttempt
import com.example.eartrainer.exercise.ScaleDegreeAttempt
import com.example.eartrainer.music.ChordInversion
import com.example.eartrainer.music.ChordProgression
import com.example.eartrainer.music.ChordQuality
import com.example.eartrainer.music.DiatonicChord
import com.example.eartrainer.music.Interval
import com.example.eartrainer.music.Mode
import com.example.eartrainer.music.ScaleDegree
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** How often one answer value (an interval, a chord quality, ...) was identified correctly. */
data class AnswerAccuracy<T>(val item: T, val attempts: Int, val correct: Int) {
    /** Fraction correct, or null when it has not been practised. */
    val accuracy: Float? get() = if (attempts == 0) null else correct.toFloat() / attempts
}

data class SessionSummary(
    val sessionId: String,
    val exercise: ExerciseKind,
    val startedAtMillis: Long,
    val attempts: Int,
    val correct: Int,
)

/** Local record of every answered question, used for progress views and later adaptive practice. */
interface PracticeHistory {
    /** Saves [attempt] in the background. The write completes even if the calling screen closes. */
    fun record(attempt: IntervalAttempt)

    /** Saves [attempt] in the background. The write completes even if the calling screen closes. */
    fun record(attempt: ChordAttempt)

    /** Saves [attempt] in the background. The write completes even if the calling screen closes. */
    fun record(attempt: ScaleDegreeAttempt)

    /** Saves [attempt] in the background. The write completes even if the calling screen closes. */
    fun record(attempt: ProgressionAttempt)

    /** Accuracy for every interval, including unpractised ones; [style] null means all styles. */
    fun intervalAccuracy(style: IntervalStyle?): Flow<List<AnswerAccuracy<Interval>>>

    /**
     * Accuracy for every possible answer to chord questions with [target]: each quality, or
     * each inversion. Unpractised answers are included with zero attempts.
     */
    fun chordAccuracy(target: ChordTarget): Flow<List<AnswerAccuracy<ChordAnswer>>>

    /** Accuracy for every scale degree, including unpractised ones; [mode] null means all modes. */
    fun scaleDegreeAccuracy(mode: Mode?): Flow<List<AnswerAccuracy<ScaleDegree>>>

    /**
     * How often each chord of [mode] was identified correctly inside progressions, counted per
     * position. The opening tonic is given rather than tested, so it is not counted.
     */
    fun progressionAccuracy(mode: Mode): Flow<List<AnswerAccuracy<DiatonicChord>>>

    /** Most recent sessions of any exercise first. Sessions without answers are not recorded. */
    fun recentSessions(limit: Int): Flow<List<SessionSummary>>
}

class RoomPracticeHistory(
    private val database: EarTrainerDatabase,
    /** Application-lifetime scope, so writes are not cancelled with a ViewModel. */
    private val writeScope: CoroutineScope,
) : PracticeHistory {

    override fun record(attempt: IntervalAttempt) = write { database.intervalAttemptDao().insert(attempt.toEntity()) }

    override fun record(attempt: ChordAttempt) = write { database.chordAttemptDao().insert(attempt.toEntity()) }

    override fun record(attempt: ScaleDegreeAttempt) = write { database.scaleDegreeAttemptDao().insert(attempt.toEntity()) }

    override fun record(attempt: ProgressionAttempt) = write { database.progressionAttemptDao().insert(attempt.toEntity()) }

    override fun intervalAccuracy(style: IntervalStyle?): Flow<List<AnswerAccuracy<Interval>>> =
        database.intervalAttemptDao().accuracyByInterval(style?.name).map { rows -> rows.completeOver(Interval.entries) }

    override fun chordAccuracy(target: ChordTarget): Flow<List<AnswerAccuracy<ChordAnswer>>> = when (target) {
        ChordTarget.QUALITY -> database.chordAttemptDao().accuracyByQuality().map { rows ->
            rows.completeOver(ChordQuality.entries).map { AnswerAccuracy(ChordAnswer.Quality(it.item), it.attempts, it.correct) }
        }
        ChordTarget.INVERSION -> database.chordAttemptDao().accuracyByInversion().map { rows ->
            rows.completeOver(ChordInversion.entries).map { AnswerAccuracy(ChordAnswer.Inversion(it.item), it.attempts, it.correct) }
        }
    }

    override fun scaleDegreeAccuracy(mode: Mode?): Flow<List<AnswerAccuracy<ScaleDegree>>> =
        database.scaleDegreeAttemptDao().accuracyByDegree(mode?.name).map { rows -> rows.completeOver(ScaleDegree.entries) }

    override fun progressionAccuracy(mode: Mode): Flow<List<AnswerAccuracy<DiatonicChord>>> =
        database.progressionAttemptDao().answersInMode(mode.name)
            .map { rows -> progressionChordAccuracy(mode, rows.map { it.progression.toSteps() to it.answer.toSteps() }) }
            .flowOn(Dispatchers.Default)

    override fun recentSessions(limit: Int): Flow<List<SessionSummary>> =
        database.sessionDao().recentSessions(limit).map { rows ->
            rows.mapNotNull { row ->
                val exercise = ExerciseKind.entries.find { it.name == row.exercise } ?: return@mapNotNull null
                SessionSummary(row.sessionId, exercise, row.startedAtMillis, row.attempts, row.correct)
            }
        }

    private fun write(insert: suspend () -> Unit) {
        writeScope.launch {
            try {
                insert()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Losing one history row must never interrupt practice.
                Log.w(TAG, "Could not save practice attempt", e)
            }
        }
    }

    private companion object {
        const val TAG = "PracticeHistory"
    }
}

/** One entry per value in [all], in that order; values without rows get zero attempts. */
private fun <T : Enum<T>> List<AccuracyRow>.completeOver(all: List<T>): List<AnswerAccuracy<T>> {
    val byName = associateBy { it.item }
    return all.map { value ->
        val row = byName[value.name]
        AnswerAccuracy(value, attempts = row?.attempts ?: 0, correct = row?.correct ?: 0)
    }
}

internal fun IntervalAttempt.toEntity() = IntervalAttemptEntity(
    sessionId = sessionId,
    answeredAtMillis = answeredAtMillis,
    interval = interval.name,
    style = style.name,
    lowerMidi = lowerPitch.midi,
    answer = answer.name,
    isCorrect = isCorrect,
    playCount = playCount,
    responseMillis = responseMillis,
)

internal fun ScaleDegreeAttempt.toEntity() = ScaleDegreeAttemptEntity(
    sessionId = sessionId,
    answeredAtMillis = answeredAtMillis,
    tonicMidi = key.tonic.midi,
    mode = key.mode.name,
    degree = degree.name,
    context = context.name,
    answer = answer.name,
    isCorrect = isCorrect,
    playCount = playCount,
    responseMillis = responseMillis,
)

internal fun ProgressionAttempt.toEntity() = ProgressionAttemptEntity(
    sessionId = sessionId,
    answeredAtMillis = answeredAtMillis,
    tonicMidi = key.tonic.midi,
    mode = key.mode.name,
    progression = progression.toSteps(),
    answer = answer.toSteps(),
    isCorrect = isCorrect,
    playCount = playCount,
    responseMillis = responseMillis,
)

private fun ChordProgression.toSteps(): String = chords.joinToString(",") { it.step.toString() }

private fun String.toSteps(): List<Int> = split(",").mapNotNull { it.trim().toIntOrNull() }

/**
 * Per-chord accuracy from (asked, chosen) step lists: every position after the first counts as
 * one attempt at the chord asked there, correct when the chosen progression has the same chord.
 */
internal fun progressionChordAccuracy(mode: Mode, answers: List<Pair<List<Int>, List<Int>>>): List<AnswerAccuracy<DiatonicChord>> {
    val attempts = IntArray(8)
    val correct = IntArray(8)
    for ((asked, chosen) in answers) {
        for (position in 1 until asked.size) {
            val step = asked[position].takeIf { it in 1..7 } ?: continue
            attempts[step]++
            if (chosen.getOrNull(position) == step) correct[step]++
        }
    }
    return DiatonicChord.all(mode).map { AnswerAccuracy(it, attempts[it.step], correct[it.step]) }
}

internal fun ChordAttempt.toEntity() = ChordAttemptEntity(
    sessionId = sessionId,
    answeredAtMillis = answeredAtMillis,
    quality = quality.name,
    inversion = inversion.name,
    style = style.name,
    target = target.name,
    bassMidi = bass.midi,
    answer = when (answer) {
        is ChordAnswer.Quality -> answer.quality.name
        is ChordAnswer.Inversion -> answer.inversion.name
    },
    isCorrect = isCorrect,
    playCount = playCount,
    responseMillis = responseMillis,
)
