import Foundation

/**
 * Chord qualities as semitone offsets of each chord tone above the root, in root position.
 * Single source of truth for chord names, symbols and spellings.
 */
enum ChordQuality: String, StoredEnum {
    case major = "MAJOR"
    case minor = "MINOR"
    case diminished = "DIMINISHED"
    case augmented = "AUGMENTED"
    case majorSeventh = "MAJOR_SEVENTH"
    case dominantSeventh = "DOMINANT_SEVENTH"
    case minorSeventh = "MINOR_SEVENTH"
    case halfDiminishedSeventh = "HALF_DIMINISHED_SEVENTH"
    case diminishedSeventh = "DIMINISHED_SEVENTH"

    var semitonesAboveRoot: [Int] {
        switch self {
        case .major: [0, 4, 7]
        case .minor: [0, 3, 7]
        case .diminished: [0, 3, 6]
        case .augmented: [0, 4, 8]
        case .majorSeventh: [0, 4, 7, 11]
        case .dominantSeventh: [0, 4, 7, 10]
        case .minorSeventh: [0, 3, 7, 10]
        case .halfDiminishedSeventh: [0, 3, 6, 10]
        case .diminishedSeventh: [0, 3, 6, 9]
        }
    }

    var displayName: String {
        switch self {
        case .major: "Major"
        case .minor: "Minor"
        case .diminished: "Diminished"
        case .augmented: "Augmented"
        case .majorSeventh: "Major 7th"
        case .dominantSeventh: "Dominant 7th"
        case .minorSeventh: "Minor 7th"
        case .halfDiminishedSeventh: "Half-diminished 7th"
        case .diminishedSeventh: "Diminished 7th"
        }
    }

    /// Suffix after the root in chord symbols, e.g. "m7" in "Cm7".
    var symbol: String {
        switch self {
        case .major: ""
        case .minor: "m"
        case .diminished: "dim"
        case .augmented: "aug"
        case .majorSeventh: "maj7"
        case .dominantSeventh: "7"
        case .minorSeventh: "m7"
        case .halfDiminishedSeventh: "m7♭5"
        case .diminishedSeventh: "dim7"
        }
    }

    var noteCount: Int { semitonesAboveRoot.count }
    var isSeventh: Bool { noteCount == 4 }

    /// Inversions this chord can take: three for triads, four for seventh chords.
    var inversions: [ChordInversion] { Array(ChordInversion.allCases.prefix(noteCount)) }

    /**
     * Close-position shape for [inversion], as semitone offsets above the bass note. Each
     * inversion moves the lowest chord tone up an octave, e.g. a major triad in first inversion
     * is E–G–C: [0, 3, 8].
     */
    func offsetsAboveBass(_ inversion: ChordInversion) -> [Int] {
        precondition(inversions.contains(inversion), "\(displayName) chords have no \(inversion.displayName.lowercased())")
        let k = inversion.ordinal
        let raised = Array(semitonesAboveRoot.dropFirst(k)) + semitonesAboveRoot.prefix(k).map { $0 + 12 }
        return raised.map { $0 - raised[0] }
    }

    /// Semitones from the bass note up to the root in [inversion]'s close-position shape.
    func rootOffsetAboveBass(_ inversion: ChordInversion) -> Int {
        offsetsAboveBass(inversion)[(noteCount - inversion.ordinal) % noteCount]
    }
}

/// Which chord tone is in the bass.
enum ChordInversion: String, StoredEnum {
    case rootPosition = "ROOT_POSITION"
    case first = "FIRST"
    case second = "SECOND"
    case third = "THIRD"

    var displayName: String {
        switch self {
        case .rootPosition: "Root position"
        case .first: "1st inversion"
        case .second: "2nd inversion"
        case .third: "3rd inversion"
        }
    }
}
