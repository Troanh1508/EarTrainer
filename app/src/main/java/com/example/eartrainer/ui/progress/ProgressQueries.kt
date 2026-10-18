package com.example.eartrainer.ui.progress

import com.example.eartrainer.data.history.AnswerAccuracy
import com.example.eartrainer.data.history.PracticeHistory
import com.example.eartrainer.exercise.ChordTarget
import com.example.eartrainer.exercise.ExerciseKind
import com.example.eartrainer.music.Mode
import com.example.eartrainer.music.RhythmCell
import com.example.eartrainer.music.ScaleDegree
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/*
 * Accuracy per item under a progress filter, labeled for display. Shared by the Progress and Home
 * screens so both count and name answers the same way.
 */

internal fun degreeLabel(degree: ScaleDegree) = "${degree.label} · ${degree.solfege}"

internal fun rhythmLabel(cell: RhythmCell) = cell.displayName.replaceFirstChar(Char::uppercase)

private fun AnswerAccuracy<*>.toItem(label: String) = AccuracyItem(label, attempts, correct)

internal fun PracticeHistory.accuracyFor(filter: ProgressFilter): Flow<List<AccuracyItem>> = when (filter) {
    is ProgressFilter.IntervalStyleFilter -> intervalAccuracy(filter.style).map { list -> list.map { it.toItem(it.item.displayName) } }
    is ProgressFilter.ChordTargetFilter -> chordAccuracy(filter.target).map { list -> list.map { it.toItem(it.item.displayName) } }
    is ProgressFilter.ModeFilter -> scaleDegreeAccuracy(filter.mode).map { list -> list.map { it.toItem(degreeLabel(it.item)) } }
    is ProgressFilter.ProgressionModeFilter -> progressionAccuracy(filter.mode).map { list -> list.map { it.toItem(it.item.numeral) } }
    is ProgressFilter.DictationModeFilter -> dictationAccuracy(filter.mode).map { list -> list.map { it.toItem(degreeLabel(it.item)) } }
    ProgressFilter.RhythmFilter -> rhythmAccuracy().map { list -> list.map { it.toItem(rhythmLabel(it.item)) } }
}

/**
 * Filters that together count every answer of [this] exercise exactly once: every style or
 * mode at once where a filter allows it, otherwise each chord target or each mode in turn.
 */
internal val ExerciseKind.everyAnswerFilters: List<ProgressFilter>
    get() = when (this) {
        ExerciseKind.INTERVALS -> listOf(ProgressFilter.IntervalStyleFilter(null))
        ExerciseKind.CHORDS -> ChordTarget.entries.map(ProgressFilter::ChordTargetFilter)
        ExerciseKind.SCALE_DEGREES -> listOf(ProgressFilter.ModeFilter(null))
        ExerciseKind.PROGRESSIONS -> Mode.entries.map(ProgressFilter::ProgressionModeFilter)
        ExerciseKind.DICTATION -> Mode.entries.map(ProgressFilter::DictationModeFilter)
        ExerciseKind.RHYTHM -> listOf(ProgressFilter.RhythmFilter)
    }
