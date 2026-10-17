package com.example.eartrainer.data.history

import android.util.Log
import com.example.eartrainer.exercise.ChordAnswer
import com.example.eartrainer.exercise.ChordAttempt
import com.example.eartrainer.exercise.ChordTarget
import com.example.eartrainer.exercise.DictationAttempt
import com.example.eartrainer.exercise.ExerciseKind
import com.example.eartrainer.exercise.IntervalAttempt
import com.example.eartrainer.exercise.IntervalStyle
import com.example.eartrainer.exercise.ItemResult
import com.example.eartrainer.exercise.ProgressionAttempt
import com.example.eartrainer.exercise.RhythmAttempt
import com.example.eartrainer.exercise.rhythmCellResults
import com.example.eartrainer.exercise.ScaleDegreeAttempt
import com.example.eartrainer.music.ChordInversion
import com.example.eartrainer.music.ChordProgression
import com.example.eartrainer.music.ChordQuality
import com.example.eartrainer.music.DiatonicChord
import com.example.eartrainer.music.Interval
import com.example.eartrainer.music.Melody
import com.example.eartrainer.music.Mode
import com.example.eartrainer.music.Rhythm
import com.example.eartrainer.music.RhythmCell
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

/** Local record of every answered question, used for progress views and weak-spot practice. */
interface PracticeHistory {
    /** Saves [attempt] in the background. The write completes even if the calling screen closes. */
    fun record(attempt: IntervalAttempt)

    /** Saves [attempt] in the background. The write completes even if the calling screen closes. */
    fun record(attempt: ChordAttempt)

    /** Saves [attempt] in the background. The write completes even if the calling screen closes. */
    fun record(attempt: ScaleDegreeAttempt)

    /** Saves [attempt] in the background. The write completes even if the calling screen closes. */
    fun record(attempt: ProgressionAttempt)

    /** Saves [attempt] in the background. The write completes even if the calling screen closes. */
    fun record(attempt: DictationAttempt)

    /** Saves [attempt] in the background. The write completes even if the calling screen closes. */
    fun record(attempt: RhythmAttempt)

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

    /**
     * How often each degree of [mode] was written down correctly in dictations, counted per note.
     * The opening tonic is given rather than tested, so it is not counted.
     */
    fun dictationAccuracy(mode: Mode): Flow<List<AnswerAccuracy<ScaleDegree>>>

    /** How often each rhythm cell was heard correctly, counted per cell of every rhythm asked. */
    fun rhythmAccuracy(): Flow<List<AnswerAccuracy<RhythmCell>>>

    /** Most recent sessions of any exercise first. Sessions without answers are not recorded. */
    fun recentSessions(limit: Int): Flow<List<SessionSummary>>

    /** The latest [limit] interval answers, newest first, for weighting practice. */
    fun recentIntervalResults(limit: Int): Flow<List<ItemResult<Interval>>>

    /** The latest [limit] chord answers; each result is a quality or an inversion, as asked. */
    fun recentChordResults(limit: Int): Flow<List<ItemResult<ChordAnswer>>>

    fun recentScaleDegreeResults(limit: Int): Flow<List<ItemResult<ScaleDegree>>>

    /** One result per chord after the opening tonic, from the latest [limit] progression answers. */
    fun recentProgressionResults(limit: Int): Flow<List<ItemResult<DiatonicChord>>>

    /** One result per note after the opening tonic, from the latest [limit] dictations. */
    fun recentDictationResults(limit: Int): Flow<List<ItemResult<ScaleDegree>>>

    /** One result per cell, from the latest [limit] rhythm answers. */
    fun recentRhythmResults(limit: Int): Flow<List<ItemResult<RhythmCell>>>
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

    override fun record(attempt: DictationAttempt) = write { database.dictationAttemptDao().insert(attempt.toEntity()) }

    override fun record(attempt: RhythmAttempt) = write { database.rhythmAttemptDao().insert(attempt.toEntity()) }

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

    override fun dictationAccuracy(mode: Mode): Flow<List<AnswerAccuracy<ScaleDegree>>> =
        database.dictationAttemptDao().answersInMode(mode.name)
            .map { rows ->
                val results = rows.flatMap { dictationNoteResults(it.melody.toDegrees(), it.answer.toDegrees()) }
                mode.diatonicDegrees.map { degree ->
                    val forDegree = results.filter { it.item == degree }
                    AnswerAccuracy(degree, attempts = forDegree.size, correct = forDegree.count { it.isCorrect })
                }
            }
            .flowOn(Dispatchers.Default)

    override fun rhythmAccuracy(): Flow<List<AnswerAccuracy<RhythmCell>>> =
        database.rhythmAttemptDao().allAnswers()
            .map { rows ->
                val results = rows.flatMap { it.toCellResults() }
                RhythmCell.entries.map { cell ->
                    val forCell = results.filter { it.item == cell }
                    AnswerAccuracy(cell, attempts = forCell.size, correct = forCell.count { it.isCorrect })
                }
            }
            .flowOn(Dispatchers.Default)

    override fun recentSessions(limit: Int): Flow<List<SessionSummary>> =
        database.sessionDao().recentSessions(limit).map { rows ->
            rows.mapNotNull { row ->
                val exercise = ExerciseKind.entries.find { it.name == row.exercise } ?: return@mapNotNull null
                SessionSummary(row.sessionId, exercise, row.startedAtMillis, row.attempts, row.correct)
            }
        }

