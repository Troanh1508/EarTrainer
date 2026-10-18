package com.example.eartrainer.ui.progress

/** How often something was answered as [answered]; null [answered] means something else entirely. */
data class LabeledConfusion(val asked: String, val answered: String?, val count: Int)

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
) {
    val exercise get() = filter.exercise

    /** The wrong answer given most often, if there was any. */
    val mostCommonMistake: AnswerShare? get() = answers.firstOrNull { !it.isRight }

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
            return MistakesUiState(filter, items, selected, answers, pairs)
        }
    }
}
