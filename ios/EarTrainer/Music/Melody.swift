import Foundation

/// A tune written as scale degrees, e.g. 1 – 2 – 3 – 1, independent of key.
struct Melody: Hashable {
    let degrees: [ScaleDegree]

    init(_ degrees: [ScaleDegree]) {
        precondition(!degrees.isEmpty, "A melody needs at least one note")
        self.degrees = degrees
    }

    /// e.g. "1 – 2 – ♭3 – 1".
    var label: String { degrees.map(\.label).joined(separator: " – ") }

    /// e.g. "Do Re Me Do".
    var solfege: String { degrees.map(\.solfege).joined(separator: " ") }

    /// The notes in [key], each in the octave from the tonic up.
    func pitches(in key: Key) -> [Pitch] { degrees.map(key.pitchOf) }
}
