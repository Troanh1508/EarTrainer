import AVFoundation

enum AudioPlaybackError: Error {
    /// The audio session or output could not be used, e.g. during a phone call.
    case outputUnavailable(Error?)
    case released
}

/**
 * Plays listening prompts. Implementations decide how pitches sound; callers only describe what
 * to play.
 */
@MainActor
protocol AudioPlayer: AnyObject {
    /**
     * Plays [prompt] and returns once it has finished sounding. Canceling the calling task stops
     * the sound immediately. Starting a new prompt silences any prompt that is still playing, so
     * two prompts never overlap.
     */
    func play(_ prompt: ListeningPrompt) async throws

    /// Stops any playback of this player. The player must not be used afterwards.
    func release()
}

/**
 * The app's one audio output: an `AVAudioEngine` with a single player node.
 *
 * Each prompt is rendered into one PCM buffer up front and scheduled in a single call. Note
 * onsets and gaps are therefore fixed by sample positions in that buffer and played out by the
 * audio hardware clock, independent of the main thread, task scheduling or UIKit layout.
 *
 * Like transient audio focus on Android, the session is activated for each prompt (pausing other
 * apps' audio) and given back shortly after the last prompt ends.
 */
@MainActor
final class PromptAudioEngine {
    private struct RenderedPrompt {
        let prompt: ListeningPrompt
        let renderer: ObjectIdentifier
        let sampleRate: Double
        let samples: [Float]
    }

    private let engine = AVAudioEngine()
    private let node = AVAudioPlayerNode()
    private let timing: PlaybackTiming
    /// Supplies the current instrument; waits for a sampled instrument that is still loading.
    private let renderer: () async -> PcmRenderer
    private let sessionQueue = DispatchQueue(label: "EarTrainer.audio-session")

    private var connectedRate: Double = 0
    /// The prompt that owns the output; any other playback still finishing has been superseded.
    private var activeToken: UUID?
    private var activeOwner: ObjectIdentifier?
    private var deactivation: DispatchWorkItem?
    private var sessionConfigured = false

    // Replays of the same question reuse the rendered buffer instead of rendering again.
    private var lastRender: RenderedPrompt?

    /// Time the session stays active after a prompt, so "Next" and replays do not toggle it.
    private static let releaseDelay: TimeInterval = 1.5

