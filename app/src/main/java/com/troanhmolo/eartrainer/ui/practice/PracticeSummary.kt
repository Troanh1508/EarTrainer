package com.troanhmolo.eartrainer.ui.practice

import com.troanhmolo.eartrainer.data.history.SessionSummary
import com.troanhmolo.eartrainer.exercise.ExerciseQuestion
import kotlin.math.roundToInt

/** One answered question of the current session, kept for the session strip and the summary. */
data class AnsweredQuestion<A, Q>(
    val question: Q,
    val answer: A,
    val isCorrect: Boolean,
    /** Times the prompt was started before answering; 1 means no replays. */
    val playCount: Int,
    val firstPlayedAtMillis: Long,
    val answeredAtMillis: Long,
) {
    val responseMillis: Long get() = (answeredAtMillis - firstPlayedAtMillis).coerceAtLeast(0)
}

/**
 * One scored part of an answer: the whole answer for most exercises, or one chord, note or
 * rhythm figure where an exercise is scored item by item. [chosenLabel] is what was given in its
 * place, or null when nothing matches it (e.g. a beat split differently).
 */
data class SummaryItem(val label: String, val chosenLabel: String?, val isCorrect: Boolean)

/** How often an item came up this session and how often it was missed. */
data class ItemTally(val label: String, val right: Int, val missed: Int) {
    val total: Int get() = right + missed
}

/** A wrong answer given for [label], [count] times this session. */
data class SessionMistake(val label: String, val chosenLabel: String?, val count: Int)

/** The end-of-session results. */
data class PracticeSummary(
    val questions: Int,
    val questionsRight: Int,
    /** Scored items: equal to [questions] unless the exercise is scored item by item. */
    val items: Int,
    val itemsRight: Int,
    /** From first hearing the first question to answering the last. */
    val durationMillis: Long,
    val averageResponseMillis: Long,
    /** Plays beyond the first of each question. */
    val replays: Int,
    /** Weakest first. */
    val tallies: List<ItemTally>,
    /** Most frequent first. */
    val mistakes: List<SessionMistake>,
    /** Item accuracy over recent earlier sessions of this exercise, or null if there are none. */
    val pastAccuracy: Float? = null,
) {
    val accuracy: Float get() = if (items == 0) 0f else itemsRight.toFloat() / items
    val percent: Int get() = (accuracy * 100).roundToInt()
    val missedQuestions: Int get() = questions - questionsRight

    /** Percentage points above (positive) or below the recent average, or null without one. */
    val pointsAboveAverage: Int? get() = pastAccuracy?.let { percent - (it * 100).roundToInt() }

    /** The same summary compared with [sessions], leaving out [currentSessionId]. */
    fun comparedWith(sessions: List<SessionSummary>, currentSessionId: String): PracticeSummary {
        val earlier = sessions.filter { it.sessionId != currentSessionId && it.items > 0 }
        val items = earlier.sumOf { it.items }
        return copy(pastAccuracy = if (items == 0) null else earlier.sumOf { it.itemsCorrect }.toFloat() / items)
    }

    companion object {
        /** Sessions the "recent average" is taken over. */
        const val AVERAGE_OVER_SESSIONS = 20

        fun <A, Q> from(answered: List<AnsweredQuestion<A, Q>>, itemsOf: (Q, A) -> List<SummaryItem>): PracticeSummary {
            val items = answered.flatMap { itemsOf(it.question, it.answer) }
            return PracticeSummary(
                questions = answered.size,
                questionsRight = answered.count { it.isCorrect },
                items = items.size,
                itemsRight = items.count { it.isCorrect },
                durationMillis = if (answered.isEmpty()) 0 else answered.last().answeredAtMillis - answered.first().firstPlayedAtMillis,
                averageResponseMillis = if (answered.isEmpty()) 0 else answered.sumOf { it.responseMillis } / answered.size,
                replays = answered.sumOf { (it.playCount - 1).coerceAtLeast(0) },
                tallies = items.groupBy { it.label }
                    .map { (label, results) -> ItemTally(label, right = results.count { it.isCorrect }, missed = results.count { !it.isCorrect }) }
                    .sortedWith(compareBy<ItemTally> { it.right.toFloat() / it.total }.thenByDescending { it.total }),
                mistakes = items.filter { !it.isCorrect }
                    .groupingBy { it.label to it.chosenLabel }.eachCount()
                    .map { (pair, count) -> SessionMistake(pair.first, pair.second, count) }
                    .sortedByDescending { it.count },
            )
        }
    }
}

/** Scores each answer as one item, named by [labelOf], for exercises not scored item by item. */
fun <A, Q : ExerciseQuestion<A>> wholeAnswerItems(labelOf: (A) -> String): (Q, A) -> List<SummaryItem> =
    { question, answer -> listOf(SummaryItem(labelOf(question.correctAnswer), labelOf(answer), question.isCorrect(answer))) }
