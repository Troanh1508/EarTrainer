package com.example.eartrainer.ui.dictation

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.example.eartrainer.music.Melody
import com.example.eartrainer.music.ScaleDegree

/**
 * The notes written so far in a dictation, before "Check". Slot 0 holds the given tonic and is
 * never edited; the others start empty. One editable slot is selected at a time: keypad input
 * goes there, and tapping a slot selects it so any note can be changed directly.
 */
data class DictationDraft(val notes: List<ScaleDegree?>, val selected: Int) {

    init {
        require(notes.size >= 2 && notes.first() == ScaleDegree.TONIC) { "A draft has the given tonic and at least one note to write" }
        require(selected in 1 until notes.size) { "Only written notes can be selected" }
    }

    val isComplete: Boolean get() = notes.all { it != null }

    /** Whether any note has been written, i.e. whether there is anything to clear. */
    val hasWrittenNotes: Boolean get() = notes.drop(1).any { it != null }

    /** Selects slot [index] for editing; the given tonic cannot be selected. */
    fun select(index: Int): DictationDraft = if (index in 1 until notes.size) copy(selected = index) else this

    /**
     * Writes [degree] into the selected slot, then moves on to the next empty slot (wrapping to
     * earlier gaps), so typing a melody in order needs no extra taps. If every slot is filled,
     * the selection stays put.
     */
    fun enter(degree: ScaleDegree): DictationDraft {
        val updated = notes.toMutableList().also { it[selected] = degree }
        val emptySlots = updated.indices.filter { it > 0 && updated[it] == null }
        val next = emptySlots.firstOrNull { it > selected } ?: emptySlots.firstOrNull() ?: selected
        return DictationDraft(updated, next)
    }

    /** Empties every written slot and goes back to the first one. */
    fun clearAll(): DictationDraft = start(notes.size)

    /** The finished melody, or null while any slot is empty. */
    fun toMelodyOrNull(): Melody? = if (isComplete) Melody(notes.map { checkNotNull(it) }) else null

    companion object {
        fun start(length: Int): DictationDraft = DictationDraft(listOf(ScaleDegree.TONIC) + List(length - 1) { null }, selected = 1)

        /** Degree names (empty for an unwritten slot) followed by the selected index. */
        internal fun encode(draft: DictationDraft): List<String> = draft.notes.map { it?.name.orEmpty() } + draft.selected.toString()

        internal fun decode(saved: List<String>): DictationDraft =
            DictationDraft(saved.dropLast(1).map { name -> ScaleDegree.entries.find { it.name == name } }, saved.last().toInt())
    }
}

/**
 * A [DictationDraft] with the earlier versions needed for Undo. Every change to the notes can be
 * undone, including "Clear all"; moving the selection is not a change, so Undo skips over it.
 */
data class DictationEntry(val draft: DictationDraft, val history: List<DictationDraft> = emptyList()) {

    val canUndo: Boolean get() = history.isNotEmpty()

    /** Moves the cursor without adding an undo step. */
    fun select(index: Int): DictationEntry = copy(draft = draft.select(index))

    fun enter(degree: ScaleDegree): DictationEntry = edit(draft.enter(degree))

    fun clearAll(): DictationEntry = edit(draft.clearAll())

    /** Returns to the notes (and selection) before the last change. */
    fun undo(): DictationEntry = if (canUndo) DictationEntry(history.last(), history.dropLast(1)) else this

    private fun edit(next: DictationDraft): DictationEntry =
        if (next == draft) this else DictationEntry(next, (history + draft).takeLast(MAX_UNDO_STEPS))

    companion object {
        /** Far more than a six-note melody needs, while keeping saved state small. */
        const val MAX_UNDO_STEPS = 50

        fun start(length: Int): DictationEntry = DictationEntry(DictationDraft.start(length))

        /** Saves the draft and its undo history across rotation and process death. */
        val Saver: Saver<DictationEntry, Any> = listSaver(
            save = { entry -> (entry.history + entry.draft).map(DictationDraft::encode) },
            restore = { saved ->
                val drafts = saved.map(DictationDraft::decode)
                DictationEntry(drafts.last(), drafts.dropLast(1))
            },
        )
    }
}
