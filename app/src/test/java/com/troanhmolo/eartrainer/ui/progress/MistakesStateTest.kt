package com.troanhmolo.eartrainer.ui.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MistakesStateTest {

    private val filter = ProgressFilter.IntervalStyleFilter(null)
    private val accuracy = listOf(
        AccuracyItem("Major third", 10, 9),
        AccuracyItem("Minor sixth", 10, 5),
        AccuracyItem("Major sixth", 8, 6),
        AccuracyItem("Octave", 0, 0),
    )
    private val confusions = listOf(
        LabeledConfusion("Minor sixth", "Minor sixth", 5),
        LabeledConfusion("Minor sixth", "Major sixth", 3),
        LabeledConfusion("Minor sixth", "Perfect fifth", 1),
        LabeledConfusion("Minor sixth", null, 1),
        LabeledConfusion("Major sixth", "Major sixth", 6),
        LabeledConfusion("Major sixth", "Minor sixth", 2),
        LabeledConfusion("Major third", "Major third", 9),
        LabeledConfusion("Major third", "Perfect fourth", 1),
    )

    @Test
    fun answersListTheRightOneFirstThenWrongOnesByFrequency() {
        val state = checkNotNull(MistakesUiState.from(filter, accuracy, confusions, "Minor sixth"))
        assertEquals(
            listOf(
                AnswerShare("Minor sixth", 5, isRight = true),
                AnswerShare("Major sixth", 3, isRight = false),
                AnswerShare("Perfect fifth", 1, isRight = false),
                AnswerShare(null, 1, isRight = false),
            ),
            state.answers,
        )
        assertEquals(AnswerShare("Major sixth", 3, isRight = false), state.mostCommonMistake)
        // Practiced items only, weakest first, to choose from.
        assertEquals(listOf("Minor sixth", "Major sixth", "Major third"), state.items.map { it.label })
    }

    @Test
    fun pairsAddUpMistakesInBothDirectionsAndNameTheWeakerItemFirst() {
        val state = checkNotNull(MistakesUiState.from(filter, accuracy, confusions, "Major third"))
        assertEquals(
            listOf(
                ConfusedPair("Minor sixth", "Major sixth", 5),
                // Unpracticed answers are still named, after every practiced item.
                ConfusedPair("Minor sixth", "Perfect fifth", 1),
                ConfusedPair("Major third", "Perfect fourth", 1),
            ),
            state.confusedPairs,
        )
    }

    @Test
    fun anItemWithoutMistakesHasNoMostCommonMistake() {
        val state = checkNotNull(MistakesUiState.from(filter, accuracy, confusions.filterNot { it.asked == "Major third" && it.answered != "Major third" }, "Major third"))
        assertNull(state.mostCommonMistake)
    }

    @Test
    fun anItemWithoutAnswersCannotBeShown() {
        assertNull(MistakesUiState.from(filter, accuracy, confusions, "Octave"))
        assertNull(MistakesUiState.from(filter, accuracy, confusions, "Not an interval"))
    }
}
