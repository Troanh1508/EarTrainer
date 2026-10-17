package com.example.eartrainer.testing

import com.example.eartrainer.data.history.AnswerAccuracy
import com.example.eartrainer.data.history.PracticeHistory
import com.example.eartrainer.data.history.SessionSummary
import com.example.eartrainer.data.history.progressionChordAccuracy
import com.example.eartrainer.exercise.ChordAnswer
import com.example.eartrainer.exercise.ChordAttempt
import com.example.eartrainer.exercise.ChordTarget
import com.example.eartrainer.exercise.ExerciseKind
import com.example.eartrainer.exercise.IntervalAttempt
import com.example.eartrainer.exercise.IntervalStyle
import com.example.eartrainer.exercise.ProgressionAttempt
import com.example.eartrainer.exercise.ScaleDegreeAttempt
import com.example.eartrainer.music.ChordInversion
import com.example.eartrainer.music.ChordQuality
import com.example.eartrainer.music.DiatonicChord
import com.example.eartrainer.music.Interval
import com.example.eartrainer.music.Mode
import com.example.eartrainer.music.ScaleDegree
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [PracticeHistory] with the same aggregation rules as the Room implementation. */
class FakePracticeHistory : PracticeHistory {
    private val intervals = MutableStateFlow<List<IntervalAttempt>>(emptyList())
    private val chords = MutableStateFlow<List<ChordAttempt>>(emptyList())
    private val degrees = MutableStateFlow<List<ScaleDegreeAttempt>>(emptyList())
    private val progressions = MutableStateFlow<List<ProgressionAttempt>>(emptyList())

    val recorded: List<IntervalAttempt> get() = intervals.value

    override fun record(attempt: IntervalAttempt) = intervals.update { it + attempt }

    override fun record(attempt: ChordAttempt) = chords.update { it + attempt }

    override fun record(attempt: ScaleDegreeAttempt) = degrees.update { it + attempt }

    override fun record(attempt: ProgressionAttempt) = progressions.update { it + attempt }

    override fun intervalAccuracy(style: IntervalStyle?): Flow<List<AnswerAccuracy<Interval>>> = intervals.map { all ->
        Interval.entries.map { interval ->
            val matching = all.filter { it.interval == interval && (style == null || it.style == style) }
            AnswerAccuracy(interval, attempts = matching.size, correct = matching.count { it.isCorrect })
        }
    }

    override fun chordAccuracy(target: ChordTarget): Flow<List<AnswerAccuracy<ChordAnswer>>> = chords.map { all ->
        val asked = all.filter { it.target == target }
        when (target) {
            ChordTarget.QUALITY -> ChordQuality.entries.map { quality ->
                val matching = asked.filter { it.quality == quality }
                AnswerAccuracy<ChordAnswer>(ChordAnswer.Quality(quality), matching.size, matching.count { it.isCorrect })
            }
            ChordTarget.INVERSION -> ChordInversion.entries.map { inversion ->
                val matching = asked.filter { it.inversion == inversion }
                AnswerAccuracy<ChordAnswer>(ChordAnswer.Inversion(inversion), matching.size, matching.count { it.isCorrect })
            }
        }
    }

    override fun scaleDegreeAccuracy(mode: Mode?): Flow<List<AnswerAccuracy<ScaleDegree>>> = degrees.map { all ->
        ScaleDegree.entries.map { degree ->
            val matching = all.filter { it.degree == degree && (mode == null || it.key.mode == mode) }
            AnswerAccuracy(degree, attempts = matching.size, correct = matching.count { it.isCorrect })
        }
    }

    // Uses the same per-position scoring function as the Room implementation.
    override fun progressionAccuracy(mode: Mode): Flow<List<AnswerAccuracy<DiatonicChord>>> = progressions.map { all ->
        progressionChordAccuracy(
            mode,
            all.filter { it.key.mode == mode }.map { attempt -> attempt.progression.chords.map { it.step } to attempt.answer.chords.map { it.step } },
        )
    }

    override fun recentSessions(limit: Int): Flow<List<SessionSummary>> = combine(intervals, chords, degrees, progressions) { intervalList, chordList, degreeList, progressionList ->
        val rows = intervalList.map { Triple(it.sessionId, ExerciseKind.INTERVALS, it.answeredAtMillis to it.isCorrect) } +
            chordList.map { Triple(it.sessionId, ExerciseKind.CHORDS, it.answeredAtMillis to it.isCorrect) } +
            degreeList.map { Triple(it.sessionId, ExerciseKind.SCALE_DEGREES, it.answeredAtMillis to it.isCorrect) } +
            progressionList.map { Triple(it.sessionId, ExerciseKind.PROGRESSIONS, it.answeredAtMillis to it.isCorrect) }
        rows.groupBy { it.first }
            .map { (id, session) ->
                SessionSummary(
                    sessionId = id,
                    exercise = session.first().second,
                    startedAtMillis = session.minOf { it.third.first },
                    attempts = session.size,
                    correct = session.count { it.third.second },
                )
            }
            .sortedByDescending { it.startedAtMillis }
            .take(limit)
    }
}
