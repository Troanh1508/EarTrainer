package com.example.eartrainer.data.history

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.eartrainer.exercise.ChordAnswer
import com.example.eartrainer.exercise.ChordAttempt
import com.example.eartrainer.exercise.ChordStyle
import com.example.eartrainer.exercise.ChordTarget
import com.example.eartrainer.exercise.DictationAttempt
import com.example.eartrainer.exercise.ExerciseKind
import com.example.eartrainer.exercise.IntervalAttempt
import com.example.eartrainer.exercise.IntervalStyle
import com.example.eartrainer.exercise.ItemResult
import com.example.eartrainer.exercise.KeyContext
import com.example.eartrainer.exercise.ProgressionAttempt
import com.example.eartrainer.exercise.ScaleDegreeAttempt
import com.example.eartrainer.music.ChordInversion
import com.example.eartrainer.music.ChordProgression
import com.example.eartrainer.music.ChordQuality
import com.example.eartrainer.music.DiatonicChord
import com.example.eartrainer.music.Interval
import com.example.eartrainer.music.Key
import com.example.eartrainer.music.Melody
import com.example.eartrainer.music.Mode
import com.example.eartrainer.music.Pitch
import com.example.eartrainer.music.ScaleDegree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real SQL queries against an in-memory database on a device or emulator. */
@RunWith(AndroidJUnit4::class)
class RoomPracticeHistoryTest {

    private lateinit var database: EarTrainerDatabase

    // Queries never use the write scope; only record() does.
    private val unusedScope = CoroutineScope(SupervisorJob())

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EarTrainerDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() = database.close()

    private fun interval(session: String, at: Long, interval: Interval, style: IntervalStyle, correct: Boolean) =
        IntervalAttempt(session, at, interval, style, Pitch(60), if (correct) interval else Interval.TRITONE, correct, 2, 1_500)

    private fun chord(session: String, at: Long, quality: ChordQuality, inversion: ChordInversion, target: ChordTarget, correct: Boolean) =
        ChordAttempt(
            session, at, quality, inversion, ChordStyle.ARPEGGIATED, target, Pitch(55),
            answer = if (target == ChordTarget.QUALITY) ChordAnswer.Quality(quality) else ChordAnswer.Inversion(inversion),
            isCorrect = correct, playCount = 1, responseMillis = 2_000,
        )

    private fun insertAll(vararg attempts: IntervalAttempt) = runBlocking {
        attempts.forEach { database.intervalAttemptDao().insert(it.toEntity()) }
    }

    private fun insertChords(vararg attempts: ChordAttempt) = runBlocking {
        attempts.forEach { database.chordAttemptDao().insert(it.toEntity()) }
    }

    @Test
    fun intervalAccuracyCoversEveryIntervalAndFiltersByStyle() = runBlocking {
        insertAll(
            interval("s1", 1_000, Interval.MAJOR_THIRD, IntervalStyle.MELODIC_ASCENDING, correct = true),
            interval("s1", 2_000, Interval.MAJOR_THIRD, IntervalStyle.HARMONIC, correct = false),
            interval("s1", 3_000, Interval.MAJOR_THIRD, IntervalStyle.HARMONIC, correct = true),
            interval("s2", 4_000, Interval.OCTAVE, IntervalStyle.MELODIC_ASCENDING, correct = true),
        )
        val history = RoomPracticeHistory(database, unusedScope)

        val all = history.intervalAccuracy(style = null).first()
        assertEquals(Interval.entries, all.map { it.item })
        assertEquals(AnswerAccuracy(Interval.MAJOR_THIRD, attempts = 3, correct = 2), all.single { it.item == Interval.MAJOR_THIRD })
        assertEquals(AnswerAccuracy(Interval.OCTAVE, attempts = 1, correct = 1), all.single { it.item == Interval.OCTAVE })
        assertNull(all.single { it.item == Interval.TRITONE }.accuracy)

        val harmonic = history.intervalAccuracy(IntervalStyle.HARMONIC).first()
        assertEquals(AnswerAccuracy(Interval.MAJOR_THIRD, attempts = 2, correct = 1), harmonic.single { it.item == Interval.MAJOR_THIRD })
        assertEquals(0, harmonic.single { it.item == Interval.OCTAVE }.attempts)
    }