    override fun recentIntervalResults(limit: Int): Flow<List<ItemResult<Interval>>> =
        database.intervalAttemptDao().recentResults(limit).map { rows -> rows.toResults(Interval.entries) }

    override fun recentChordResults(limit: Int): Flow<List<ItemResult<ChordAnswer>>> =
        database.chordAttemptDao().recentResults(limit).map { rows ->
            rows.mapNotNull { row ->
                val answer: ChordAnswer? = when (row.target) {
                    ChordTarget.QUALITY.name -> ChordQuality.entries.find { it.name == row.quality }?.let(ChordAnswer::Quality)
                    ChordTarget.INVERSION.name -> ChordInversion.entries.find { it.name == row.inversion }?.let(ChordAnswer::Inversion)
                    else -> null
                }
                answer?.let { ItemResult(it, row.isCorrect) }
            }
        }

    override fun recentScaleDegreeResults(limit: Int): Flow<List<ItemResult<ScaleDegree>>> =
        database.scaleDegreeAttemptDao().recentResults(limit).map { rows -> rows.toResults(ScaleDegree.entries) }

    override fun recentProgressionResults(limit: Int): Flow<List<ItemResult<DiatonicChord>>> =
        database.progressionAttemptDao().recentResults(limit).map { rows ->
            rows.flatMap { row ->
                val mode = Mode.entries.find { it.name == row.mode } ?: return@flatMap emptyList()
                progressionChordResults(mode, row.progression.toSteps(), row.answer.toSteps())
            }
        }

    override fun recentDictationResults(limit: Int): Flow<List<ItemResult<ScaleDegree>>> =
        database.dictationAttemptDao().recentAnswers(limit).map { rows ->
            rows.flatMap { dictationNoteResults(it.melody.toDegrees(), it.answer.toDegrees()) }
        }

    override fun recentRhythmResults(limit: Int): Flow<List<ItemResult<RhythmCell>>> =
        database.rhythmAttemptDao().recentAnswers(limit).map { rows -> rows.flatMap { it.toCellResults() } }

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

/** Results for rows whose stored name still matches a value in [all]; others are skipped. */
private fun <T : Enum<T>> List<ItemResultRow>.toResults(all: List<T>): List<ItemResult<T>> {
    val byName = all.associateBy { it.name }
    return mapNotNull { row -> byName[row.item]?.let { ItemResult(it, row.isCorrect) } }
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
 * Scores a progression answer chord by chord: every position after the opening tonic is one
 * result for the chord asked there, correct when the chosen progression has the same chord.
 */
internal fun progressionChordResults(mode: Mode, asked: List<Int>, chosen: List<Int>): List<ItemResult<DiatonicChord>> =
    (1 until asked.size).mapNotNull { position ->
        val step = asked[position].takeIf { it in 1..7 } ?: return@mapNotNull null
        ItemResult(DiatonicChord(mode, step), chosen.getOrNull(position) == step)
    }

/** Per-chord accuracy over many (asked, chosen) step lists; see [progressionChordResults]. */
internal fun progressionChordAccuracy(mode: Mode, answers: List<Pair<List<Int>, List<Int>>>): List<AnswerAccuracy<DiatonicChord>> {
    val results = answers.flatMap { (asked, chosen) -> progressionChordResults(mode, asked, chosen) }
    return DiatonicChord.all(mode).map { chord ->
        val forChord = results.filter { it.item == chord }
        AnswerAccuracy(chord, attempts = forChord.size, correct = forChord.count { it.isCorrect })
    }
}

internal fun DictationAttempt.toEntity() = DictationAttemptEntity(
    sessionId = sessionId,
    answeredAtMillis = answeredAtMillis,
    tonicMidi = key.tonic.midi,
    mode = key.mode.name,
    melody = melody.toNames(),
    answer = answer.toNames(),
    isCorrect = isCorrect,
    playCount = playCount,
    responseMillis = responseMillis,
)

private fun Melody.toNames(): String = degrees.joinToString(",") { it.name }

private fun String.toDegrees(): List<ScaleDegree?> = split(",").map { name -> ScaleDegree.entries.find { it.name == name.trim() } }

/**
 * Scores a dictation note by note: every note after the given opening tonic is one result for
 * the degree played there, correct when the written melody has the same degree in that place.
 */
internal fun dictationNoteResults(melody: List<ScaleDegree?>, answer: List<ScaleDegree?>): List<ItemResult<ScaleDegree>> =
    (1 until melody.size).mapNotNull { position ->
        val played = melody[position] ?: return@mapNotNull null
        ItemResult(played, answer.getOrNull(position) == played)
    }

internal fun RhythmAttempt.toEntity() = RhythmAttemptEntity(
    sessionId = sessionId,
    answeredAtMillis = answeredAtMillis,
    tempo = tempo.name,
    rhythm = rhythm.toNames(),
    answer = answer.toNames(),
    isCorrect = isCorrect,
    playCount = playCount,
    responseMillis = responseMillis,
)

private fun Rhythm.toNames(): String = cells.joinToString(",") { it.name }

/** Null if a stored rhythm no longer parses (e.g. a renamed cell), so one bad row is skipped. */
private fun String.toRhythmOrNull(): Rhythm? = runCatching {
    Rhythm(split(",").map { name -> RhythmCell.valueOf(name.trim()) })
}.getOrNull()

private fun RhythmAnswerRow.toCellResults(): List<ItemResult<RhythmCell>> {
    val played = rhythm.toRhythmOrNull() ?: return emptyList()
    val chosen = answer.toRhythmOrNull() ?: return emptyList()
    return rhythmCellResults(played, chosen)
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
