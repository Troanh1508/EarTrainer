import Foundation

/**
 * Up-to-date selection weights per exercise, derived from recent history. They refresh by
 * themselves whenever a new answer is saved, and question generators read the latest value
 * each time they pick a question. Loading starts when this is created, so weights are ready
 * before the first question of a session.
 */
final class WeakSpots: @unchecked Sendable {
    /**
     * Only recent answers count, so weights follow current ability: an item that used to be
     * hard but is now answered reliably stops being favored.
     */
    static let recentAnswers = 300

    private let history: PracticeHistory
    private let lock = NSLock()
    private var intervals: [Interval: Double] = [:]
    private var chords: [ChordAnswer: Double] = [:]
    private var scaleDegrees: [ScaleDegree: Double] = [:]
    private var progressionChords: [DiatonicChord: Double] = [:]
    private var dictationDegrees: [ScaleDegree: Double] = [:]
    private var rhythmCells: [RhythmCell: Double] = [:]
    private var observer: NSObjectProtocol?

    init(history: PracticeHistory) {
        self.history = history
        refresh()
        observer = NotificationCenter.default.addObserver(forName: .practiceHistoryDidChange, object: nil, queue: .main) { [weak self] _ in
            self?.refresh()
        }
    }

    /*
     * Lookups for generators: an item's weight, or the unseen weight if it has no history yet.
     */

    var intervalWeight: (Interval) -> Double { { [unowned self] item in weight(\.intervals, item) } }
    var chordWeight: (ChordAnswer) -> Double { { [unowned self] item in weight(\.chords, item) } }
    var scaleDegreeWeight: (ScaleDegree) -> Double { { [unowned self] item in weight(\.scaleDegrees, item) } }
    var progressionChordWeight: (DiatonicChord) -> Double { { [unowned self] item in weight(\.progressionChords, item) } }
    var dictationDegreeWeight: (ScaleDegree) -> Double { { [unowned self] item in weight(\.dictationDegrees, item) } }
    var rhythmCellWeight: (RhythmCell) -> Double { { [unowned self] item in weight(\.rhythmCells, item) } }

    private func weight<T: Hashable>(_ keyPath: KeyPath<WeakSpots, [T: Double]>, _ item: T) -> Double {
        lock.withLock { self[keyPath: keyPath][item] ?? WeakSpotWeighting.unseenWeight }
    }

    private func refresh() {
        let history = history
        let limit = Self.recentAnswers
        Task.detached { [weak self] in
            let intervals = WeakSpotWeighting.weights(await history.recentIntervalResults(limit: limit))
            let chords = WeakSpotWeighting.weights(await history.recentChordResults(limit: limit))
            let scaleDegrees = WeakSpotWeighting.weights(await history.recentScaleDegreeResults(limit: limit))
            let progressionChords = WeakSpotWeighting.weights(await history.recentProgressionResults(limit: limit))
            let dictationDegrees = WeakSpotWeighting.weights(await history.recentDictationResults(limit: limit))
            let rhythmCells = WeakSpotWeighting.weights(await history.recentRhythmResults(limit: limit))
            guard let self else { return }
            self.lock.withLock {
                self.intervals = intervals
                self.chords = chords
                self.scaleDegrees = scaleDegrees
                self.progressionChords = progressionChords
                self.dictationDegrees = dictationDegrees
                self.rhythmCells = rhythmCells
            }
        }
    }
}
