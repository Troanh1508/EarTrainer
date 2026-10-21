import Foundation

/// Simple intervals up to an octave: the single source of truth for interval names and sizes.
enum Interval: String, StoredEnum {
    case minorSecond = "MINOR_SECOND"
    case majorSecond = "MAJOR_SECOND"
    case minorThird = "MINOR_THIRD"
    case majorThird = "MAJOR_THIRD"
    case perfectFourth = "PERFECT_FOURTH"
    case tritone = "TRITONE"
    case perfectFifth = "PERFECT_FIFTH"
    case minorSixth = "MINOR_SIXTH"
    case majorSixth = "MAJOR_SIXTH"
    case minorSeventh = "MINOR_SEVENTH"
    case majorSeventh = "MAJOR_SEVENTH"
    case octave = "OCTAVE"

    /// Declared smallest first, one semitone apart.
    var semitones: Int { ordinal + 1 }

    var displayName: String {
        switch self {
        case .minorSecond: "Minor second"
        case .majorSecond: "Major second"
        case .minorThird: "Minor third"
        case .majorThird: "Major third"
        case .perfectFourth: "Perfect fourth"
        case .tritone: "Tritone"
        case .perfectFifth: "Perfect fifth"
        case .minorSixth: "Minor sixth"
        case .majorSixth: "Major sixth"
        case .minorSeventh: "Minor seventh"
        case .majorSeventh: "Major seventh"
        case .octave: "Octave"
        }
    }

    var abbreviation: String {
        switch self {
        case .minorSecond: "m2"
        case .majorSecond: "M2"
        case .minorThird: "m3"
        case .majorThird: "M3"
        case .perfectFourth: "P4"
        case .tritone: "TT"
        case .perfectFifth: "P5"
        case .minorSixth: "m6"
        case .majorSixth: "M6"
        case .minorSeventh: "m7"
        case .majorSeventh: "M7"
        case .octave: "P8"
        }
    }

    static func fromSemitones(_ semitones: Int) -> Interval? { allCases.first { $0.semitones == semitones } }

    /// The interval between two pitches regardless of their order, if it is a simple interval.
    static func between(_ first: Pitch, _ second: Pitch) -> Interval? { fromSemitones(abs(second.midi - first.midi)) }
}
