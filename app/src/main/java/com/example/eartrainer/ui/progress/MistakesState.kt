package com.example.eartrainer.ui.progress

import com.example.eartrainer.ui.compare.ComparisonRequest
import kotlin.math.abs

/**
 * How often something was answered as [answered]; null [answered] means something else entirely.
 * The keys are the values' stable names (e.g. "MINOR_SIXTH"), for turning a label back into a value;
 * the short forms are compact labels (e.g. "m6"), and the semitones each value's pitch where it has one.
 */
data class LabeledConfusion(
    val asked: String,
    val answered: String?,
    val count: Int,
    val askedKey: String = asked,
    val answeredKey: String? = answered,
    val askedShort: String = asked,
    val answeredShort: String? = answered,
    val askedSemitones: Int? = null,
    val answeredSemitones: Int? = null,
)

/** One answer the selected item was given as, and how often. [label] is null for "something else". */
data class AnswerShare(val label: String?, val count: Int, val isRight: Boolean)

/** Two items mistaken for each other [count] times, in either direction. */
data class ConfusedPair(val first: String, val second: String, val count: Int)

/** What one item was answered as, plus the exercise's most confused pairs, under one filter. */
data class MistakesUiState(
    val filter: ProgressFilter,
    /** Practiced items, weakest first, to choose from. */
    val items: List<AccuracyItem>,
    val selected: AccuracyItem,
    /** The right answer first, then wrong answers from most to least frequent. */
    val answers: List<AnswerShare>,
    /** Most frequent first. */
    val confusedPairs: List<ConfusedPair>,
    /** The stable name of every item and answer, by its label. */
    val keys: Map<String, String> = emptyMap(),
    /** Compact labels, e.g. "m6" for "Minor sixth", where an exercise has them. */
    val shortLabels: Map<String, String> = emptyMap(),
    /** Each item's pitch in semitones, for exercises where items are pitches. */
    val semitones: Map<String, Int> = emptyMap(),
) {
    val exercise get() = filter.exercise

    /** The wrong answer given most often, if there was any. */
    val mostCommonMistake: AnswerShare? get() = answers.firstOrNull { !it.isRight }

    fun shortLabelOf(label: String): String = shortLabels[label] ?: label

    /** How many semitones apart two items are, when both are pitches. */
    fun semitonesBetween(first: String, second: String): Int? {
        val a = semitones[first] ?: return null
        val b = semitones[second] ?: return null
        return abs(a - b)
    }

    /**
     * A drill on the two items labeled [first] and [second], under the same style, chord target
     * or mode as these mistakes; null if this exercise has no drill or a label is unknown.
     */
    fun comparisonFor(first: String, second: String): ComparisonRequest? {
        if (exercise !in ComparisonRequest.SUPPORTED) return null
        val firstKey = keys[first] ?: return null
        val secondKey = keys[second] ?: return null
        val scope = when (filter) {
            is ProgressFilter.IntervalStyleFilter -> filter.style?.name
            is ProgressFilter.ChordTargetFilter -> filter.target.name
            is ProgressFilter.ModeFilter -> filter.mode?.name
            is ProgressFilter.DictationModeFilter -> filter.mode.name
            is ProgressFilter.ProgressionModeFilter, ProgressFilter.RhythmFilter -> null
        }
        return ComparisonRequest(exercise, scope, firstKey, secondKey)
    }

    companion object {
        const val PAIR_LIMIT = 5

        /** Null when [selectedLabel] has no answers under [filter], e.g. after the filter changed. */
        fun from(
            filter: ProgressFilter,
            accuracy: List<AccuracyItem>,
            confusions: List<LabeledConfusion>,
            selectedLabel: String,
        ): MistakesUiState? {
            val items = ProgressUiState(filter, accuracy, recentSessions = emptyList()).weakestFirst.filter { it.attempts > 0 }
            val selected = items.find { it.label == selectedLabel } ?: return null
            val answers = confusions
                .filter { it.asked == selected.label }
                .map { AnswerShare(it.answered, it.count, isRight = it.answered == it.asked) }
                .sortedWith(compareByDescending<AnswerShare> { it.isRight }.thenByDescending { it.count })
            // Each pair is named weaker item first, the order the items are listed in.
            val rank = items.withIndex().associate { (index, item) -> item.label to index }
            val pairs = confusions
                .filter { it.answered != null && it.answered != it.asked }
                .groupBy { listOf(it.asked, checkNotNull(it.answered)).sortedBy { label -> rank[label] ?: Int.MAX_VALUE } }
                .map { (pair, mistakes) -> ConfusedPair(pair[0], pair[1], mistakes.sumOf { it.count }) }
                .sortedWith(compareByDescending<ConfusedPair> { it.count }.thenBy { rank[it.first] ?: Int.MAX_VALUE })
                .take(PAIR_LIMIT)
            val keys = confusions.associate { it.asked to it.askedKey } +
                confusions.mapNotNull { confusion -> confusion.answered?.let { label -> confusion.answeredKey?.let { label to it } } }
            fun <T> collect(asked: (LabeledConfusion) -> T?, answered: (LabeledConfusion) -> T?): Map<String, T> =
                confusions.mapNotNull { c -> asked(c)?.let { c.asked to it } }.toMap() +
                    confusions.mapNotNull { c -> c.answered?.let { label -> answered(c)?.let { label to it } } }.toMap()
            return MistakesUiState(
                filter, items, selected, answers, pairs, keys,
                shortLabels = collect({ it.askedShort }, { it.answeredShort }),
                semitones = collect({ it.askedSemitones }, { it.answeredSemitones }),
            )
        }
    }
}
