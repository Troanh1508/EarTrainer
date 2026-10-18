package com.example.eartrainer.data.history

import com.example.eartrainer.exercise.ExerciseKind
import com.example.eartrainer.exercise.ItemResult
import com.example.eartrainer.music.Mode
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionScoringTest {

    private fun row(session: String, at: Long, asked: String, chosen: String, mode: String? = "MAJOR") =
        SessionAnswerRow(session, at, mode, asked, chosen, isCorrect = asked == chosen)

    private fun progressionItems(row: SessionAnswerRow): List<ItemResult<*>> =
        progressionChordResults(Mode.valueOf(row.mode!!), row.asked.split(",").map(String::toInt), row.chosen.split(",").map(String::toInt))

    @Test
    fun answersAreGroupedIntoSessionsNewestFirstAndScoredItemByItem() {
        val rows = listOf(
            row("a", 1_000, "1,6,4,5", "1,6,2,5"),
            row("b", 5_000, "1,4,5,1", "1,4,5,1"),
            row("a", 2_000, "1,4,5", "1,4,5"),
        )
        assertEquals(
            listOf(
                SessionSummary("b", ExerciseKind.PROGRESSIONS, 5_000, attempts = 1, correct = 1, items = 3, itemsCorrect = 3),
                SessionSummary("a", ExerciseKind.PROGRESSIONS, 1_000, attempts = 2, correct = 1, items = 5, itemsCorrect = 4),
            ),
            rows.toSessions(ExerciseKind.PROGRESSIONS, ::progressionItems),
        )
    }

    @Test
    fun sessionsWithNothingToScoreAreLeftOut() {
        // A lone tonic has no chords after it to score.
        assertEquals(emptyList<SessionSummary>(), listOf(row("a", 1_000, "1", "1")).toSessions(ExerciseKind.PROGRESSIONS, ::progressionItems))
    }
}
