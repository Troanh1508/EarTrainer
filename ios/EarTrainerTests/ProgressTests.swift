import XCTest
@testable import EarTrainer

final class ProgressTests: XCTestCase {
    private let accuracy = [
        AccuracyItem(label: "Never", attempts: 0, correct: 0),
        AccuracyItem(label: "Strong", attempts: 10, correct: 9),
        AccuracyItem(label: "Weak, few", attempts: 2, correct: 1),
        AccuracyItem(label: "Weak, many", attempts: 10, correct: 5),
        AccuracyItem(label: "Also never", attempts: 0, correct: 0),
    ]

    func testWeakestFirstSortsPracticedItemsByAccuracyThenLeavesUnpracticedLast() {
        XCTAssertEqual(accuracy.weakestFirst.map(\.label), ["Weak, many", "Weak, few", "Strong", "Never", "Also never"])
        XCTAssertEqual(accuracy.map(\.needsWork), [false, false, true, true, false])
    }

    func testStrongestFirstPutsMoreAnswersFirstOnATieAndUnpracticedItemsLast() {
        XCTAssertEqual(accuracy.strongestFirst.map(\.label), ["Strong", "Weak, many", "Weak, few", "Never", "Also never"])
    }

    func testOrderedFollowsTheChosenOrder() {
        XCTAssertEqual(accuracy.ordered(.weakestFirst), accuracy.weakestFirst)
        XCTAssertEqual(accuracy.ordered(.strongestFirst), accuracy.strongestFirst)
    }
}
