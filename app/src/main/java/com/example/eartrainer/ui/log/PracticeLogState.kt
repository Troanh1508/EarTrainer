package com.example.eartrainer.ui.log

import com.example.eartrainer.data.history.DayCount
import com.example.eartrainer.data.history.SessionSummary
import java.util.TimeZone

private const val DAY_MILLIS = 86_400_000L

/**
 * The local calendar day of [millis], in days since 1970-01-01: the same days that
 * [com.example.eartrainer.data.history.PracticeHistory.answersPerDay] counts answers in.
 */
fun localEpochDay(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): Long =
    Math.floorDiv(millis + timeZone.getOffset(millis), DAY_MILLIS)

/** Weekday of an epoch day, 0 for Monday to 6 for Sunday. 1970-01-01 was a Thursday. */
fun weekdayOf(day: Long): Int = Math.floorMod(day + 3, 7).toInt()

data class CalendarDay(val day: Long, val answers: Int, val isToday: Boolean, val isFuture: Boolean) {
    /** 0 without practice, then 1 to 4 by how much. */
    val level: Int
        get() = when {
            answers <= 0 -> 0
            answers <= 10 -> 1
            answers <= 25 -> 2
            answers <= 50 -> 3
            else -> 4
        }
}

data class PracticeLogUiState(
    /** Consecutive days with practice up to today, or up to yesterday while today has none yet. */
    val currentStreak: Int,
    val longestStreak: Int,
    /** [WEEKS] whole weeks, Monday first, ending with the current week. */
    val calendar: List<CalendarDay>,
    /** The current week, Monday to Sunday. */
    val week: List<CalendarDay>,
    /** Most recent first. */
    val sessions: List<SessionSummary>,
) {
    val weekAnswers: Int get() = week.sumOf { it.answers }
    val practicedDays: Int get() = calendar.count { it.answers > 0 }
    val hasHistory: Boolean get() = sessions.isNotEmpty()

    companion object {
        const val WEEKS = 5

        fun from(days: List<DayCount>, sessions: List<SessionSummary>, today: Long): PracticeLogUiState {
            val answersOn = days.associate { it.day to it.answers }
            val practiced = answersOn.filterValues { it > 0 }.keys
            // A streak is not broken until a whole day passes without practice.
            var current = 0
            var day = if (today in practiced) today else today - 1
            while (day in practiced) {
                current++
                day--
            }
            var longest = 0
            var run = 0
            var previous: Long? = null
            for (practicedDay in practiced.sorted()) {
                run = if (previous == practicedDay - 1) run + 1 else 1
                longest = maxOf(longest, run)
                previous = practicedDay
            }
            val weekStart = today - weekdayOf(today)
            val calendarStart = weekStart - 7L * (WEEKS - 1)
            val calendar = (calendarStart until weekStart + 7).map { calendarDay ->
                CalendarDay(calendarDay, answersOn[calendarDay] ?: 0, isToday = calendarDay == today, isFuture = calendarDay > today)
            }
            return PracticeLogUiState(
                currentStreak = current,
                longestStreak = longest,
                calendar = calendar,
                week = calendar.takeLast(7),
                sessions = sessions,
            )
        }
    }
}
