package com.example.eartrainer.ui.dictation

import androidx.compose.runtime.saveable.SaverScope
import com.example.eartrainer.music.Melody
import com.example.eartrainer.music.ScaleDegree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationDraftTest {

    private val start = DictationDraft.start(length = 4)

    @Test
    fun startsWithTheGivenTonicAndTheFirstWrittenSlotSelected() {
        assertEquals(listOf(ScaleDegree.TONIC, null, null, null), start.notes)
        assertEquals(1, start.selected)
        assertFalse(start.isComplete)
        assertFalse(start.hasWrittenNotes)
        assertNull(start.toMelodyOrNull())
    }

    @Test
    fun typingInOrderFillsSlotsAndAdvances() {
        val typed = start.enter(ScaleDegree.SECOND).enter(ScaleDegree.THIRD).enter(ScaleDegree.FIFTH)
        assertEquals(listOf(ScaleDegree.TONIC, ScaleDegree.SECOND, ScaleDegree.THIRD, ScaleDegree.FIFTH), typed.notes)
        assertTrue(typed.isComplete)
        // With nothing left to fill, the selection stays on the last slot written.
        assertEquals(3, typed.selected)
        assertEquals(Melody(typed.notes.map { it!! }), typed.toMelodyOrNull())
    }

    @Test
    fun tappingAFilledNoteLetsItBeReplacedDirectly() {
        val typed = start.enter(ScaleDegree.SECOND).enter(ScaleDegree.THIRD).enter(ScaleDegree.FIFTH)
        val fixed = typed.select(2).enter(ScaleDegree.FOURTH)
        assertEquals(listOf(ScaleDegree.TONIC, ScaleDegree.SECOND, ScaleDegree.FOURTH, ScaleDegree.FIFTH), fixed.notes)
    }

    @Test
    fun afterEditingTheCursorMovesToTheNextGapWrappingAround() {
        // Fill slot 3 first, leaving 1 and 2 empty.
        val skipped = start.select(3).enter(ScaleDegree.FIFTH)
        assertEquals(1, skipped.selected)
        assertEquals(2, skipped.enter(ScaleDegree.SECOND).selected)
    }

    @Test
    fun clearAllEmptiesEveryWrittenSlot() {
        val cleared = start.enter(ScaleDegree.SECOND).enter(ScaleDegree.THIRD).select(2).clearAll()
        assertEquals(start, cleared)
    }

    @Test
    fun theGivenTonicCannotBeSelectedOrChanged() {
        assertSame(start, start.select(0))
        assertSame(start, start.select(4))
        assertThrows(IllegalArgumentException::class.java) { DictationDraft(listOf(ScaleDegree.TONIC, null), selected = 0) }
    }

    @Test
    fun undoStepsBackThroughEditsInOrder() {
        val first = DictationEntry.start(4).enter(ScaleDegree.SECOND)
        val second = first.enter(ScaleDegree.THIRD)
        assertTrue(second.canUndo)
        assertEquals(first.draft, second.undo().draft)
        assertEquals(DictationDraft.start(4), second.undo().undo().draft)
        assertFalse(second.undo().undo().canUndo)
    }

    @Test
    fun undoRevertsAReplacedNoteIncludingWhereTheCursorWas() {
        val typed = DictationEntry.start(4).enter(ScaleDegree.SECOND).enter(ScaleDegree.THIRD).enter(ScaleDegree.FIFTH)
        val replaced = typed.select(1).enter(ScaleDegree.SEVENTH)
        val undone = replaced.undo()
        assertEquals(typed.draft.notes, undone.draft.notes)
        // The cursor goes back to the slot that was edited, ready for another try.
        assertEquals(1, undone.draft.selected)
    }

    @Test
    fun selectingIsNotAnUndoStep() {
        val typed = DictationEntry.start(4).enter(ScaleDegree.SECOND)
        val moved = typed.select(3)
        assertEquals(typed.history, moved.history)
        assertEquals(DictationDraft.start(4), moved.undo().draft)
    }

    @Test
    fun clearAllCanBeUndone() {
        val typed = DictationEntry.start(4).enter(ScaleDegree.SECOND).enter(ScaleDegree.THIRD)
        val cleared = typed.clearAll()
        assertFalse(cleared.draft.hasWrittenNotes)
        assertEquals(typed.draft, cleared.undo().draft)
        // Clearing an already empty draft changes nothing, so it adds no undo step.
        val empty = DictationEntry.start(4)
        assertSame(empty, empty.clearAll())
    }

    @Test
    fun historyIsCapped() {
        var entry = DictationEntry.start(6)
        repeat(DictationEntry.MAX_UNDO_STEPS + 20) { i -> entry = entry.enter(if (i % 2 == 0) ScaleDegree.SECOND else ScaleDegree.THIRD) }
        assertEquals(DictationEntry.MAX_UNDO_STEPS, entry.history.size)
    }

    @Test
    fun saverRestoresDraftAndUndoHistory() {
        val scope = object : SaverScope {
            override fun canBeSaved(value: Any) = true
        }
        val entry = DictationEntry.start(4).enter(ScaleDegree.SIXTH).enter(ScaleDegree.FIFTH).select(1)
        val saved = with(DictationEntry.Saver) { scope.save(entry) }!!
        assertEquals(entry, DictationEntry.Saver.restore(saved))
    }
}