    @Test
    fun chordAccuracyIsSplitByWhatWasAsked() = runBlocking {
        insertChords(
            chord("c", 1_000, ChordQuality.MINOR, ChordInversion.FIRST, ChordTarget.QUALITY, correct = true),
            chord("c", 2_000, ChordQuality.MINOR, ChordInversion.ROOT_POSITION, ChordTarget.QUALITY, correct = false),
            chord("c", 3_000, ChordQuality.DOMINANT_SEVENTH, ChordInversion.THIRD, ChordTarget.INVERSION, correct = true),
        )
        val history = RoomPracticeHistory(database, unusedScope)

        val qualities = history.chordAccuracy(ChordTarget.QUALITY).first()
        assertEquals(ChordQuality.entries.map { ChordAnswer.Quality(it) }, qualities.map { it.item })
        assertEquals(2, qualities.single { it.item == ChordAnswer.Quality(ChordQuality.MINOR) }.attempts)
        // The inversion question is not counted towards its chord's quality.
        assertEquals(0, qualities.single { it.item == ChordAnswer.Quality(ChordQuality.DOMINANT_SEVENTH) }.attempts)

        val inversions = history.chordAccuracy(ChordTarget.INVERSION).first()
        assertEquals(ChordInversion.entries.map { ChordAnswer.Inversion(it) }, inversions.map { it.item })
        assertEquals(1, inversions.single { it.item == ChordAnswer.Inversion(ChordInversion.THIRD) }.correct)
        assertEquals(0, inversions.single { it.item == ChordAnswer.Inversion(ChordInversion.FIRST) }.attempts)
    }

    @Test
    fun scaleDegreeAccuracyCoversEveryDegreeAndFiltersByMode() = runBlocking {
        val major = Key(Pitch(60), Mode.MAJOR)
        val minor = Key(Pitch(57), Mode.MINOR)
        runBlocking {
            listOf(
                ScaleDegreeAttempt("d", 1_000, major, ScaleDegree.FIFTH, KeyContext.CADENCE, ScaleDegree.FIFTH, true, 1, 800),
                ScaleDegreeAttempt("d", 2_000, minor, ScaleDegree.FIFTH, KeyContext.CADENCE, ScaleDegree.FOURTH, false, 2, 1_800),
            ).forEach { database.scaleDegreeAttemptDao().insert(it.toEntity()) }
        }
        val history = RoomPracticeHistory(database, unusedScope)

        val all = history.scaleDegreeAccuracy(mode = null).first()
        assertEquals(ScaleDegree.entries, all.map { it.item })
        assertEquals(AnswerAccuracy(ScaleDegree.FIFTH, attempts = 2, correct = 1), all.single { it.item == ScaleDegree.FIFTH })
        assertEquals(AnswerAccuracy(ScaleDegree.FIFTH, attempts = 1, correct = 0), history.scaleDegreeAccuracy(Mode.MINOR).first().single { it.item == ScaleDegree.FIFTH })
        assertEquals(listOf(SessionSummary("d", ExerciseKind.SCALE_DEGREES, 1_000, 2, 1)), history.recentSessions(10).first())
    }

    @Test
    fun progressionAccuracyIsScoredPerChordInTheRequestedMode() = runBlocking {
        fun progression(mode: Mode, vararg steps: Int) = ChordProgression(steps.map { DiatonicChord(mode, it) })
        val major = Key(Pitch(60), Mode.MAJOR)
        val minor = Key(Pitch(57), Mode.MINOR)
        runBlocking {
            listOf(
                ProgressionAttempt("p", 1_000, major, progression(Mode.MAJOR, 1, 6, 4, 5), progression(Mode.MAJOR, 1, 6, 2, 5), false, 1, 5_000),
                ProgressionAttempt("q", 2_000, minor, progression(Mode.MINOR, 1, 4, 5), progression(Mode.MINOR, 1, 4, 5), true, 2, 3_000),
            ).forEach { database.progressionAttemptDao().insert(it.toEntity()) }
        }
        val history = RoomPracticeHistory(database, unusedScope)

        val majorChords = history.progressionAccuracy(Mode.MAJOR).first()
        assertEquals(DiatonicChord.all(Mode.MAJOR), majorChords.map { it.item })
        assertEquals(AnswerAccuracy(DiatonicChord(Mode.MAJOR, 4), attempts = 1, correct = 0), majorChords.single { it.item.step == 4 })
        assertEquals(AnswerAccuracy(DiatonicChord(Mode.MAJOR, 6), attempts = 1, correct = 1), majorChords.single { it.item.step == 6 })
        assertEquals(0, majorChords.single { it.item.step == 1 }.attempts)

        val minorChords = history.progressionAccuracy(Mode.MINOR).first()
        assertEquals(2, minorChords.sumOf { it.attempts })
        assertEquals(listOf("q" to ExerciseKind.PROGRESSIONS, "p" to ExerciseKind.PROGRESSIONS), history.recentSessions(10).first().map { it.sessionId to it.exercise })
    }

    @Test
    fun recentSessionsCombineExercisesNewestFirstAndAreLimited() = runBlocking {
        insertAll(
            interval("old", 1_000, Interval.MINOR_SECOND, IntervalStyle.MELODIC_ASCENDING, correct = true),
            interval("old", 1_500, Interval.MINOR_SECOND, IntervalStyle.MELODIC_ASCENDING, correct = false),
            interval("new", 9_000, Interval.OCTAVE, IntervalStyle.HARMONIC, correct = true),
        )
        insertChords(chord("mid", 5_000, ChordQuality.MAJOR, ChordInversion.ROOT_POSITION, ChordTarget.QUALITY, correct = true))
        val history = RoomPracticeHistory(database, unusedScope)

        assertEquals(
            listOf(
                SessionSummary("new", ExerciseKind.INTERVALS, 9_000, 1, 1),
                SessionSummary("mid", ExerciseKind.CHORDS, 5_000, 1, 1),
            ),
            history.recentSessions(limit = 2).first(),
        )
        assertEquals(SessionSummary("old", ExerciseKind.INTERVALS, 1_000, 2, 1), history.recentSessions(limit = 10).first().last())
    }

