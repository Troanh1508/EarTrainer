package com.troanhmolo.eartrainer.ui.log

import com.troanhmolo.eartrainer.data.history.DayCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class PracticeLogStateTest {

    // 2026-10-18 was a Sunday: days since 1970-01-01.
    private val sunday = 20_744L
    private val wednesday = sunday - 4

    private fun practiced(vararg days: Long, answers: Int = 5) = days.map { DayCount(it, answers) }

    @Test
    fun daysFollowTheLocalCalendar() {
        val utc = TimeZone.getTimeZone("UTC")
        val tokyo = TimeZone.getTimeZone("Asia/Tokyo")
        // 2026-10-18 20:00 UTC is already Monday the 19th in Tokyo.
        val evening = sunday * 86_400_000L + 20 * 3_600_000L
        assertEquals(sunday, localEpochDay(evening, utc))
        assertEquals(sunday + 1, localEpochDay(evening, tokyo))
        assertEquals(6, weekdayOf(sunday))
        assertEquals(0, weekdayOf(sunday - 6))
        // 1970-01-01 was a Thursday.
        assertEquals(3, weekdayOf(0))
    }

    @Test
    fun theCurrentStreakCountsBackFromTodayOrFromYesterdayBeforeTodaysPractice() {
        val history = practiced(wednesday - 9, wednesday - 2, wednesday - 1)
        assertEquals(2, PracticeLogUiState.from(history, emptyList(), today = wednesday).currentStreak)
        assertEquals(3, PracticeLogUiState.from(history + practiced(wednesday), emptyList(), today = wednesday).currentStreak)
        // A whole day without practice ends the streak.
        assertEquals(0, PracticeLogUiState.from(history, emptyList(), today = wednesday + 1).currentStreak)
    }

    @Test
    fun theLongestStreakIsTheLongestRunOfPracticeDays() {
        val history = practiced(1, 2, 3, 4, 10, 11, 20) + DayCount(5, 0)
        assertEquals(4, PracticeLogUiState.from(history, emptyList(), today = 30).longestStreak)
        assertEquals(0, PracticeLogUiState.from(emptyList(), emptyList(), today = 30).longestStreak)
    }

    @Test
    fun theCalendarHoldsWholeWeeksFromMondayEndingWithThisWeek() {
        val state = PracticeLogUiState.from(practiced(wednesday, wednesday - 7, answers = 30), emptyList(), today = wednesday)
        assertEquals(PracticeLogUiState.WEEKS * 7, state.calendar.size)
        assertTrue(state.calendar.chunked(7).all { week -> weekdayOf(week.first().day) == 0 })
        assertEquals(sunday, state.calendar.last().day)
        assertEquals(listOf(wednesday), state.calendar.filter { it.isToday }.map { it.day })
        assertEquals((wednesday + 1..sunday).toList(), state.calendar.filter { it.isFuture }.map { it.day })
        assertEquals(state.calendar.takeLast(7), state.week)
        assertEquals(30, state.weekAnswers)
        assertEquals(2, state.practicedDays)
    }

    @Test
    fun levelsGrowWithTheNumberOfAnswers() {
        assertEquals(listOf(0, 1, 1, 2, 3, 4), listOf(0, 1, 10, 11, 50, 51).map { CalendarDay(0, it, isToday = false, isFuture = false).level })
    }
}
