import Foundation

/// How exercises that work in a key choose it.
enum KeyChoice: String, StoredEnum {
    case random = "RANDOM"
    case cOnly = "C_ONLY"

    var displayName: String {
        switch self {
        case .random: "Random key"
        case .cOnly: "Always C"
        }
    }

    /**
     * Tonics are drawn from G3..F♯4: every key is available, and both key-setting chords
     * (down to about an octave below the tonic) and notes above it stay in a clear register.
     */
    static let tonicRange = 55...66
    static let cTonic = Pitch(60)

    func pickTonic(_ random: RandomSource) -> Pitch {
        switch self {
        case .random: Pitch(random.int(in: KeyChoice.tonicRange))
        case .cOnly: KeyChoice.cTonic
        }
    }
}

/// What is played before the note to establish the key.
enum KeyContext: String, StoredEnum {
    case cadence = "CADENCE"
    case tonicChord = "TONIC_CHORD"

    var displayName: String {
        switch self {
        case .cadence: "Cadence (I–IV–V–I)"
        case .tonicChord: "Tonic chord"
        }
    }

    func moments(in key: Key) -> [[Pitch]] {
        switch self {
        case .cadence: key.cadence()
        case .tonicChord: [key.tonicChord()]
        }
    }
}
