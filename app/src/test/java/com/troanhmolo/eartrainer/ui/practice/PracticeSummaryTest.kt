package com.troanhmolo.eartrainer.ui.practice

import com.troanhmolo.eartrainer.data.history.SessionSummary
import com.troanhmolo.eartrainer.exercise.ExerciseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PracticeSummaryTest {

    // Questions are strings of items, e.g. "ab" asks a then b; an upper-case letter in the answer is a miss.
    private fun answered(question: String, answer: String, playCount: Int = 1, firstPlayedAt: Long = 0, answeredAt: Long = 0) =
        AnsweredQuestion(question, answer, question == answer, playCount, firstPlayedAt, answeredAt)

    private val itemsOf: (String, String) -> List<SummaryItem> = { question, answer ->
        question.zip(answer).map { (asked, given) -> SummaryItem(asked.toString(), given.toString(), asked == given) }
    }

    @Test
    fun countsItemsAndWholeAnswersSeparately() {
        val summary = PracticeSummary.from(listOf(answered("ab", "ab"), answered("ab", "aB"), answered("cc", "cc")), itemsOf)
        assertEquals(3, summary.questions)
        assertEquals(2, summary.questionsRight)
        assertEquals(6, summary.items)
        assertEquals(5, summary.itemsRight)
        assertEquals(83, summary.percent)
        assertEquals(1, summary.missedQuestions)
    }

    @Test
    fun talliesPutTheWeakestFirstAndMistakesTheMostFrequent() {
        val summary = PracticeSummary.from(
            listOf(answered("ab", "aB"), answered("cb", "cB"), answered("ab", "ab"), answered("ca", "cX")),
            itemsOf,
        )
        assertEquals(
            listOf(ItemTally("b", right = 1, missed = 2), ItemTally("a", right = 2, missed = 1), ItemTally("c", right = 2, missed = 0)),
            summary.tallies,
        )
        assertEquals(listOf(SessionMistake("b", "B", 2), SessionMistake("a", "X", 1)), summary.mistakes)
    }

    @Test
    fun timeRunsFromTheFirstPlayToTheLastAnswer() {
        val summary = PracticeSummary.from(
            listOf(
                answered("a", "a", playCount = 1, firstPlayedAt = 1_000, answeredAt = 3_000),
                answered("a", "a", playCount = 3, firstPlayedAt = 4_000, answeredAt = 8_000),
            ),
            itemsOf,
        )
        assertEquals(7_000, summary.durationMillis)
        assertEquals(3_000, summary.averageResponseMillis)
        assertEquals(2, summary.replays)
    }

    @Test
    fun theAverageLeavesOutThisSessionAndWeighsByItems() {
        val summary = PracticeSummary.from(listOf(answered("ab", "ab"), answered("ab", "aB")), itemsOf)
        assertNull(summary.pointsAboveAverage)
        val sessions = listOf(
            SessionSummary("this", ExerciseKind.RHYTHM, 0, attempts = 2, correct = 1, items = 4, itemsCorrect = 3),
            SessionSummary("a", ExerciseKind.RHYTHM, 0, attempts = 1, correct = 0, items = 2, itemsCorrect = 0),
            SessionSummary("b", ExerciseKind.RHYTHM, 0, attempts = 4, correct = 4, items = 8, itemsCorrect = 8),
        )
        val compared = summary.comparedWith(sessions, currentSessionId = "this")
        assertEquals(0.8f, compared.pastAccuracy)
        // 75% this session against 80%.
        assertEquals(-5, compared.pointsAboveAverage)
        assertNull(summary.comparedWith(sessions.take(1), currentSessionId = "this").pastAccuracy)
    }
}
