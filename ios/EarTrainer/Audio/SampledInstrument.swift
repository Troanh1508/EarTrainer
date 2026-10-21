import AVFoundation

/// One recorded note: mono PCM in -1...1 and the MIDI pitch it was recorded at.
final class InstrumentSample {
    let rootMidi: Int
    let sampleRate: Int
    let pcm: [Float]
    /// Loudest absolute value; recordings are scaled by it so every note plays at the same level.
    let peak: Float

    init(rootMidi: Int, sampleRate: Int, pcm: [Float]) {
        precondition(sampleRate > 0, "Sample rate must be positive")
        precondition(pcm.count >= 4, "A sample needs at least four frames for interpolation")
        self.rootMidi = rootMidi
        self.sampleRate = sampleRate
        self.pcm = pcm
        peak = max(pcm.map(abs).max() ?? 0, 1.0 / 32768)
    }

    /// Last position `value(at:)` can read, leaving room for the two frames ahead it needs.
    var lastReadablePosition: Int { pcm.count - 3 }
}

/// Recordings covering a pitch range; each note is played from the closest recording.
final class SampleBank {
    private let samples: [InstrumentSample]

    init(_ samples: [InstrumentSample]) {
        precondition(!samples.isEmpty, "A sample bank needs at least one recording")
        precondition(Set(samples.map(\.rootMidi)).count == samples.count, "Each recording needs a distinct root pitch")
        self.samples = samples.sorted { $0.rootMidi < $1.rootMidi }
    }

    var rootPitches: [Int] { samples.map(\.rootMidi) }

    /// Nearest recording by pitch; on a tie the lower one wins.
    func sample(for midi: Int) -> InstrumentSample {
        samples.min { abs($0.rootMidi - midi) < abs($1.rootMidi - midi) }!
    }
}

/**
 * Plays notes from recorded samples. Each note reads the nearest recording at a rate that both
 * shifts it to the requested pitch and converts its sample rate to the output rate, so one
 * recording serves the neighboring semitones.
 */
final class SampledInstrumentRenderer: PcmRenderer {
    private let bank: SampleBank
    /// Damper-like fade at the end of each note; shorter than a real piano so notes stay distinct.
    private let releaseSeconds: Double
    /// Peak level (0...1) when `maxPolyphony` voices are at full amplitude.
    private let headroom: Double

    /**
     * Recordings start at the hammer strike, so only a tiny fade-in is needed to guarantee the
     * first frame is exactly zero without softening the attack.
     */
    private static let fadeInSeconds = 0.002

    init(bank: SampleBank, releaseSeconds: Double = 0.25, headroom: Double = 0.8) {
        precondition(releaseSeconds > 0, "Release must be positive")
        precondition(headroom > 0 && headroom <= 1, "Headroom must be in (0, 1]")
        self.bank = bank
        self.releaseSeconds = releaseSeconds
        self.headroom = headroom
    }

    func render(_ schedule: NoteSchedule) -> [Float] {
        let outputRate = Double(schedule.sampleRate)
        var mix = [Double](repeating: 0, count: schedule.totalFrames)
        // Same gain staging as the synthesizer: normalized recordings divided across voices can
        // never sum past full scale.
        let voiceGain = headroom / Double(schedule.maxPolyphony)
        let fadeInFrames = max(1, Int((Self.fadeInSeconds * outputRate).rounded()))

        mix.withUnsafeMutableBufferPointer { buffer in
            for note in schedule.notes {
                let sample = bank.sample(for: note.pitch.midi)
                let step = pow(2.0, Double(note.pitch.midi - sample.rootMidi) / 12) * Double(sample.sampleRate) / outputRate
                // A note ends early if its recording runs out; the release then fades the tail instead
                // of cutting it off mid-waveform.
                let playable = Int(Double(sample.lastReadablePosition) / step)
                let length = min(note.frameCount, playable, buffer.count - note.startFrame)
                if length <= 0 { continue }
                let releaseFrames = min(max(1, Int((releaseSeconds * outputRate).rounded())), length)
                let gain = voiceGain / Double(sample.peak)

                sample.pcm.withUnsafeBufferPointer { pcm in
                    for i in 0..<length {
                        let fadeIn = i < fadeInFrames ? raisedCosine(Double(i) / Double(fadeInFrames)) : 1
                        let framesLeft = length - 1 - i
                        let release = framesLeft < releaseFrames ? raisedCosine(Double(framesLeft) / Double(releaseFrames)) : 1
                        buffer[note.startFrame + i] += Self.value(pcm, at: Double(i) * step) * gain * fadeIn * release
                    }
                }
            }
        }
        return clampedToFullScale(mix)
    }

    /**
     * Value at a fractional frame position, using 4-point Catmull-Rom interpolation. It keeps
     * noticeably more high-frequency detail than linear interpolation when a recording is
     * pitch-shifted or resampled to the output rate.
     */
    private static func value(_ pcm: UnsafeBufferPointer<Float>, at position: Double) -> Double {
        let index = Int(position)
        let t = position - Double(index)
        let y0 = Double(pcm[max(index - 1, 0)])
        let y1 = Double(pcm[index])
        let y2 = Double(pcm[index + 1])
        let y3 = Double(pcm[index + 2])
        let c1 = 0.5 * (y2 - y0)
        let c2 = y0 - 2.5 * y1 + 2 * y2 - 0.5 * y3
        let c3 = 0.5 * (y3 - y0) + 1.5 * (y1 - y2)
        return ((c3 * t + c2) * t + c1) * t + y1
    }
}
