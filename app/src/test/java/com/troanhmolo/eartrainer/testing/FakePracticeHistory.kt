package com.troanhmolo.eartrainer.testing

import com.troanhmolo.eartrainer.data.history.AnswerAccuracy
import com.troanhmolo.eartrainer.data.history.Confusion
import com.troanhmolo.eartrainer.data.history.DayCount
import com.troanhmolo.eartrainer.data.history.DaySessions
import com.troanhmolo.eartrainer.data.history.countConfusions
import com.troanhmolo.eartrainer.data.history.dictationNotePairs
import com.troanhmolo.eartrainer.data.history.progressionChordPairs
import com.troanhmolo.eartrainer.data.history.PracticeHistory
import com.troanhmolo.eartrainer.data.history.SessionSummary
import com.troanhmolo.eartrainer.data.history.dictationNoteResults
import com.troanhmolo.eartrainer.data.history.progressionChordAccuracy
import com.troanhmolo.eartrainer.data.history.progressionChordResults
import com.troanhmolo.eartrainer.exercise.ChordAnswer
import com.troanhmolo.eartrainer.exercise.ChordAttempt
import com.troanhmolo.eartrainer.exercise.ChordTarget
import com.troanhmolo.eartrainer.exercise.DictationAttempt
import com.troanhmolo.eartrainer.exercise.ExerciseKind
import com.troanhmolo.eartrainer.exercise.IntervalAttempt
import com.troanhmolo.eartrainer.exercise.IntervalStyle
import com.troanhmolo.eartrainer.exercise.ItemResult
import com.troanhmolo.eartrainer.exercise.ProgressionAttempt
import com.troanhmolo.eartrainer.exercise.RhythmAttempt
import com.troanhmolo.eartrainer.exercise.rhythmCellResults
import com.troanhmolo.eartrainer.exercise.rhythmCellPairs
import com.troanhmolo.eartrainer.exercise.SESSION_LENGTH
import com.troanhmolo.eartrainer.exercise.ScaleDegreeAttempt
import com.troanhmolo.eartrainer.music.ChordInversion
import com.troanhmolo.eartrainer.music.ChordQuality
import com.troanhmolo.eartrainer.music.DiatonicChord
import com.troanhmolo.eartrainer.music.Interval
import com.troanhmolo.eartrainer.music.Mode
import com.troanhmolo.eartrainer.music.RhythmCell
import com.troanhmolo.eartrainer.music.ScaleDegree
import com.troanhmolo.eartrainer.ui.log.localEpochDay
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

    override fun intervalConfusions(style: IntervalStyle?): Flow<List<Confusion<Interval>>> = intervals.map { all ->
        all.filter { style == null || it.style == style }.map { it.interval to it.answer }.countConfusions()
    }

    override fun chordConfusions(target: ChordTarget): Flow<List<Confusion<ChordAnswer>>> = chords.map { all ->
        all.filter { it.target == target }.map { attempt ->
            val asked: ChordAnswer = when (target) {
                ChordTarget.QUALITY -> ChordAnswer.Quality(attempt.quality)
                ChordTarget.INVERSION -> ChordAnswer.Inversion(attempt.inversion)
            }
            asked to attempt.answer
        }.countConfusions()
    }

    override fun scaleDegreeConfusions(mode: Mode?): Flow<List<Confusion<ScaleDegree>>> = degrees.map { all ->
        all.filter { mode == null || it.key.mode == mode }.map { it.degree to it.answer }.countConfusions()
    }

    override fun progressionConfusions(mode: Mode): Flow<List<Confusion<DiatonicChord>>> = progressions.map { all ->
        all.filter { it.key.mode == mode }
            .flatMap { progressionChordPairs(mode, it.progression.chords.map { chord -> chord.step }, it.answer.chords.map { chord -> chord.step }) }
            .countConfusions()
    }

    override fun dictationConfusions(mode: Mode): Flow<List<Confusion<ScaleDegree>>> = dictations.map { all ->
        all.filter { it.key.mode == mode }.flatMap { dictationNotePairs(it.melody.degrees, it.answer.degrees) }.countConfusions()
    }

    override fun rhythmConfusions(): Flow<List<Confusion<RhythmCell>>> =
        rhythms.map { all -> all.flatMap { rhythmCellPairs(it.rhythm, it.answer) }.countConfusions() }

    override fun answersPerDay(): Flow<List<DayCount>> = combine(
        combine(intervals, chords, degrees, progressions, dictations) { a, b, c, d, e ->
            a.map { it.answeredAtMillis } + b.map { it.answeredAtMillis } + c.map { it.answeredAtMillis } +
                d.map { it.answeredAtMillis } + e.map { it.answeredAtMillis }
        },
        rhythms,
    ) { earlier, rhythmList ->
        (earlier + rhythmList.map { it.answeredAtMillis })
            .groupingBy { localEpochDay(it) }.eachCount()
            .map { (day, answers) -> DayCount(day, answers) }
            .sortedBy { it.day }
    }

    override fun finishedSessionsPerDay(): Flow<List<DaySessions>> = combine(
        combine(intervals, chords, degrees, progressions, dictations) { a, b, c, d, e ->
            a.map { it.sessionId to it.answeredAtMillis } + b.map { it.sessionId to it.answeredAtMillis } +
                c.map { it.sessionId to it.answeredAtMillis } + d.map { it.sessionId to it.answeredAtMillis } +
                e.map { it.sessionId to it.answeredAtMillis }
        },
        rhythms,
    ) { earlier, rhythmList ->
        (earlier + rhythmList.map { it.sessionId to it.answeredAtMillis })
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size >= SESSION_LENGTH }
            .values.groupingBy { localEpochDay(it.max()) }.eachCount()
            .map { (day, sessions) -> DaySessions(day, sessions) }
            .sortedBy { it.day }
    }

    // combine() takes at most five flows, so the sixth exercise is merged in a second step.
    override fun recentSessions(limit: Int, exercise: ExerciseKind?): Flow<List<SessionSummary>> = combine(
        combine(intervals, chords, degrees, progressions, dictations) { intervalList, chordList, degreeList, progressionList, dictationList ->
            intervalList.toSessions() + chordList.toSessions() + degreeList.toSessions() +
                progressionList.toSessions() + dictationList.toSessions()
        },
        rhythms,
    ) { earlier, rhythmList ->
        (earlier + rhythmList.toSessions())
            .sortedByDescending { it.startedAtMillis }
            .filter { exercise == null || it.exercise == exercise }
            .take(limit)
    }

    override fun intervalSessions(style: IntervalStyle?, limit: Int): Flow<List<SessionSummary>> =
        intervals.map { all -> all.filter { style == null || it.style == style }.toSessions().take(limit) }

    override fun chordSessions(target: ChordTarget, limit: Int): Flow<List<SessionSummary>> =
        chords.map { all -> all.filter { it.target == target }.toSessions().take(limit) }

    override fun scaleDegreeSessions(mode: Mode?, limit: Int): Flow<List<SessionSummary>> =
        degrees.map { all -> all.filter { mode == null || it.key.mode == mode }.toSessions().take(limit) }

    override fun progressionSessions(mode: Mode, limit: Int): Flow<List<SessionSummary>> =
        progressions.map { all -> all.filter { it.key.mode == mode }.toSessions().take(limit) }

    override fun dictationSessions(mode: Mode, limit: Int): Flow<List<SessionSummary>> =
        dictations.map { all -> all.filter { it.key.mode == mode }.toSessions().take(limit) }

    override fun rhythmSessions(limit: Int): Flow<List<SessionSummary>> = rhythms.map { all -> all.toSessions().take(limit) }

    @JvmName("intervalSessionsOf")
    private fun List<IntervalAttempt>.toSessions() =
        sessions(ExerciseKind.INTERVALS, { it.sessionId }, { it.answeredAtMillis }, { it.isCorrect }) { listOf(it.isCorrect) }

    @JvmName("chordSessionsOf")
    private fun List<ChordAttempt>.toSessions() =
        sessions(ExerciseKind.CHORDS, { it.sessionId }, { it.answeredAtMillis }, { it.isCorrect }) { listOf(it.isCorrect) }

    @JvmName("degreeSessionsOf")
    private fun List<ScaleDegreeAttempt>.toSessions() =
        sessions(ExerciseKind.SCALE_DEGREES, { it.sessionId }, { it.answeredAtMillis }, { it.isCorrect }) { listOf(it.isCorrect) }

    @JvmName("progressionSessionsOf")
    private fun List<ProgressionAttempt>.toSessions() =
        sessions(ExerciseKind.PROGRESSIONS, { it.sessionId }, { it.answeredAtMillis }, { it.isCorrect }) { attempt ->
            progressionChordResults(attempt.key.mode, attempt.progression.chords.map { it.step }, attempt.answer.chords.map { it.step })
                .map { it.isCorrect }
        }

    @JvmName("dictationSessionsOf")
    private fun List<DictationAttempt>.toSessions() =
        sessions(ExerciseKind.DICTATION, { it.sessionId }, { it.answeredAtMillis }, { it.isCorrect }) { attempt ->
            dictationNoteResults(attempt.melody.degrees, attempt.answer.degrees).map { it.isCorrect }
        }

    @JvmName("rhythmSessionsOf")
    private fun List<RhythmAttempt>.toSessions() =
        sessions(ExerciseKind.RHYTHM, { it.sessionId }, { it.answeredAtMillis }, { it.isCorrect }) { attempt ->
            rhythmCellResults(attempt.rhythm, attempt.answer).map { it.isCorrect }
        }

    /** Groups answers into sessions, newest first, with [itemsOf] giving each answer's scored items. */
    private fun <A> List<A>.sessions(
        exercise: ExerciseKind,
        sessionOf: (A) -> String,
        answeredAt: (A) -> Long,
        isCorrect: (A) -> Boolean,
        itemsOf: (A) -> List<Boolean>,
    ): List<SessionSummary> = groupBy(sessionOf)
        .map { (id, answers) ->
            val items = answers.flatMap(itemsOf)
            SessionSummary(
                id, exercise, answers.minOf(answeredAt), answers.size, answers.count(isCorrect),
                items = items.size, itemsCorrect = items.count { it },
            )
        }
        .filter { it.items > 0 }
        .sortedByDescending { it.startedAtMillis }
}
