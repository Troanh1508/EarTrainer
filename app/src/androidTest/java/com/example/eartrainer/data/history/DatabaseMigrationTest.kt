package com.example.eartrainer.data.history

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.eartrainer.exercise.ExerciseKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Upgrading must keep existing practice history; checked against the exported schemas. */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val databaseName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), EarTrainerDatabase::class.java)

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun insertIntervalRow(sessionId: String) = """
        INSERT INTO interval_attempts
            (session_id, answered_at, interval, style, lower_midi, answer, is_correct, play_count, response_ms)
        VALUES ('$sessionId', 1000, 'PERFECT_FIFTH', 'HARMONIC', 55, 'PERFECT_FIFTH', 1, 2, 1800)
    """

    /** Opens the migrated file with the real database class and returns every session in it. */
    private fun sessionsAfterUpgrade(): List<SessionSummaryRow> {
        val database = Room.databaseBuilder(context, EarTrainerDatabase::class.java, databaseName).build()
        try {
            return runBlocking { database.sessionDao().recentSessions(10).first() }
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun version1UpgradesThroughEveryVersionAndKeepsIntervalHistory() {
        helper.createDatabase(databaseName, 1).apply {
            execSQL(insertIntervalRow("from-v1"))
            close()
        }
        // Applies each auto-migration in turn and checks the result matches each exported schema.
        helper.runMigrationsAndValidate(databaseName, 2, true).close()
        helper.runMigrationsAndValidate(databaseName, 3, true).close()

        assertEquals(listOf(SessionSummaryRow("from-v1", ExerciseKind.INTERVALS.name, 1_000, 1, 1)), sessionsAfterUpgrade())
    }

    @Test
    fun version2To3KeepsChordHistory() {
        helper.createDatabase(databaseName, 2).apply {
            execSQL(
                """
                INSERT INTO chord_attempts
                    (session_id, answered_at, quality, inversion, style, target, bass_midi, answer, is_correct, play_count, response_ms)
                VALUES ('from-v2', 2000, 'MINOR', 'FIRST', 'BLOCK', 'QUALITY', 52, 'MAJOR', 0, 1, 2500)
                """,
            )
            close()
        }
        helper.runMigrationsAndValidate(databaseName, 3, true).close()

        assertEquals(listOf(SessionSummaryRow("from-v2", ExerciseKind.CHORDS.name, 2_000, 1, 0)), sessionsAfterUpgrade())
    }
}
