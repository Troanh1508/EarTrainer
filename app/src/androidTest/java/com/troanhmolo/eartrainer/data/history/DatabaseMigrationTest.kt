package com.troanhmolo.eartrainer.data.history

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.troanhmolo.eartrainer.exercise.ExerciseKind
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
        helper.runMigrationsAndValidate(databaseName, 4, true).close()
        helper.runMigrationsAndValidate(databaseName, 5, true).close()
        helper.runMigrationsAndValidate(databaseName, 6, true).close()

        assertEquals(listOf(SessionSummaryRow("from-v1", ExerciseKind.INTERVALS.name, 1_000, 1, 1)), sessionsAfterUpgrade())
    }

    @Test
    fun version5To6KeepsDictationHistory() {
        helper.createDatabase(databaseName, 5).apply {
            execSQL(
                """
                INSERT INTO dictation_attempts
                    (session_id, answered_at, tonic_midi, mode, melody, answer, is_correct, play_count, response_ms)
                VALUES ('from-v5', 5000, 62, 'MINOR', 'TONIC,SECOND,THIRD', 'TONIC,SECOND,THIRD', 1, 2, 7000)
                """,
            )
            close()
        }
        helper.runMigrationsAndValidate(databaseName, 6, true).close()

        assertEquals(listOf(SessionSummaryRow("from-v5", ExerciseKind.DICTATION.name, 5_000, 1, 1)), sessionsAfterUpgrade())
    }

    @Test
    fun version4UpgradesToLatestAndKeepsProgressionHistory() {
        helper.createDatabase(databaseName, 4).apply {
            execSQL(
                """
                INSERT INTO progression_attempts
                    (session_id, answered_at, tonic_midi, mode, progression, answer, is_correct, play_count, response_ms)
                VALUES ('from-v4', 4000, 60, 'MAJOR', '1,6,4,5', '1,6,4,5', 1, 1, 3000)
                """,
            )
            close()
        }
        helper.runMigrationsAndValidate(databaseName, 5, true).close()
        helper.runMigrationsAndValidate(databaseName, 6, true).close()

        assertEquals(listOf(SessionSummaryRow("from-v4", ExerciseKind.PROGRESSIONS.name, 4_000, 1, 1)), sessionsAfterUpgrade())
    }

    @Test
    fun version3UpgradesToLatestAndKeepsScaleDegreeHistory() {
        helper.createDatabase(databaseName, 3).apply {
            execSQL(
                """
                INSERT INTO scale_degree_attempts
                    (session_id, answered_at, tonic_midi, mode, degree, context, answer, is_correct, play_count, response_ms)
                VALUES ('from-v3', 3000, 60, 'MAJOR', 'THIRD', 'CADENCE', 'THIRD', 1, 1, 1200)
                """,
            )
            close()
        }
        helper.runMigrationsAndValidate(databaseName, 4, true).close()
        helper.runMigrationsAndValidate(databaseName, 5, true).close()
        helper.runMigrationsAndValidate(databaseName, 6, true).close()

        assertEquals(listOf(SessionSummaryRow("from-v3", ExerciseKind.SCALE_DEGREES.name, 3_000, 1, 1)), sessionsAfterUpgrade())
    }

    @Test
    fun version2UpgradesToLatestAndKeepsChordHistory() {
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
        helper.runMigrationsAndValidate(databaseName, 4, true).close()
        helper.runMigrationsAndValidate(databaseName, 5, true).close()
        helper.runMigrationsAndValidate(databaseName, 6, true).close()

        assertEquals(listOf(SessionSummaryRow("from-v2", ExerciseKind.CHORDS.name, 2_000, 1, 0)), sessionsAfterUpgrade())
    }
}
