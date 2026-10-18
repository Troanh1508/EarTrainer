package com.example.eartrainer.testing

import com.example.eartrainer.data.history.AnswerAccuracy
import com.example.eartrainer.data.history.PracticeHistory
import com.example.eartrainer.data.history.SessionSummary
import com.example.eartrainer.data.history.dictationNoteResults
import com.example.eartrainer.data.history.progressionChordAccuracy
import com.example.eartrainer.data.history.progressionChordResults
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
import com.example.eartrainer.music.ChordQuality
import com.example.eartrainer.music.DiatonicChord
import com.example.eartrainer.music.Interval
import com.example.eartrainer.music.Mode
import com.example.eartrainer.music.RhythmCell
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
    private val dictations = MutableStateFlow<List<DictationAttempt>>(emptyList())
    private val rhythms = MutableStateFlow<List<RhythmAttempt>>(emptyList())

    val recorded: List<IntervalAttempt> get() = intervals.value

    override fun record(attempt: IntervalAttempt) = intervals.update { it + attempt }

    override fun record(attempt: ChordAttempt) = chords.update { it + attempt }

    override fun record(attempt: ScaleDegreeAttempt) = degrees.update { it + attempt }

    override fun record(attempt: ProgressionAttempt) = progressions.update { it + attempt }

    override fun record(attempt: DictationAttempt) = dictations.update { it + attempt }

    override fun record(attempt: RhythmAttempt) = rhythms.update { it + attempt }

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

    override fun recentIntervalResults(limit: Int): Flow<List<ItemResult<Interval>>> =
        intervals.map { all -> all.asReversed().take(limit).map { ItemResult(it.interval, it.isCorrect) } }

    override fun recentChordResults(limit: Int): Flow<List<ItemResult<ChordAnswer>>> =
        chords.map { all ->
            all.asReversed().take(limit).map { attempt ->
                val item = if (attempt.target == ChordTarget.QUALITY) ChordAnswer.Quality(attempt.quality) else ChordAnswer.Inversion(attempt.inversion)
                ItemResult(item, attempt.isCorrect)
            }
        }

    override fun recentScaleDegreeResults(limit: Int): Flow<List<ItemResult<ScaleDegree>>> =
        degrees.map { all -> all.asReversed().take(limit).map { ItemResult(it.degree, it.isCorrect) } }

    override fun recentProgressionResults(limit: Int): Flow<List<ItemResult<DiatonicChord>>> =
        progressions.map { all ->
            all.asReversed().take(limit).flatMap { attempt ->
                progressionChordResults(attempt.key.mode, attempt.progression.chords.map { it.step }, attempt.answer.chords.map { it.step })
            }
        }

    override fun dictationAccuracy(mode: Mode): Flow<List<AnswerAccuracy<ScaleDegree>>> = dictations.map { all ->
        val results = all.filter { it.key.mode == mode }.flatMap { dictationNoteResults(it.melody.degrees, it.answer.degrees) }
        mode.diatonicDegrees.map { degree ->
            val forDegree = results.filter { it.item == degree }
            AnswerAccuracy(degree, attempts = forDegree.size, correct = forDegree.count { it.isCorrect })
        }
    }

    override fun recentDictationResults(limit: Int): Flow<List<ItemResult<ScaleDegree>>> =
        dictations.map { all -> all.asReversed().take(limit).flatMap { dictationNoteResults(it.melody.degrees, it.answer.degrees) } }

    override fun rhythmAccuracy(): Flow<List<AnswerAccuracy<RhythmCell>>> = rhythms.map { all ->
        val results = all.flatMap { rhythmCellResults(it.rhythm, it.answer) }
        RhythmCell.entries.map { cell ->
            val forCell = results.filter { it.item == cell }
            AnswerAccuracy(cell, attempts = forCell.size, correct = forCell.count { it.isCorrect })
        }
    }

    override fun recentRhythmResults(limit: Int): Flow<List<ItemResult<RhythmCell>>> =
        rhythms.map { all -> all.asReversed().take(limit).flatMap { rhythmCellResults(it.rhythm, it.answer) } }

    // combine() takes at most five flows, so the sixth exercise is merged in a second step.
    override fun recentSessions(limit: Int, exercise: ExerciseKind?): Flow<List<SessionSummary>> = combine(
        combine(intervals, chords, degrees, progressions, dictations) { intervalList, chordList, degreeList, progressionList, dictationList ->
            intervalList.map { Triple(it.sessionId, ExerciseKind.INTERVALS, it.answeredAtMillis to it.isCorrect) } +
                chordList.map { Triple(it.sessionId, ExerciseKind.CHORDS, it.answeredAtMillis to it.isCorrect) } +
                degreeList.map { Triple(it.sessionId, ExerciseKind.SCALE_DEGREES, it.answeredAtMillis to it.isCorrect) } +
                progressionList.map { Triple(it.sessionId, ExerciseKind.PROGRESSIONS, it.answeredAtMillis to it.isCorrect) } +
                dictationList.map { Triple(it.sessionId, ExerciseKind.DICTATION, it.answeredAtMillis to it.isCorrect) }
        },
        rhythms,
    ) { earlier, rhythmList ->
        val rows = earlier + rhythmList.map { Triple(it.sessionId, ExerciseKind.RHYTHM, it.answeredAtMillis to it.isCorrect) }
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
            .filter { exercise == null || it.exercise == exercise }
            .take(limit)
    }
}
