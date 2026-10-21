import Foundation

/// How the two notes of an interval are presented.
enum IntervalStyle: String, StoredEnum {
    case melodicAscending = "MELODIC_ASCENDING"
    case melodicDescending = "MELODIC_DESCENDING"
    case harmonic = "HARMONIC"

    var displayName: String {
        switch self {
        case .melodicAscending: "Ascending melodic"
        case .melodicDescending: "Descending melodic"
        case .harmonic: "Harmonic"
        }
    }
}

struct IntervalQuestion: ExerciseQuestion {
    let lowerPitch: Pitch
    let interval: Interval
    let style: IntervalStyle
    let choices: [Interval]

    init(lowerPitch: Pitch, interval: Interval, style: IntervalStyle, choices: [Interval]) {
        requireValidChoices(choices, interval)
        precondition(Pitch.midiRange.contains(lowerPitch.midi + interval.semitones), "\(interval.displayName) above \(lowerPitch) is outside the MIDI range")
        self.lowerPitch = lowerPitch
        self.interval = interval
        self.style = style
        self.choices = choices
    }

    var upperPitch: Pitch { lowerPitch.transposed(by: interval.semitones) }

    var correctAnswer: Interval { interval }

    var prompt: ListeningPrompt {
        switch style {
        case .melodicAscending: .sequential([lowerPitch, upperPitch])
        case .melodicDescending: .sequential([upperPitch, lowerPitch])
        case .harmonic: .simultaneous([lowerPitch, upperPitch])
        }
    }
}

struct IntervalExerciseSettings: Equatable {
    var intervals: [Interval] = Interval.allCases
    var styles: Set<IntervalStyle> = [.melodicAscending]
    /// Every sounded pitch stays within this range. Default C3..C6 is comfortable on small speakers.
    var pitchRange: ClosedRange<Int> = 48...84
    var choiceCount = 6
    /// Ask more often about intervals recently answered wrongly.
    var focusOnWeakSpots = true

    /// Fewer than two intervals would make every question a giveaway.
    static let minIntervals = 2

    /// Choice counts offered in settings; 12 shows every interval.
    static let choiceCountOptions = [4, 6, 8, 12]

    var isValid: Bool {
        intervals.count >= Self.minIntervals &&
            Set(intervals).count == intervals.count &&
            !styles.isEmpty &&
            Pitch.midiRange.contains(pitchRange.lowerBound) && Pitch.midiRange.contains(pitchRange.upperBound) &&
            pitchRange.upperBound - pitchRange.lowerBound >= (intervals.map(\.semitones).max() ?? 0) &&
            choiceCount >= 2
    }

    /// Adds or removes [interval], refusing to drop below `minIntervals`. Keeps size order.
    func withIntervalToggled(_ interval: Interval) -> IntervalExerciseSettings {
        var copy = self
        if !intervals.contains(interval) {
            copy.intervals = (intervals + [interval]).sorted { $0.semitones < $1.semitones }
        } else if intervals.count > Self.minIntervals {
            copy.intervals = intervals.filter { $0 != interval }
        }
        return copy
    }

    /// Adds or removes [style], refusing to remove the last one.
    func withStyleToggled(_ style: IntervalStyle) -> IntervalExerciseSettings {
        var copy = self
        if !styles.contains(style) {
            copy.styles.insert(style)
        } else if styles.count > 1 {
            copy.styles.remove(style)
        }
        return copy
    }
}

final class IntervalQuestionGenerator {
    private let settings: IntervalExerciseSettings
    private let random: RandomSource
    /// Relative likelihood of asking each interval; nil asks them all equally often.
    private let weightOf: ((Interval) -> Double)?
    private let answerPool: [Interval]

    init(settings: IntervalExerciseSettings = IntervalExerciseSettings(), random: RandomSource = .system, weightOf: ((Interval) -> Double)? = nil) {
        self.settings = settings
        self.random = random
        self.weightOf = weightOf
        answerPool = settings.intervals.sorted { $0.semitones < $1.semitones }
    }

    func generate() -> IntervalQuestion {
        let interval = weightOf.map { settings.intervals.weightedRandom(random, weightOf: $0) } ?? random.element(of: settings.intervals)
        let highestLowerNote = settings.pitchRange.upperBound - interval.semitones
        let lowerPitch = Pitch(random.int(in: settings.pitchRange.lowerBound...highestLowerNote))
        return IntervalQuestion(
            lowerPitch: lowerPitch,
            interval: interval,
            style: random.element(of: settings.styles.inCatalogOrder),
            choices: choices(for: interval)
        )
    }

    /**
     * Picks a contiguous run of intervals (by size) that contains the correct one. Neighboring
     * sizes make plausible distractors, and randomizing where the run starts keeps the correct
     * answer from always sitting in the same position.
     */
    private func choices(for correct: Interval) -> [Interval] {
        let count = min(settings.choiceCount, answerPool.count)
        let correctIndex = answerPool.firstIndex(of: correct)!
        let firstStart = max(0, correctIndex - count + 1)
        let lastStart = min(correctIndex, answerPool.count - count)
        let start = random.int(in: firstStart...lastStart)
        return Array(answerPool[start..<(start + count)])
    }
}