    init(timing: PlaybackTiming = PlaybackTiming(), renderer: @escaping () async -> PcmRenderer) {
        self.timing = timing
        self.renderer = renderer
        engine.attach(node)
        let center = NotificationCenter.default
        center.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] notification in
            let type = (notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt).flatMap(AVAudioSession.InterruptionType.init)
            guard type == .began else { return }
            // A call or another app took the output; the prompt's play() then returns.
            MainActor.assumeIsolated { self?.silence() }
        }
        center.addObserver(forName: .AVAudioEngineConfigurationChange, object: engine, queue: .main) { [weak self] _ in
            // The output changed (e.g. headphones unplugged); the engine restarts on the next prompt.
            MainActor.assumeIsolated {
                self?.silence()
                self?.connectedRate = 0
            }
        }
    }

    func play(_ prompt: ListeningPrompt, owner: ObjectIdentifier) async throws {
        let token = UUID()
        // Claimed first, so a prompt that is still finishing never gives the session back.
        activeToken = token
        activeOwner = owner
        deactivation?.cancel()
        node.stop()

        try await activateSession()
        guard activeToken == token, !Task.isCancelled else { return }
        let sampleRate = try startEngine()
        let instrument = await renderer()
        let samples = await render(prompt, with: instrument, sampleRate: sampleRate)
        guard activeToken == token, !Task.isCancelled else { return }
        guard let buffer = Self.buffer(samples, sampleRate: sampleRate) else { throw AudioPlaybackError.outputUnavailable(nil) }

        await withTaskCancellationHandler {
            await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
                // Called once the buffer has been heard, or as soon as the node is stopped.
                node.scheduleBuffer(buffer, at: nil, options: [], completionCallbackType: .dataPlayedBack) { _ in
                    continuation.resume()
                }
                node.play()
            }
        } onCancel: {
            Task { @MainActor [weak self] in self?.stop(token) }
        }
        if activeToken == token {
            activeToken = nil
            activeOwner = nil
            scheduleDeactivation()
        }
    }

    /// Stops the current prompt if [owner] started it, e.g. when a screen closes.
    func stop(owner: ObjectIdentifier) {
        if activeOwner == owner { silence() }
    }

    private func stop(_ token: UUID) {
        if activeToken == token { silence() }
    }

    private func silence() {
        node.stop()
    }

    private func activateSession() async throws {
        let configure = !sessionConfigured
        sessionConfigured = true
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            sessionQueue.async {
                do {
                    let session = AVAudioSession.sharedInstance()
                    // Playback ignores the silent switch: an ear trainer is useless muted, like a music app.
                    if configure { try session.setCategory(.playback, mode: .default) }
                    try session.setActive(true)
                    continuation.resume()
                } catch {
                    continuation.resume(throwing: AudioPlaybackError.outputUnavailable(error))
                }
            }
        }
    }

    /// Starts the engine at the output's current rate and returns that rate.
    private func startEngine() throws -> Double {
        // Rendering at the output's native rate means the mixer plays our samples as they are.
        let outputRate = engine.outputNode.outputFormat(forBus: 0).sampleRate
        let sampleRate = outputRate > 0 ? outputRate : AVAudioSession.sharedInstance().sampleRate
        if sampleRate != connectedRate {
            if engine.isRunning { engine.stop() }
            guard let format = AVAudioFormat(standardFormatWithSampleRate: sampleRate, channels: 1) else {
                throw AudioPlaybackError.outputUnavailable(nil)
            }
            engine.connect(node, to: engine.mainMixerNode, format: format)
            connectedRate = sampleRate
        }
        if !engine.isRunning {
            engine.prepare()
            do {
                try engine.start()
            } catch {
                throw AudioPlaybackError.outputUnavailable(error)
            }
        }
        return sampleRate
    }

    private func render(_ prompt: ListeningPrompt, with instrument: PcmRenderer, sampleRate: Double) async -> [Float] {
        if let cached = lastRender, cached.prompt == prompt, cached.renderer == ObjectIdentifier(instrument), cached.sampleRate == sampleRate {
            return cached.samples
        }
        let schedule = NoteSchedule.from(prompt, sampleRate: Int(sampleRate), timing: timing)
        let samples = await Task.detached(priority: .userInitiated) { instrument.render(schedule) }.value
        lastRender = RenderedPrompt(prompt: prompt, renderer: ObjectIdentifier(instrument), sampleRate: sampleRate, samples: samples)
        return samples
    }

    private static func buffer(_ samples: [Float], sampleRate: Double) -> AVAudioPCMBuffer? {
        guard let format = AVAudioFormat(standardFormatWithSampleRate: sampleRate, channels: 1),
              let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(samples.count)),
              let channel = buffer.floatChannelData?[0] else { return nil }
        buffer.frameLength = AVAudioFrameCount(samples.count)
        samples.withUnsafeBufferPointer { channel.update(from: $0.baseAddress!, count: samples.count) }
        return buffer
    }

    /// Gives the output back to other apps once no prompt has played for a moment.
    private func scheduleDeactivation() {
        let work = DispatchWorkItem { [weak self] in
            guard let self, self.activeToken == nil else { return }
            self.engine.pause()
            self.sessionQueue.async {
                try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
            }
        }
        deactivation = work
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.releaseDelay, execute: work)
    }
}

/// A screen's handle on the shared output; releasing it silences only what this screen started.
@MainActor
final class EngineAudioPlayer: AudioPlayer {
    private let engine: PromptAudioEngine
    private var released = false

    init(engine: PromptAudioEngine) {
        self.engine = engine
    }

    func play(_ prompt: ListeningPrompt) async throws {
        if released { throw AudioPlaybackError.released }
        try await engine.play(prompt, owner: ObjectIdentifier(self))
    }

    func release() {
        released = true
        engine.stop(owner: ObjectIdentifier(self))
    }
}
