import Foundation
import os

/// Hand-wired app-wide dependencies; small enough that a DI framework would add nothing.
@MainActor
final class AppContainer {
    static let shared = AppContainer()

    let history: PracticeHistory

    /// Selection weights for weak-spot practice, kept current from history from app start.
    let weakSpots: WeakSpots

    let settings = SettingsStorage()
    let intervalSettings: SettingsStore<IntervalExerciseSettings>
    let chordSettings: SettingsStore<ChordExerciseSettings>
    let scaleDegreeSettings: SettingsStore<ScaleDegreeSettings>
    let progressionSettings: SettingsStore<ProgressionSettings>
    let dictationSettings: SettingsStore<DictationSettings>
    let rhythmSettings: SettingsStore<RhythmSettings>
    let soundSettings: SettingsStore<SoundSettings>
    let goalSettings: SettingsStore<GoalSettings>

    /// Export and import of history and settings.
    let backup: DataBackup

    private(set) lazy var audioEngine = PromptAudioEngine { [unowned self] in
        await self.renderer(for: self.soundSettings.current.instrument)
    }

    /**
     * Each instrument is decoded at most once per launch, in the background, and shared by every
     * player. Only instruments the user picks are decoded.
     */
    private var instruments: [Instrument: Task<PcmRenderer, Never>] = [:]
    private var settingsObserver: NSObjectProtocol?
    private let log = Logger(subsystem: "EarTrainer", category: "AppContainer")

    private init() {
        let history: PracticeHistory
        do {
            history = try PracticeHistory.openDefault()
        } catch {
            // Practice still works; this launch's answers are kept in memory only.
            Logger(subsystem: "EarTrainer", category: "AppContainer").error("Could not open history: \(String(describing: error))")
            history = try! PracticeHistory(database: SQLiteDatabase.inMemory())
        }
        self.history = history
        weakSpots = WeakSpots(history: history)
        intervalSettings = settings.store(IntervalSettingsCodec.decode, IntervalSettingsCodec.encode)
        chordSettings = settings.store(ChordSettingsCodec.decode, ChordSettingsCodec.encode)
        scaleDegreeSettings = settings.store(ScaleDegreeSettingsCodec.decode, ScaleDegreeSettingsCodec.encode)
        progressionSettings = settings.store(ProgressionSettingsCodec.decode, ProgressionSettingsCodec.encode)
        dictationSettings = settings.store(DictationSettingsCodec.decode, DictationSettingsCodec.encode)
        rhythmSettings = settings.store(RhythmSettingsCodec.decode, RhythmSettingsCodec.encode)
        soundSettings = settings.store(SoundSettingsCodec.decode, SoundSettingsCodec.encode)
        goalSettings = settings.store(GoalSettingsCodec.decode, GoalSettingsCodec.encode)
        backup = DataBackup(history: history, settings: settings)
    }

    /**
     * Decodes the chosen instrument at launch, and a newly chosen one as soon as it is picked,
     * so it is ready before the next prompt plays.
     */
    func preloadInstrument() {
        _ = rendererTask(for: soundSettings.current.instrument)
        settingsObserver = NotificationCenter.default.addObserver(forName: .settingsDidChange, object: nil, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated {
                guard let self else { return }
                _ = self.rendererTask(for: self.soundSettings.current.instrument)
            }
        }
    }

    /// Each exercise screen owns and releases its own player; every prompt uses the current instrument.
    func makeAudioPlayer() -> AudioPlayer { EngineAudioPlayer(engine: audioEngine) }

    private func renderer(for instrument: Instrument) async -> PcmRenderer {
        await rendererTask(for: instrument).value
    }

    private func rendererTask(for instrument: Instrument) -> Task<PcmRenderer, Never> {
        if let task = instruments[instrument] { return task }
        let log = log
        let task = Task.detached(priority: .userInitiated) { () -> PcmRenderer in
            do {
                return SampledInstrumentRenderer(bank: try SampleLoader.load(instrument))
            } catch {
                // The app still works, using the built-in synthesizer instead.
                log.warning("\(instrument.displayName) samples could not be loaded; using the synthesizer: \(String(describing: error))")
                return ToneSynthesizer()
            }
        }
        instruments[instrument] = task
        return task
    }
}
