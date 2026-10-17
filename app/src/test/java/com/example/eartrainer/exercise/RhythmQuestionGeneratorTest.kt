package com.example.eartrainer.exercise

import com.example.eartrainer.music.ListeningPrompt
import com.example.eartrainer.music.Rhythm
import com.example.eartrainer.music.RhythmCell
import com.example.eartrainer.music.RhythmEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RhythmQuestionGeneratorTest {

    private fun generate(settings: RhythmSettings, count: Int = 500, seed: Int = 31, weights: ((RhythmCell) -> Double)? = null) =
        RhythmQuestionGenerator(settings, Random(seed), weights).let { generator -> List(count) { generator.generate() } }

    private val everySetting = RhythmVocabulary.entries.flatMap { vocabulary -> RhythmSettings.BAR_OPTIONS.map { RhythmSettings(vocabulary, bars = it) } }

    @Test
    fun cellsHaveTheRightLengths() {
        RhythmCell.entries.forEach { cell ->
            val expected = if (cell == RhythmCell.HALF || cell == RhythmCell.DOTTED_QUARTER_EIGHTH) 2 else 1
            assertEquals(cell.name, expected, cell.beats)
            assertEquals(cell.name, expected.toDouble(), cell.events.sumOf { it.beats }, 1e-9)
        }
    }

    @Test
    fun rhythmsMustFillBarsAndKeepTwoBeatFiguresOnStrongBeats() {
        assertThrows(IllegalArgumentException::class.java) { Rhythm(listOf(RhythmCell.QUARTER, RhythmCell.QUARTER, RhythmCell.QUARTER)) }
        assertThrows(IllegalArgumentException::class.java) {
            Rhythm(listOf(RhythmCell.QUARTER, RhythmCell.HALF, RhythmCell.QUARTER))
        }
        val rhythm = Rhythm(listOf(RhythmCell.HALF, RhythmCell.EIGHTH_REST_EIGHTH, RhythmCell.QUARTER, RhythmCell.DOTTED_QUARTER_EIGHTH, RhythmCell.SIXTEENTHS, RhythmCell.QUARTER))
        assertEquals(2, rhythm.bars)
        assertEquals(listOf(0, 2, 3, 4, 6, 7), rhythm.startBeats)
        assertEquals(
            listOf(listOf(RhythmCell.HALF, RhythmCell.EIGHTH_REST_EIGHTH, RhythmCell.QUARTER), listOf(RhythmCell.DOTTED_QUARTER_EIGHTH, RhythmCell.SIXTEENTHS, RhythmCell.QUARTER)),
            rhythm.cellsByBar,
        )
        assertEquals(listOf(0.0, 2.0, 2.5), rhythm.timeline.take(3).map { it.first })
        assertEquals(RhythmEvent(0.5, isRest = true), rhythm.timeline[1].second)
    }

    @Test
    fun generatedRhythmsFillTheBarsWithAllowedCellsStartOnANoteAndHaveTwoNotesPerBar() {
        everySetting.forEach { settings ->
            generate(settings, count = 200).forEach { question ->
                val rhythm = question.rhythm
                assertEquals(settings.bars, rhythm.bars)
                assertTrue(rhythm.cells.all { it in settings.vocabulary.cells })
                assertTrue(!rhythm.cells.first().events.first().isRest)
                rhythm.cellsByBar.forEach { bar -> assertTrue(bar.sumOf { it.noteCount } >= 2) }
            }
        }
    }

    @Test
    fun choicesAreFourDistinctRhythmsIncludingTheAnswer() {
        everySetting.forEach { settings ->
            generate(settings, count = 200).forEach { question ->
                assertEquals(RhythmSettings.CHOICE_COUNT, question.choices.size)
                assertEquals(question.choices.size, question.choices.toSet().size)
                assertTrue(question.rhythm in question.choices)
                assertTrue(question.choices.all { it.bars == settings.bars && it.cells.all { cell -> cell in settings.vocabulary.cells } })
            }
        }
    }

    /** Notes and rests of each half bar (two beats), keyed by half-bar index. */
    private fun halfBars(rhythm: Rhythm) = rhythm.timeline.groupBy { (start, _) -> (start / 2).toInt() }

    @Test
    fun wrongChoicesDifferFromTheAnswerInExactlyOneHalfBar() {
        everySetting.forEach { settings ->
            generate(settings, count = 200).forEach { question ->
                val answer = halfBars(question.rhythm)
                (question.choices - question.rhythm).forEach { wrong ->
                    val changed = halfBars(wrong).count { (half, events) -> answer[half] != events }
                    assertEquals("${question.rhythm.description} vs ${wrong.description}", 1, changed)
                }
            }
        }
    }

    @Test
    fun aSwappedCellIsPreferredWhenThereAreEnough() {
        generate(RhythmSettings(vocabulary = RhythmVocabulary.SIXTEENTHS, bars = 2)).forEach { question ->
            (question.choices - question.rhythm).forEach { wrong ->
                assertEquals(question.rhythm.cells.size, wrong.cells.size)
                assertEquals(1, wrong.cells.indices.count { wrong.cells[it] != question.rhythm.cells[it] })
            }
        }
    }

    @Test
    fun answerPositionVaries() {
        assertEquals((0 until RhythmSettings.CHOICE_COUNT).toSet(), generate(RhythmSettings()).map { it.choices.indexOf(it.rhythm) }.toSet())
    }

    @Test
    fun promptCountsInOneBarThenPlaysTheNotesOnTheirBeats() {
        val rhythm = Rhythm(listOf(RhythmCell.QUARTER, RhythmCell.EIGHTH_REST_EIGHTH, RhythmCell.HALF))
        val question = RhythmQuestion(rhythm, RhythmTempo.SLOW, listOf(rhythm))
        val prompt = question.prompt
        assertEquals(
            ListeningPrompt.BeatTiming(
                bpm = 70,
                startBeats = listOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.5, 6.0),
                lengthBeats = listOf(0.25, 0.25, 0.25, 0.25, 1.0, 0.5, 2.0),
            ),
            prompt.beatTiming,
        )
        assertEquals(List(4) { listOf(RhythmQuestion.COUNT_IN_PITCH) } + List(3) { listOf(RhythmQuestion.RHYTHM_PITCH) }, prompt.moments)
    }

    @Test
    fun weakCellsAppearMoreOften() {
        val favourRest: (RhythmCell) -> Double = { if (it == RhythmCell.QUARTER_REST) 10.0 else 0.2 }
        fun restShare(questions: List<RhythmQuestion>) =
            questions.flatMap { it.rhythm.cells }.let { cells -> cells.count { it == RhythmCell.QUARTER_REST }.toDouble() / cells.size }
        assertTrue(restShare(generate(RhythmSettings(), weights = favourRest)) > restShare(generate(RhythmSettings())) * 1.5)
    }

    @Test
    fun answersAreScoredPerCellOnTheSameBeat() {
        val played = Rhythm(listOf(RhythmCell.QUARTER, RhythmCell.EIGHTHS, RhythmCell.HALF))
        val chosen = Rhythm(listOf(RhythmCell.QUARTER, RhythmCell.QUARTER_REST, RhythmCell.HALF))
        assertEquals(
            listOf(ItemResult(RhythmCell.QUARTER, true), ItemResult(RhythmCell.EIGHTHS, false), ItemResult(RhythmCell.HALF, true)),
            rhythmCellResults(played, chosen),
        )
        // A cell only counts as right if the chosen rhythm has it starting on the same beat.
        val shifted = Rhythm(listOf(RhythmCell.EIGHTHS, RhythmCell.QUARTER, RhythmCell.HALF))
        assertEquals(listOf(false, false, true), rhythmCellResults(played, shifted).map { it.isCorrect })
    }

    @Test
    fun settingsAndAttempts() {
        assertThrows(IllegalArgumentException::class.java) { RhythmSettings(bars = 3) }
        val rhythm = Rhythm(listOf(RhythmCell.HALF, RhythmCell.HALF))
        val other = Rhythm(listOf(RhythmCell.HALF, RhythmCell.QUARTER, RhythmCell.QUARTER))
        val question = RhythmQuestion(rhythm, RhythmTempo.FAST, listOf(rhythm, other))
        val answered = ExerciseEngine<Rhythm, RhythmQuestion> { question }.start().promptPlayed(1_000).submitAnswer(other, 2_500)
        assertEquals(RhythmAttempt("s", 2_500, rhythm, RhythmTempo.FAST, other, false, 1, 1_500), RhythmAttempt.from(answered, "s"))
        assertEquals("half, quarter, quarter", other.description)
    }
}
