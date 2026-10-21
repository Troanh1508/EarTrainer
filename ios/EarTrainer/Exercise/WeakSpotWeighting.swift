import Foundation

/// One past answer about [item], e.g. an interval or a chord quality.
struct ItemResult<T: Hashable>: Hashable {
    let item: T
    let isCorrect: Bool
}

/**
 * Turns recent results into selection weights so practice leans toward what the listener gets
 * wrong. Items are never excluded: even mastered ones keep `minWeight`, so they still come up
 * now and then and forgetting is noticed.
 */
enum WeakSpotWeighting {
    /// Floor for well-known items, a few times less likely than weak ones but never zero.
    static let minWeight = 0.2

    /// Weight for an item with no history: between mastered and weak, so new items get asked.
    static let unseenWeight = weightOf(correct: 0, attempts: 0)

    /**
     * `minWeight` plus the estimated error rate. The estimate adds one imaginary right and one
     * imaginary wrong answer (Laplace smoothing), so a single early miss or hit does not swing an
     * item to an extreme. 20 of 20 right gives about 0.25; 0 of 5 right gives about 1.06.
     */
    static func weightOf(correct: Int, attempts: Int) -> Double {
        let estimatedAccuracy = (Double(correct) + 1) / (Double(attempts) + 2)
        return minWeight + (1 - estimatedAccuracy)
    }

    static func weights<T>(_ results: [ItemResult<T>]) -> [T: Double] {
        Dictionary(grouping: results, by: \.item).mapValues { itemResults in
            weightOf(correct: itemResults.filter(\.isCorrect).count, attempts: itemResults.count)
        }
    }
}

extension Array {
    /// Picks an element with probability proportional to its weight; falls back to uniform if all are zero.
    func weightedRandom(_ random: RandomSource, weightOf: (Element) -> Double) -> Element {
        precondition(!isEmpty, "Cannot pick from an empty list")
        let weights = map { Swift.max(0, weightOf($0)) }
        let total = weights.reduce(0, +)
        if total <= 0 { return random.element(of: self) }
        var remaining = random.double() * total
        for (index, weight) in weights.enumerated() {
            remaining -= weight
            if remaining < 0 { return self[index] }
        }
        return self[count - 1] // Floating-point rounding can leave a sliver on the last element.
    }
}
