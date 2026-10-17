package com.example.eartrainer.data.history

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.eartrainer.exercise.IntervalAttempt
import com.example.eartrainer.exercise.IntervalStyle
import com.example.eartrainer.music.Interval
import com.example.eartrainer.music.Pitch
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
    private lateinit var dao: IntervalAttemptDao

    // Queries never use the write scope; only record() does.
    private val unusedScope = CoroutineScope(SupervisorJob())

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EarTrainerDatabase::class.java,
        ).build()
        dao = database.intervalAttemptDao()
    }

    @After
    fun tearDown() = database.close()

    private fun attempt(session: String, at: Long, interval: Interval, style: IntervalStyle, correct: Boolean) =
        IntervalAttempt(session, at, interval, style, Pitch(60), if (correct) interval else Interval.TRITONE, correct, 2, 1_500)

    private fun insertAll(vararg attempts: IntervalAttempt) = runBlocking {
        attempts.forEach { dao.insert(it.toEntity()) }
    }

    @Test
    fun accuracyCoversEveryIntervalAndFiltersByStyle() = runBlocking {
        insertAll(
            attempt("s1", 1_000, Interval.MAJOR_THIRD, IntervalStyle.MELODIC_ASCENDING, correct = true),
            attempt("s1", 2_000, Interval.MAJOR_THIRD, IntervalStyle.HARMONIC, correct = false),
            attempt("s1", 3_000, Interval.MAJOR_THIRD, IntervalStyle.HARMONIC, correct = true),
            attempt("s2", 4_000, Interval.OCTAVE, IntervalStyle.MELODIC_ASCENDING, correct = true),
        )
        val history = RoomPracticeHistory(dao, writeScope = unusedScope)

        val all = history.intervalAccuracy(style = null).first()
        assertEquals(Interval.entries, all.map { it.interval })
        assertEquals(IntervalAccuracy(Interval.MAJOR_THIRD, attempts = 3, correct = 2), all.single { it.interval == Interval.MAJOR_THIRD })
        assertEquals(IntervalAccuracy(Interval.OCTAVE, attempts = 1, correct = 1), all.single { it.interval == Interval.OCTAVE })
        assertNull(all.single { it.interval == Interval.TRITONE }.accuracy)

        val harmonic = history.intervalAccuracy(IntervalStyle.HARMONIC).first()
        assertEquals(IntervalAccuracy(Interval.MAJOR_THIRD, attempts = 2, correct = 1), harmonic.single { it.interval == Interval.MAJOR_THIRD })
        assertEquals(0, harmonic.single { it.interval == Interval.OCTAVE }.attempts)
    }

    @Test
    fun recentSessionsAreGroupedNewestFirstAndLimited() = runBlocking {
        insertAll(
            attempt("old", 1_000, Interval.MINOR_SECOND, IntervalStyle.MELODIC_ASCENDING, correct = true),
            attempt("old", 1_500, Interval.MINOR_SECOND, IntervalStyle.MELODIC_ASCENDING, correct = false),
            attempt("mid", 5_000, Interval.PERFECT_FIFTH, IntervalStyle.MELODIC_ASCENDING, correct = true),
            attempt("new", 9_000, Interval.OCTAVE, IntervalStyle.HARMONIC, correct = true),
        )
        val history = RoomPracticeHistory(dao, writeScope = unusedScope)

        assertEquals(
            listOf(SessionSummary("new", 9_000, 1, 1), SessionSummary("mid", 5_000, 1, 1)),
            history.recentSessions(limit = 2).first(),
        )
        assertEquals(SessionSummary("old", 1_000, 2, 1), history.recentSessions(limit = 10).first().last())
    }

    @Test
    fun recordWritesThroughTheWriteScope() = runBlocking {
        val history = RoomPracticeHistory(dao, writeScope = this)
        history.record(attempt("s", 1_000, Interval.MAJOR_SIXTH, IntervalStyle.MELODIC_DESCENDING, correct = false))
        // Wait for the background write launched in this scope.
        coroutineContext[Job]!!.children.forEach { it.join() }

        val sixth = history.intervalAccuracy(IntervalStyle.MELODIC_DESCENDING).first().single { it.interval == Interval.MAJOR_SIXTH }
        assertEquals(IntervalAccuracy(Interval.MAJOR_SIXTH, attempts = 1, correct = 0), sixth)
    }
}
