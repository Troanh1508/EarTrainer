import Foundation

/// A pitch identified by its MIDI note number (middle C = 60, concert A = 69).
struct Pitch: Hashable, Comparable, CustomStringConvertible {
    static let midiRange = 0...127
    static let concertAMidi = 69
    static let concertAHz = 440.0
    static let middleC = Pitch(60)

    private static let pitchClassNames = ["C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B"]

    let midi: Int

    init(_ midi: Int) {
        precondition(Pitch.midiRange.contains(midi), "MIDI note \(midi) is outside \(Pitch.midiRange)")
        self.midi = midi
    }

    /// Twelve-tone equal temperament frequency, tuned to concert A.
    var frequencyHz: Double { Pitch.frequency(ofMidi: midi) }

    /// Scientific pitch notation using sharps, e.g. "C4", "F♯3".
    var name: String { pitchClassName + String(octave) }

    /// Note name without octave, e.g. "F♯".
    var pitchClassName: String { Pitch.pitchClassNames[midi % 12] }

    /// Scientific pitch octave; MIDI 60 is C4.
    var octave: Int { midi / 12 - 1 }

    func transposed(by semitones: Int) -> Pitch { Pitch(midi + semitones) }

    var description: String { name }

    static func < (lhs: Pitch, rhs: Pitch) -> Bool { lhs.midi < rhs.midi }

    static func frequency(ofMidi midi: Int, concertA: Double = concertAHz) -> Double {
        concertA * pow(2.0, Double(midi - concertAMidi) / 12.0)
    }
}
