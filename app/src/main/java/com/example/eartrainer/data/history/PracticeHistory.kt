package com.example.eartrainer.data.history

import android.util.Log
import com.example.eartrainer.exercise.IntervalAttempt
import com.example.eartrainer.exercise.IntervalStyle
import com.example.eartrainer.music.Interval
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class IntervalAccuracy(val interval: Interval, val attempts: Int, val correct: Int) {
    /** Fraction correct, or null when the interval has not been practised. */
    val accuracy: Float? get() = if (attempts == 0) null else correct.toFloat() / attempts
}

data class SessionSummary(val sessionId: String, val startedAtMillis: Long, val attempts: Int, val correct: Int)

/** Local record of every answered question, used for progress views and later adaptive practice. */
interface PracticeHistory {
    /** Saves [attempt] in the background. The write completes even if the calling screen closes. */
    fun record(attempt: IntervalAttempt)

    /** Accuracy for every interval, including unpractised ones; [style] null means all styles. */
    fun intervalAccuracy(style: IntervalStyle?): Flow<List<IntervalAccuracy>>

    /** Most recent sessions first. Sessions without answers are not recorded at all. */
    fun recentSessions(limit: Int): Flow<List<SessionSummary>>
}

class RoomPracticeHistory(
    private val dao: IntervalAttemptDao,
    /** Application-lifetime scope, so writes are not cancelled with a ViewModel. */
    private val writeScope: CoroutineScope,
) : PracticeHistory {

    override fun record(attempt: IntervalAttempt) {
        writeScope.launch {
            try {
                dao.insert(attempt.toEntity())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Losing one history row must never interrupt practice.
                Log.w(TAG, "Could not save practice attempt", e)
            }
        }
    }

    override fun intervalAccuracy(style: IntervalStyle?): Flow<List<IntervalAccuracy>> =
        dao.accuracyByInterval(style?.name).map { rows ->
            val byInterval = rows.associateBy { it.interval }
            Interval.entries.map { interval ->
                val row = byInterval[interval.name]
                IntervalAccuracy(interval, attempts = row?.attempts ?: 0, correct = row?.correct ?: 0)
            }
        }

    override fun recentSessions(limit: Int): Flow<List<SessionSummary>> =
        dao.recentSessions(limit).map { rows ->
            rows.map { SessionSummary(it.sessionId, it.startedAtMillis, it.attempts, it.correct) }
        }

    private companion object {
        const val TAG = "PracticeHistory"
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
