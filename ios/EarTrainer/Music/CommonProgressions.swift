import Foundation

/**
 * Well-known progressions from pop, rock, folk and classical harmony, each starting on the
 * tonic. Written as Roman numerals so the list reads like a harmony textbook.
 */
enum CommonProgressions {

    private static let major = [
        // Three chords
        "I IV V", "I V I", "I IV I", "I V IV", "I vi IV", "I vi V", "I ii V", "I iii IV", "I V vi",
        // Four chords
        "I IV V I", "I V vi IV", "I vi IV V", "I IV vi V", "I ii V I", "I vi ii V", "I IV I V",
        "I V IV I", "I iii IV V", "I IV ii V", "I iii vi IV", "I V IV V", "I IV V IV",
        // Five chords
        "I vi IV V I", "I IV V IV I", "I V vi IV I", "I vi ii V I", "I IV ii V I", "I IV I V I",
        "I iii vi IV V", "I V vi iii IV", "I ii IV V I", "I V IV V I",
    ]

    private static let minor = [
        // Three chords
        "i iv V", "i VI VII", "i iv i", "i V i", "i VII VI", "i ii° V", "i VI V", "i VII i",
        // Four chords
        "i iv V i", "i VI III VII", "i VII VI V", "i iv VII III", "i VI iv V", "i ii° V i",
        "i VI VII i", "i iv i V", "i VII VI VII", "i III VII iv", "i iv VI V",
        // Five chords
        "i VI III VII i", "i iv VII III VI", "i VII VI V i", "i VI iv V i", "i iv ii° V i",
        "i VI VII V i", "i iv i V i", "i iv V iv i",
    ]

    private static let byMode: [Mode: [ChordProgression]] = [
        .major: major.map { parse(.major, $0) },
        .minor: minor.map { parse(.minor, $0) },
    ]

    /// Every common progression in [mode] with [length] chords.
    static func forMode(_ mode: Mode, length: Int) -> [ChordProgression] {
        byMode[mode, default: []].filter { $0.chords.count == length }
    }

    /// Reads a space-separated list of numerals, e.g. "I vi IV V".
    static func parse(_ mode: Mode, _ numerals: String) -> ChordProgression {
        let chords = DiatonicChord.all(mode)
        return ChordProgression(numerals.split(separator: " ").map { numeral in
            guard let chord = chords.first(where: { $0.numeral == numeral }) else {
                preconditionFailure("\"\(numeral)\" is not a \(mode.displayName.lowercased()) numeral")
            }
            return chord
        })
    }
}
