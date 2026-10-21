import AVFoundation

/**
 * The sampled instruments prompts can be played with. Both come from the FreePats project under
 * CC0 (public domain); see the README and license next to each set of recordings in
 * shared/assets, which both apps bundle as they are.
 */
enum Instrument: String, StoredEnum {
    /// "Upright Piano KW" (small), recorded roughly every minor third; roots from its SFZ map.
    case piano = "PIANO"

    /**
     * "Spanish Classical Guitar" (nylon strings): a subset of its recordings, about every three
     * semitones over the guitar's range (G1 to C6), so no note is shifted more than a semitone
     * and a half. Notes above the range, such as the rhythm count-in, are shifted further.
     */
    case guitar = "GUITAR"

    var displayName: String {
        switch self {
        case .piano: "Piano"
        case .guitar: "Guitar"
        }
    }

    /// Folder of the recordings inside the bundled "instruments" folder.
    var directory: String {
        switch self {
        case .piano: "upright-piano-kw"
        case .guitar: "spanish-classical-guitar"
        }
    }

    /// File names (without ".flac") and the MIDI pitch each was recorded at.
    var recordings: [(name: String, rootMidi: Int)] {
        switch self {
        case .piano:
            [
                ("C1", 24), ("Fs1", 30), ("B1", 35), ("Ds2", 39), ("Fs2", 42), ("B2", 47),
                ("Ds3", 51), ("Fs3", 54), ("A3", 57), ("C4", 60), ("Ds4", 63), ("Fs4", 66),
                ("A4", 69), ("C5", 72), ("Ds5", 75), ("Fs5", 78), ("A5", 81), ("C6", 84),
                ("Ds6", 87), ("Fs6", 90), ("A6", 93), ("C7", 96), ("Ds7", 99), ("Fs7", 102),
                ("A7", 105), ("B7", 107),
            ]
        case .guitar:
            [
                ("G1", 31), ("As1", 34), ("Cs2", 37), ("E2", 40), ("G2", 43), ("A2", 45),
                ("C3", 48), ("D3", 50), ("E3", 52), ("G3", 55), ("A3", 57), ("C4", 60),
                ("Ds4", 63), ("Fs4", 66), ("A4", 69), ("C5", 72), ("Ds5", 75), ("Fs5", 78),
                ("A5", 81), ("C6", 84),
            ]
        }
    }
}

/// App-wide sound preferences; unlike exercise settings they apply to every exercise.
struct SoundSettings: Equatable {
    var instrument: Instrument = .piano
}

enum SampleLoaderError: Error {
    case missingRecording(String)
    case unreadable(String)
}

/// Decodes the bundled FLAC recordings into PCM with Core Audio.
enum SampleLoader {

    /**
     * Decodes the recordings of [instrument]. Only the first [maxSeconds] of each recording are kept:
     * prompts never hold a note that long, and it keeps the decoded bank to a few megabytes.
     */
    static func load(_ instrument: Instrument, bundle: Bundle = .main, maxSeconds: Double = 3.0) throws -> SampleBank {
        guard let base = bundle.resourceURL?.appendingPathComponent("instruments").appendingPathComponent(instrument.directory) else {
            throw SampleLoaderError.missingRecording(instrument.directory)
        }
        let recordings = instrument.recordings
        var results = [Result<InstrumentSample, Error>?](repeating: nil, count: recordings.count)
        let lock = NSLock()
        // Decoding is mostly file and codec start-up, so a few in parallel cut the load time.
        DispatchQueue.concurrentPerform(iterations: recordings.count) { index in
            let recording = recordings[index]
            let url = base.appendingPathComponent(recording.name + ".flac")
            let result = Result { try decode(url, rootMidi: recording.rootMidi, maxSeconds: maxSeconds) }
            lock.lock()
            results[index] = result
            lock.unlock()
        }
        return SampleBank(try results.map { try $0!.get() })
    }

    /// Reads up to [maxSeconds] of [url] as mono, averaging channels if the recording is not mono.
    static func decode(_ url: URL, rootMidi: Int, maxSeconds: Double) throws -> InstrumentSample {
        guard FileManager.default.fileExists(atPath: url.path) else { throw SampleLoaderError.missingRecording(url.lastPathComponent) }
        let file = try AVAudioFile(forReading: url)
        let format = file.processingFormat
        let frames = AVAudioFrameCount(min(Double(file.length), maxSeconds * format.sampleRate))
        guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frames) else {
            throw SampleLoaderError.unreadable(url.lastPathComponent)
        }
        try file.read(into: buffer, frameCount: frames)
        guard let channels = buffer.floatChannelData else { throw SampleLoaderError.unreadable(url.lastPathComponent) }
        let channelCount = Int(format.channelCount)
        let length = Int(buffer.frameLength)
        var mono = [Float](repeating: 0, count: length)
        for channel in 0..<channelCount {
            let data = channels[channel]
            for i in 0..<length { mono[i] += data[i] / Float(channelCount) }
        }
        return InstrumentSample(rootMidi: rootMidi, sampleRate: Int(format.sampleRate), pcm: mono)
    }
}
