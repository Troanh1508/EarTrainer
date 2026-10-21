import Foundation

/**
 * The twelve chromatic scale degrees, named relative to the major scale ("♭3" is a minor third
 * above the tonic) with movable-do solfège. Single source of truth for degree names.
 */
enum ScaleDegree: String, StoredEnum {
    case tonic = "TONIC"
    case flatSecond = "FLAT_SECOND"
    case second = "SECOND"
    case flatThird = "FLAT_THIRD"
    case third = "THIRD"
    case fourth = "FOURTH"
    case sharpFourth = "SHARP_FOURTH"
    case fifth = "FIFTH"
    case flatSixth = "FLAT_SIXTH"
    case sixth = "SIXTH"
    case flatSeventh = "FLAT_SEVENTH"
    case seventh = "SEVENTH"

    /// Declared chromatically from the tonic, one semitone apart.
    var semitonesAboveTonic: Int { ordinal }

    var label: String { ["1", "♭2", "2", "♭3", "3", "4", "♯4", "5", "♭6", "6", "♭7", "7"][ordinal] }

    var solfege: String { ["Do", "Ra", "Re", "Me", "Mi", "Fa", "Fi", "Sol", "Le", "La", "Te", "Ti"][ordinal] }

    static func fromSemitones(_ semitones: Int) -> ScaleDegree { allCases[positiveMod(semitones, 12)] }
}

enum Mode: String, StoredEnum {
    case major = "MAJOR"
    /// Natural minor for the scale; cadences use the raised leading tone, as in common practice.
    case minor = "MINOR"

    var displayName: String {
        switch self {
        case .major: "Major"
        case .minor: "Minor"
        }
    }

    var diatonicDegrees: [ScaleDegree] {
        switch self {
        case .major: [.tonic, .second, .third, .fourth, .fifth, .sixth, .seventh]
        case .minor: [.tonic, .second, .flatThird, .fourth, .fifth, .flatSixth, .flatSeventh]
        }
    }

    fileprivate var third: Int { self == .major ? 4 : 3 }
}

/// A key: a tonic pitch (which also fixes the register of cadences) and a mode.
struct Key: Hashable {
    let tonic: Pitch
    let mode: Mode

    private static let majorKeyNames = ["C", "D♭", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B"]
    private static let minorKeyNames = ["C", "C♯", "D", "E♭", "E", "F", "F♯", "G", "G♯", "A", "B♭", "B"]
    private static let flatNames = ["C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B"]

    /// Conventional key name, e.g. "E♭ major" or "C♯ minor", using the usual flat or sharp spelling.
    var name: String {
        let names = mode == .major ? Key.majorKeyNames : Key.minorKeyNames
        return "\(names[tonic.midi % 12]) \(mode.displayName.lowercased())"
    }

    func pitchOf(_ degree: ScaleDegree) -> Pitch { tonic.transposed(by: degree.semitonesAboveTonic) }

    /// The tonic triad with the root doubled an octave below.
    func tonicChord() -> [Pitch] { voiced([-12, 0, mode.third, 7]) }

    /**
     * I–IV–V–I (i–iv–V–i in minor) in a smooth keyboard voicing around the tonic, which firmly
     * establishes the key before a question.
     */
    func cadence() -> [[Pitch]] {
        [
            voiced([-12, 0, mode.third, 7]),
            // IV (iv) in second inversion over its root: the tonic is held as a common tone.
            voiced([-7, 0, 5, 5 + mode.third]),
            // V in first inversion over its root, with the leading tone a semitone below the tonic.
            voiced([-5, -1, 2, 7]),
            voiced([-12, 0, mode.third, 7]),
        ]
    }

    /// Note name spelled for this key: flats in flat keys (A♭ in E♭ major), sharps otherwise.
    func noteName(_ pitch: Pitch) -> String {
        prefersFlats ? Key.flatNames[pitch.midi % 12] : pitch.pitchClassName
    }

    /// Keys whose signatures use flats: F, B♭, E♭, A♭, D♭ major and D, G, C, F, B♭, E♭ minor.
    private var prefersFlats: Bool {
        let flatTonics: Set<Int> = mode == .major ? [5, 10, 3, 8, 1] : [2, 7, 0, 5, 10, 3]
        return flatTonics.contains(tonic.midi % 12)
    }

    private func voiced(_ offsets: [Int]) -> [Pitch] { offsets.map { tonic.transposed(by: $0) } }
}
