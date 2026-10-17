package com.example.eartrainer.exercise

import kotlin.random.Random

/** One past answer about [item], e.g. an interval or a chord quality. */
data class ItemResult<T>(val item: T, val isCorrect: Boolean)

/**
 * Turns recent results into selection weights so practice leans towards what the listener gets
 * wrong. Items are never excluded: even mastered ones keep [MIN_WEIGHT], so they still come up
 * now and then and forgetting is noticed.
 */
object WeakSpotWeighting {
    /** Floor for well-known items, a few times less likely than weak ones but never zero. */
    const val MIN_WEIGHT = 0.2

    /** Weight for an item with no history: between mastered and weak, so new items get asked. */
    val UNSEEN_WEIGHT: Double = weightOf(correct = 0, attempts = 0)

    /**
     * MIN_WEIGHT plus the estimated error rate. The estimate adds one imaginary right and one
     * imaginary wrong answer (Laplace smoothing), so a single early miss or hit does not swing an
     * item to an extreme. 20 of 20 right gives about 0.25; 0 of 5 right gives about 1.06.
     */
    fun weightOf(correct: Int, attempts: Int): Double {
        val estimatedAccuracy = (correct + 1.0) / (attempts + 2.0)
        return MIN_WEIGHT + (1 - estimatedAccuracy)
    }

    fun <T> weights(results: List<ItemResult<T>>): Map<T, Double> =
        results.groupBy { it.item }.mapValues { (_, itemResults) ->
            weightOf(correct = itemResults.count { it.isCorrect }, attempts = itemResults.size)
        }
}

/** Picks an element with probability proportional to its weight; falls back to uniform if all are zero. */
fun <T> List<T>.weightedRandom(random: Random, weightOf: (T) -> Double): T {
    require(isNotEmpty()) { "Cannot pick from an empty list" }
    val weights = map { weightOf(it).coerceAtLeast(0.0) }
    val total = weights.sum()
    if (total <= 0.0) return random(random)
    var remaining = random.nextDouble() * total
    weights.forEachIndexed { index, weight ->
        remaining -= weight
        if (remaining < 0) return this[index]
    }
    return last() // Floating-point rounding can leave a sliver on the last element.
}
