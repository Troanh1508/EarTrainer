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

    @Test
    fun version1To2KeepsIntervalHistoryAndAddsChords() {
        helper.createDatabase(databaseName, 1).apply {
            execSQL(
                """
                INSERT INTO interval_attempts
                    (session_id, answered_at, interval, style, lower_midi, answer, is_correct, play_count, response_ms)
                VALUES ('before-upgrade', 1000, 'PERFECT_FIFTH', 'HARMONIC', 55, 'PERFECT_FIFTH', 1, 2, 1800)
                """,
            )
            close()
        }

        // Applies the auto-migration and checks the result matches the version 2 schema exactly.
        helper.runMigrationsAndValidate(databaseName, 2, true).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, EarTrainerDatabase::class.java, databaseName).build()
        try {
            val sessions = runBlocking { database.sessionDao().recentSessions(10).first() }
            assertEquals(listOf(SessionSummaryRow("before-upgrade", ExerciseKind.INTERVALS.name, 1_000, 1, 1)), sessions)
            assertEquals(emptyList<AccuracyRow>(), runBlocking { database.chordAttemptDao().accuracyByQuality().first() })
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }
}
