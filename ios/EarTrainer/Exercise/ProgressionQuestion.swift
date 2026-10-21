import Foundation

/// Which chords progressions may use, by scale step.
enum ChordVocabulary: String, StoredEnum {
    case primary = "PRIMARY"
    case common = "COMMON"
    case all = "ALL"

    var displayName: String {
        switch self {
        case .primary: "I, IV and V"
        case .common: "Add ii and vi"
        case .all: "All seven chords"
        }
    }

    var steps: Set<Int> {
        switch self {
        case .primary: [1, 4, 5]
        case .common: [1, 2, 4, 5, 6]
        case .all: Set(1...7)
        }
    }
}

/// Where questions come from.
enum ProgressionSource: String, StoredEnum {
    /// Well-known progressions from popular and classical music.
    case common = "COMMON"
    /// Any order of the allowed chords, including unusual ones.
    case random = "RANDOM"

    var displayName: String {
        switch self {
        case .common: "Common progressions"
        case .random: "Random"
        }
    }
}

struct ProgressionQuestion: ExerciseQuestion {
    let key: Key
    let progression: ChordProgression
    let choices: [ChordProgression]

    init(key: Key, progression: ChordProgression, choices: [ChordProgression]) {
        precondition(progression.mode == key.mode, "The progression must be in the key's mode")
        requireValidChoices(choices, progression)
        self.key = key
        self.progression = progression
        self.choices = choices
    }

    var correctAnswer: ChordProgression { progression }

    var prompt: ListeningPrompt { ListeningPrompt(moments: progression.voiced(in: key), articulation: .flowing) }
}

struct ProgressionSettings: Equatable {
    var modes: Set<Mode> = [.major]
    var vocabulary: ChordVocabulary = .common
    var length = 4
    var keyChoice: KeyChoice = .random
    var source: ProgressionSource = .common
    /// Ask more often about progressions containing chords recently identified wrongly.
    var focusOnWeakSpots = true

    static let lengthOptions = [3, 4, 5]

    /// Multiple-choice options per question, including the correct one.
    static let choiceCount = 4

    var isValid: Bool { !modes.isEmpty && Self.lengthOptions.contains(length) }

    /// Adds or removes [mode], refusing to remove the last one.
    func withModeToggled(_ mode: Mode) -> ProgressionSettings {
        var copy = self
        if !modes.contains(mode) {
            copy.modes.insert(mode)
        } else if modes.count > 1 {
            copy.modes.remove(mode)
        }
        return copy
    }
}

final class ProgressionQuestionGenerator {
    private let settings: ProgressionSettings
    private let random: RandomSource
    /// Relative likelihood of asking about each chord; nil treats all progressions equally.
    private let weightOf: ((DiatonicChord) -> Double)?

    /// Every allowed progression per mode, computed once; at most 7^4 = 2,401 per mode.
    private let catalog: [Mode: [ChordProgression]]

    /// The common progressions that fit the settings, per mode; empty in random mode.
    private let common: [Mode: [ChordProgression]]

    init(settings: ProgressionSettings = ProgressionSettings(), random: RandomSource = .system, weightOf: ((DiatonicChord) -> Double)? = nil) {
        self.settings = settings
        self.random = random
        self.weightOf = weightOf
        var catalog: [Mode: [ChordProgression]] = [:]
        var common: [Mode: [ChordProgression]] = [:]
        for mode in settings.modes {
            catalog[mode] = Self.allProgressions(mode, settings.vocabulary, settings.length)
            common[mode] = settings.source == .common ? Self.commonProgressions(mode, settings.vocabulary, settings.length) : []
        }
        self.catalog = catalog
        self.common = common
    }

    func generate() -> ProgressionQuestion {
        let mode = random.element(of: settings.modes.inCatalogOrder)
        let tonic = settings.keyChoice.pickTonic(random)
        let everything = catalog[mode]!
        // Falls back to random progressions if no common one fits the chosen chords and length.
        let preferred = common[mode]!.isEmpty ? everything : common[mode]!
        // A progression is as likely as its chords are weak on average; the opening tonic is given.
        let correct: ChordProgression
        if let weightOf {
            correct = preferred.weightedRandom(random) { progression in
                let weights = progression.chords.dropFirst().map(weightOf)
                return weights.reduce(0, +) / Double(weights.count)
            }
        } else {
            correct = random.element(of: preferred)
        }
        return ProgressionQuestion(key: Key(tonic: tonic, mode: mode), progression: correct, choices: choices(for: correct, preferred, everything))
    }

    /**
     * The correct progression plus the closest alternatives: those differing in the fewest
     * chords (ties broken randomly). Near-misses make the listener follow every chord rather
     * than just the overall shape. Alternatives come from [preferred] first (common progressions
     * when enabled) and only then from [everything]. Shuffled so the answer's position carries
     * no clue.
     */
    private func choices(for correct: ChordProgression, _ preferred: [ChordProgression], _ everything: [ChordProgression]) -> [ChordProgression] {
        let needed = ProgressionSettings.choiceCount - 1
        let fromPreferred = Array(closest(correct, preferred.filter { $0 != correct }).prefix(needed))
        let taken = Set(fromPreferred)
        let fromEverything = closest(correct, everything.filter { $0 != correct && !taken.contains($0) }).prefix(needed - fromPreferred.count)
        return random.shuffled(fromPreferred + fromEverything + [correct])
    }

    private func closest(_ correct: ChordProgression, _ candidates: [ChordProgression]) -> [ChordProgression] {
        func distance(_ candidate: ChordProgression) -> Int {
            candidate.chords.indices.filter { candidate.chords[$0] != correct.chords[$0] }.count
        }
        // Shuffled first, then a stable sort, so equally close candidates come in random order.
        return random.shuffled(candidates).enumerated()
            .sorted { (distance($0.element), $0.offset) < (distance($1.element), $1.offset) }
            .map(\.element)
    }

    /**
     * Progressions that start on the tonic chord (so the key is heard first) and never repeat
     * a chord back to back (which would sound like one long chord).
     */
    static func allProgressions(_ mode: Mode, _ vocabulary: ChordVocabulary, _ length: Int) -> [ChordProgression] {
        let chords = DiatonicChord.all(mode).filter { vocabulary.steps.contains($0.step) }
        var sequences = [[DiatonicChord(mode, 1)]]
        for _ in 0..<(length - 1) {
            sequences = sequences.flatMap { sequence in chords.filter { $0 != sequence.last }.map { sequence + [$0] } }
        }
        return sequences.map(ChordProgression.init)
    }

    /// Common progressions in [mode] with [length] chords that use only the [vocabulary].
    static func commonProgressions(_ mode: Mode, _ vocabulary: ChordVocabulary, _ length: Int) -> [ChordProgression] {
        CommonProgressions.forMode(mode, length: length).filter { progression in
            progression.chords.allSatisfy { vocabulary.steps.contains($0.step) }
        }
    }
}