    @Test
    fun recentResultsAreNewestFirstLimitedAndScoredByWhatWasAsked() = runBlocking {
        insertAll(
            interval("s", 1_000, Interval.MAJOR_THIRD, IntervalStyle.HARMONIC, correct = true),
            interval("s", 2_000, Interval.OCTAVE, IntervalStyle.HARMONIC, correct = false),
            interval("s", 3_000, Interval.TRITONE, IntervalStyle.HARMONIC, correct = true),
        )
        insertChords(
            chord("c", 1_000, ChordQuality.MINOR, ChordInversion.FIRST, ChordTarget.QUALITY, correct = false),
            chord("c", 2_000, ChordQuality.MAJOR, ChordInversion.SECOND, ChordTarget.INVERSION, correct = true),
        )
        runBlocking {
            val key = Key(Pitch(60), Mode.MAJOR)
            fun progression(vararg steps: Int) = ChordProgression(steps.map { DiatonicChord(Mode.MAJOR, it) })
            database.progressionAttemptDao().insert(
                ProgressionAttempt("p", 1_000, key, progression(1, 6, 4, 5), progression(1, 6, 2, 5), false, 1, 5_000).toEntity(),
            )
        }
        val history = RoomPracticeHistory(database, unusedScope)

        assertEquals(
            listOf(ItemResult(Interval.TRITONE, true), ItemResult(Interval.OCTAVE, false)),
            history.recentIntervalResults(limit = 2).first(),
        )
        assertEquals(
            listOf(
                ItemResult<ChordAnswer>(ChordAnswer.Inversion(ChordInversion.SECOND), true),
                ItemResult<ChordAnswer>(ChordAnswer.Quality(ChordQuality.MINOR), false),
            ),
            history.recentChordResults(limit = 10).first(),
        )
        assertEquals(
            listOf(
                ItemResult(DiatonicChord(Mode.MAJOR, 6), true),
                ItemResult(DiatonicChord(Mode.MAJOR, 4), false),
                ItemResult(DiatonicChord(Mode.MAJOR, 5), true),
            ),
            history.recentProgressionResults(limit = 10).first(),
        )
    }

    @Test
    fun dictationIsScoredPerNoteInTheRequestedMode() = runBlocking {
        fun melody(vararg degrees: ScaleDegree) = Melody(degrees.toList())
        runBlocking {
            database.dictationAttemptDao().insert(
                DictationAttempt(
                    "d", 1_000, Key(Pitch(60), Mode.MAJOR),
                    melody(ScaleDegree.TONIC, ScaleDegree.SECOND, ScaleDegree.THIRD),
                    melody(ScaleDegree.TONIC, ScaleDegree.SECOND, ScaleDegree.FOURTH),
                    false, 1, 6_000,
                ).toEntity(),
            )
        }
        val history = RoomPracticeHistory(database, unusedScope)

        val major = history.dictationAccuracy(Mode.MAJOR).first()
        assertEquals(Mode.MAJOR.diatonicDegrees, major.map { it.item })
        assertEquals(AnswerAccuracy(ScaleDegree.SECOND, attempts = 1, correct = 1), major.single { it.item == ScaleDegree.SECOND })
        assertEquals(AnswerAccuracy(ScaleDegree.THIRD, attempts = 1, correct = 0), major.single { it.item == ScaleDegree.THIRD })
        assertEquals(0, history.dictationAccuracy(Mode.MINOR).first().sumOf { it.attempts })
        assertEquals(
            listOf(ItemResult(ScaleDegree.SECOND, true), ItemResult(ScaleDegree.THIRD, false)),
            history.recentDictationResults(10).first(),
        )
        assertEquals(ExerciseKind.DICTATION, history.recentSessions(10).first().single().exercise)
    }

    @Test
    fun recordWritesThroughTheWriteScope() = runBlocking {
        val history = RoomPracticeHistory(database, writeScope = this)
        history.record(interval("s", 1_000, Interval.MAJOR_SIXTH, IntervalStyle.MELODIC_DESCENDING, correct = false))
        history.record(chord("t", 2_000, ChordQuality.AUGMENTED, ChordInversion.SECOND, ChordTarget.INVERSION, correct = true))
        // Wait for the background writes launched in this scope.
        coroutineContext[Job]!!.children.forEach { it.join() }

        val sixth = history.intervalAccuracy(IntervalStyle.MELODIC_DESCENDING).first().single { it.item == Interval.MAJOR_SIXTH }
        assertEquals(AnswerAccuracy(Interval.MAJOR_SIXTH, attempts = 1, correct = 0), sixth)
        assertEquals(2, history.recentSessions(10).first().size)
    }
}
