import Foundation

/**
 * The source of randomness for question generators. The default draws from the system
 * generator; tests pass a seed for repeatable questions.
 */
final class RandomSource: RandomNumberGenerator {
    private let nextValue: () -> UInt64

    static let system = RandomSource()

    init() {
        var generator = SystemRandomNumberGenerator()
        nextValue = { generator.next() }
    }

    /// A repeatable sequence (SplitMix64).
    init(seed: UInt64) {
        var state = seed
        nextValue = {
            state &+= 0x9E37_79B9_7F4A_7C15
            var z = state
            z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
            z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
            return z ^ (z >> 31)
        }
    }

    func next() -> UInt64 { nextValue() }

    /// A number in [range]; like Kotlin's `nextInt(from, until)` for a half-open range.
    func int(in range: Range<Int>) -> Int {
        var generator = self
        return Int.random(in: range, using: &generator)
    }

    func int(in range: ClosedRange<Int>) -> Int {
        var generator = self
        return Int.random(in: range, using: &generator)
    }

    /// A number in [0, 1).
    func double() -> Double {
        var generator = self
        return Double.random(in: 0..<1, using: &generator)
    }

    func element<T>(of array: [T]) -> T {
        precondition(!array.isEmpty, "Cannot pick from an empty list")
        return array[int(in: 0..<array.count)]
    }

    func elementOrNil<T>(of array: [T]) -> T? { array.isEmpty ? nil : element(of: array) }

    func shuffled<T>(_ array: [T]) -> [T] {
        var generator = self
        return array.shuffled(using: &generator)
    }
}

extension Set where Element: StoredEnum {
    /// The elements in declaration order, so picks from a set are repeatable with a seed.
    var inCatalogOrder: [Element] { Element.allCases.filter(contains) }
}
