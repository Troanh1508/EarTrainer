import Foundation

/**
 * A triad built on one step of a key's scale, named with a Roman numeral (upper case for major,
 * lower case for minor, ° for diminished).
 *
 * Minor keys use the natural minor scale except for V, which takes the raised leading tone as in
 * common-practice harmony, so a minor-key cadence still pulls home.
 */
struct DiatonicChord: Hashable {
    let mode: Mode
    let step: Int

    init(_ mode: Mode, _ step: Int) {
        precondition((1...7).contains(step), "Scale step must be 1..7, was \(step)")
        self.mode = mode
        self.step = step
    }

    var rootSemitonesAboveTonic: Int { DiatonicChord.roots(mode)[step - 1] }
    var quality: ChordQuality { DiatonicChord.qualities(mode)[step - 1] }
    var numeral: String { DiatonicChord.numerals(mode)[step - 1] }

    /// Chord symbol in [key], e.g. "Am" for vi in C major or "A♭" for IV in E♭ major.
    func symbol(in key: Key) -> String { key.noteName(key.tonic.transposed(by: rootSemitonesAboveTonic)) + quality.symbol }

    /// Pitch classes of the chord tones, as semitones above the tonic.
    var toneSemitones: [Int] { quality.semitonesAboveRoot.map { (rootSemitonesAboveTonic + $0) % 12 } }

    static func all(_ mode: Mode) -> [DiatonicChord] { (1...7).map { DiatonicChord(mode, $0) } }

    private static func roots(_ mode: Mode) -> [Int] {
        switch mode {
        case .major: [0, 2, 4, 5, 7, 9, 11]
        case .minor: [0, 2, 3, 5, 7, 8, 10]
        }
    }

    private static func qualities(_ mode: Mode) -> [ChordQuality] {
        switch mode {
        case .major: [.major, .minor, .minor, .major, .major, .minor, .diminished]
        case .minor: [.minor, .diminished, .major, .minor, .major, .major, .major]
        }
    }

    private static func numerals(_ mode: Mode) -> [String] {
        switch mode {
        case .major: ["I", "ii", "iii", "IV", "V", "vi", "vii°"]
        case .minor: ["i", "ii°", "III", "iv", "V", "VI", "VII"]
        }
    }
}

/// A sequence of diatonic chords in one mode, e.g. I – vi – IV – V.
struct ChordProgression: Hashable {
    let chords: [DiatonicChord]

    /// Bass roots fall in the twelve semitones from 16 below the tonic up to 5 below it.
    private static let bassLowest = -16

    /// Upper voices fall in the twelve semitones from 3 below the tonic up to 8 above it.
    private static let upperLowest = -3

    init(_ chords: [DiatonicChord]) {
        precondition(!chords.isEmpty, "A progression needs at least one chord")
        precondition(chords.allSatisfy { $0.mode == chords[0].mode }, "All chords must be in the same mode")
        self.chords = chords
    }

    var mode: Mode { chords[0].mode }

    /// e.g. "I – vi – IV – V".
    var label: String { chords.map(\.numeral).joined(separator: " – ") }

    /// e.g. "C – Am – F – G" in C major.
    func symbols(in key: Key) -> String { chords.map { $0.symbol(in: key) }.joined(separator: " – ") }

    /**
     * Keyboard voicing in [key]: each chord's root in the bass below the tonic, and its three
     * tones in the same one-octave window above. Keeping every chord in one window makes common
     * tones stay put and other voices move by small steps, much like a pianist would play it.
     */
    func voiced(in key: Key) -> [[Pitch]] {
        chords.map { chord in
            let bass = key.tonic.transposed(by: Self.bassLowest + positiveMod(chord.rootSemitonesAboveTonic - Self.bassLowest, 12))
            let upper = chord.toneSemitones
                .map { key.tonic.transposed(by: Self.upperLowest + positiveMod($0 - Self.upperLowest, 12)) }
                .sorted()
            return [bass] + upper
        }
    }
}
