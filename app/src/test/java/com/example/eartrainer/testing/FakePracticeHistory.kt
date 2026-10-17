package com.example.eartrainer.testing

import com.example.eartrainer.data.history.IntervalAccuracy
import com.example.eartrainer.data.history.PracticeHistory
import com.example.eartrainer.data.history.SessionSummary
import com.example.eartrainer.exercise.IntervalAttempt
import com.example.eartrainer.exercise.IntervalStyle
import com.example.eartrainer.music.Interval
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [PracticeHistory] with the same aggregation rules as the Room implementation. */
class FakePracticeHistory : PracticeHistory {
    private val attempts = MutableStateFlow<List<IntervalAttempt>>(emptyList())

    val recorded: List<IntervalAttempt> get() = attempts.value

    override fun record(attempt: IntervalAttempt) {
        attempts.update { it + attempt }
    }

    override fun intervalAccuracy(style: IntervalStyle?): Flow<List<IntervalAccuracy>> = attempts.map { all ->
        Interval.entries.map { interval ->
            val matching = all.filter { it.interval == interval && (style == null || it.style == style) }
            IntervalAccuracy(interval, attempts = matching.size, correct = matching.count { it.isCorrect })
        }
    }

    override fun recentSessions(limit: Int): Flow<List<SessionSummary>> = attempts.map { all ->
        all.groupBy { it.sessionId }
            .map { (id, session) ->
                SessionSummary(id, session.minOf { it.answeredAtMillis }, session.size, session.count { it.isCorrect })
            }
            .sortedByDescending { it.startedAtMillis }
            .take(limit)
    }
}
