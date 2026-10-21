import Foundation

/// Turns scheduled notes into mono PCM in -1...1. Implementations decide what the notes sound like.
protocol PcmRenderer: AnyObject {
    func render(_ schedule: NoteSchedule) -> [Float]
}

/// Smooth 0 → 1 ramp for x in [0, 1]; used for click-free fades.
func raisedCosine(_ x: Double) -> Double { 0.5 - 0.5 * cos(Double.pi * x) }

/// Clamps a mix so anything that overshoots full scale stays in range.
func clampedToFullScale(_ mix: [Double]) -> [Float] { mix.map { Float(min(1, max(-1, $0))) } }

/**
 * Amplitude shape of a single note. Raised-cosine attack and release start and end exactly at
 * zero, which is what keeps note boundaries free of clicks. Between them the level decays
 * gently toward `sustainLevel`, giving a softer, more instrument-like tone than a flat sine.
 */
struct ToneEnvelope: Hashable {
    var attackSeconds = 0.012
    var decaySeconds = 0.45
    var sustainLevel = 0.55
    var releaseSeconds = 0.08

    func gainAt(frame: Int, noteFrames: Int, sampleRate: Int) -> Double {
        guard frame >= 0, frame < noteFrames else { return 0 }
        // Very short notes split the available frames between attack and release.
        let attackFrames = max(1, min(Int((attackSeconds * Double(sampleRate)).rounded()), noteFrames / 2))
        let releaseFrames = max(1, min(Int((releaseSeconds * Double(sampleRate)).rounded()), noteFrames - attackFrames))

        let seconds = Double(frame) / Double(sampleRate)
        let body = sustainLevel + (1 - sustainLevel) * exp(-seconds / decaySeconds)
        let attack = frame < attackFrames ? raisedCosine(Double(frame) / Double(attackFrames)) : 1
        let framesLeft = noteFrames - 1 - frame
        let release = framesLeft < releaseFrames ? raisedCosine(Double(framesLeft) / Double(releaseFrames)) : 1
        return body * attack * release
    }
}

/**
 * Renders a `NoteSchedule` with a few sine partials. Used when the sampled instruments cannot be
 * loaded, so practice always has sound.
 */
final class ToneSynthesizer: PcmRenderer {
    /**
     * Relative amplitudes of the fundamental and its overtones. A pure sine is hard to judge
     * in harmonic intervals; a few weak overtones make consonance and dissonance clearer.
     */
    private let harmonics: [Double]
    private let envelope: ToneEnvelope
    /// Peak level (0...1) when `maxPolyphony` voices are at full amplitude.
    private let headroom: Double

    init(harmonics: [Double] = [1.0, 0.25, 0.1], envelope: ToneEnvelope = ToneEnvelope(), headroom: Double = 0.8) {
        precondition(!harmonics.isEmpty && harmonics.allSatisfy { $0 >= 0 } && harmonics.reduce(0, +) > 0,
                     "Harmonic amplitudes must be non-negative with at least one positive value")
        precondition(headroom > 0 && headroom <= 1, "Headroom must be in (0, 1]")
        self.harmonics = harmonics
        self.envelope = envelope
        self.headroom = headroom
    }

    func render(_ schedule: NoteSchedule) -> [Float] {
        let sampleRate = schedule.sampleRate
        var mix = [Double](repeating: 0, count: schedule.totalFrames)
        // Dividing by polyphony keeps chords at the same overall loudness as single notes and
        // guarantees the mix can never exceed full scale.
        let voiceGain = headroom / Double(schedule.maxPolyphony) / harmonics.reduce(0, +)
        let nyquist = Double(sampleRate) / 2

        mix.withUnsafeMutableBufferPointer { buffer in
            for note in schedule.notes {
                let fundamental = note.pitch.frequencyHz
                // Overtones at or above Nyquist would alias into audible, inharmonic tones.
                let partials = harmonics.enumerated().filter { index, amplitude in amplitude > 0 && fundamental * Double(index + 1) < nyquist }
                let multipliers = partials.map { Double($0.offset + 1) }
                let amplitudes = partials.map(\.element)
                let radiansPerFrame = 2 * Double.pi * fundamental / Double(sampleRate)

                for i in 0..<note.frameCount {
                    let target = note.startFrame + i
                    if target >= buffer.count { break }
                    let phase = radiansPerFrame * Double(i)
                    var sample = 0.0
                    for p in multipliers.indices { sample += amplitudes[p] * sin(phase * multipliers[p]) }
                    buffer[target] += sample * voiceGain * envelope.gainAt(frame: i, noteFrames: note.frameCount, sampleRate: sampleRate)
                }
            }
        }
        return clampedToFullScale(mix)
    }
}
