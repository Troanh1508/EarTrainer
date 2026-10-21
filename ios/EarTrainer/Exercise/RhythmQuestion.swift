import Foundation

/// Which rhythm cells questions may use, from simplest to hardest.
enum RhythmVocabulary: String, StoredEnum {
    case basic = "BASIC"
    case syncopation = "SYNCOPATION"
    case sixteenths = "SIXTEENTHS"

    var displayName: String {
        switch self {
        case .basic: "Quarters, eighths and rests"
        case .syncopation: "Add dotted and off-beat rhythms"
        case .sixteenths: "Add sixteenths"
        }
    }

    /// In catalog order, so generated rhythms are repeatable with a seed.
    var cells: [RhythmCell] {
        let basic: [RhythmCell] = [.quarter, .eighths, .quarterRest, .half]
        let syncopation = basic + [.dottedQuarterEighth, .eighthRestEighth]
        switch self {
        case .basic: return basic
        case .syncopation: return syncopation
        case .sixteenths: return syncopation + [.sixteenths, .eighthTwoSixteenths, .twoSixteenthsEighth]
        }
    }
}

enum RhythmTempo: String, StoredEnum {
    case slow = "SLOW"
    case medium = "MEDIUM"
    case fast = "FAST"

    var displayName: String {
        switch self {
        case .slow: "Slow"
        case .medium: "Medium"
        case .fast: "Fast"
        }
    }

    var bpm: Int {
        switch self {
        case .slow: 70
        case .medium: 90
        case .fast: 120
        }
    }
}

struct RhythmQuestion: ExerciseQuestion {
    static let countInPitch = Pitch(96)
    static let rhythmPitch = Pitch(72)

    /// Count-in clicks are short so they read as a metronome rather than as notes.
    static let countInClickBeats = 0.25

    let rhythm: Rhythm
    let tempo: RhythmTempo
    let choices: [Rhythm]

    init(rhythm: Rhythm, tempo: RhythmTempo, choices: [Rhythm]) {
        requireValidChoices(choices, rhythm)
        self.rhythm = rhythm
        self.tempo = tempo
        self.choices = choices
    }

    var correctAnswer: Rhythm { rhythm }

    /**
     * One bar of count-in clicks on a high note, then the rhythm on a lower one, all placed
     * exactly in beats so the timing comes from the audio clock.
     */
    var prompt: ListeningPrompt {
        let countIn = Rhythm.beatsPerBar
        let notes = rhythm.timeline.filter { !$0.event.isRest }
        return ListeningPrompt(
            moments: Array(repeating: [Self.countInPitch], count: countIn) + notes.map { _ in [Self.rhythmPitch] },
            beatTiming: ListeningPrompt.BeatTiming(
                bpm: tempo.bpm,
                startBeats: (0..<countIn).map(Double.init) + notes.map { Double(countIn) + $0.start },
                lengthBeats: Array(repeating: Self.countInClickBeats, count: countIn) + notes.map(\.event.beats)
            )
        )
    }
}

struct RhythmSettings: Equatable {
    var vocabulary: RhythmVocabulary = .basic
    var bars = 1
    var tempo: RhythmTempo = .medium
    /// Ask more often about rhythm figures recently misheard.
    var focusOnWeakSpots = true

    static let barOptions = [1, 2]
    static let choiceCount = 4

    var isValid: Bool { Self.barOptions.contains(bars) }
}

final class RhythmQuestionGenerator {
    private let settings: RhythmSettings
    private let random: RandomSource
    /// Relative likelihood of each cell appearing; nil picks cells evenly.
    private let weightOf: ((RhythmCell) -> Double)?
    private let cells: [RhythmCell]

    init(settings: RhythmSettings = RhythmSettings(), random: RandomSource = .system, weightOf: ((RhythmCell) -> Double)? = nil) {
        self.settings = settings
        self.random = random
        self.weightOf = weightOf
        cells = settings.vocabulary.cells
    }

    func generate() -> RhythmQuestion {
        let rhythm = randomRhythm()
        return RhythmQuestion(rhythm: rhythm, tempo: settings.tempo, choices: choices(for: rhythm))
    }

    private func randomRhythm() -> Rhythm {
        while true {
            let candidate = Rhythm((0..<settings.bars).flatMap { _ in randomBar() })
            if isPleasant(candidate) { return candidate }
        }
    }

    /// Fills one bar beat by beat; two-beat cells only start on beat 1 or 3 so they notate cleanly.
    private func randomBar() -> [RhythmCell] {
        var bar: [RhythmCell] = []
        var beat = 0
        while beat < Rhythm.beatsPerBar {
            let fitting = cells.filter { $0.beats == 1 || (beat % 2 == 0 && beat + $0.beats <= Rhythm.beatsPerBar) }
            let cell = weightOf.map { fitting.weightedRandom(random, weightOf: $0) } ?? random.element(of: fitting)
            bar.append(cell)
            beat += cell.beats
        }
        return bar
    }

    /**
     * The correct rhythm plus alternatives that each change one cell (a beat or two-beat
     * figure), so the listener has to follow the whole rhythm. Shuffled so position is no clue.
     */
    private func choices(for correct: Rhythm) -> [Rhythm] {
        // Closest first: a swapped cell, then a re-split half bar; random rhythms only as a last resort.
        let nearMisses = random.shuffled(oneCellChanges(correct).filter(isPleasant)) +
            random.shuffled(halfBarChanges(correct).filter(isPleasant))
        let needed = RhythmSettings.choiceCount - 1
        var distractors: [Rhythm] = []
        var seen: Set<Rhythm> = [correct]
        for candidate in nearMisses where distractors.count < needed && seen.insert(candidate).inserted {
            distractors.append(candidate)
        }
        var attempts = 0
        while distractors.count < needed && attempts < 50 {
            let candidate = randomRhythm()
            if seen.insert(candidate).inserted { distractors.append(candidate) }
            attempts += 1
        }
        return random.shuffled(distractors + [correct])
    }

    /// Every rhythm that replaces exactly one cell with a different cell of the same length.
    private func oneCellChanges(_ rhythm: Rhythm) -> [Rhythm] {
        rhythm.cells.indices.flatMap { index in
            let current = rhythm.cells[index]
            return cells.filter { $0 != current && $0.beats == current.beats }.map { replacement in
                var changed = rhythm.cells
                changed[index] = replacement
                return Rhythm(changed)
            }
        }
    }

    /**
     * Rhythms that change one half bar (beats 1–2 or 3–4) by splitting a two-beat cell into two
     * one-beat cells, or merging two one-beat cells into a two-beat cell.
     */
    private func halfBarChanges(_ rhythm: Rhythm) -> [Rhythm] {
        let single = cells.filter { $0.beats == 1 }
        let double = cells.filter { $0.beats == 2 }
        let starts = rhythm.startBeats
        return starts.indices.filter { starts[$0] % 2 == 0 }.flatMap { index -> [Rhythm] in
            let cell = rhythm.cells[index]
            let replaced: Int
            let replacements: [[RhythmCell]]
            if cell.beats == 2 {
                replaced = 1
                replacements = single.flatMap { first in single.map { second in [first, second] } }
            } else {
                replaced = 2
                replacements = double.map { [$0] }
            }
            return replacements.compactMap { replacement in
                let end = min(index + replaced, rhythm.cells.count)
                return Rhythm.validated(Array(rhythm.cells[..<index]) + replacement + Array(rhythm.cells[end...]))
            }
        }
    }

    /// Rhythms that start with a note and have at least two notes per bar are easier to follow.
    private func isPleasant(_ rhythm: Rhythm) -> Bool {
        if rhythm.cells[0].events[0].isRest { return false }
        var notesPerBar = Array(repeating: 0, count: rhythm.bars)
        for (start, event) in rhythm.timeline where !event.isRest {
            notesPerBar[Int(start) / Rhythm.beatsPerBar] += 1
        }
        return notesPerBar.allSatisfy { $0 >= 2 }
    }
}

/**
 * Scores a rhythm answer cell by cell: each cell of the played rhythm is one result, correct when
 * the chosen rhythm has the same cell starting on the same beat.
 */
func rhythmCellResults(_ played: Rhythm, _ chosen: Rhythm) -> [ItemResult<RhythmCell>] {
    rhythmCellPairs(played, chosen).map { ItemResult(item: $0.played, isCorrect: $0.chosen == $0.played) }
}

/**
 * Pairs each cell of the played rhythm with the chosen rhythm's cell starting on the same beat,
 * or nil when no chosen cell starts there (the beat was split differently).
 */
func rhythmCellPairs(_ played: Rhythm, _ chosen: Rhythm) -> [(played: RhythmCell, chosen: RhythmCell?)] {
    let chosenByBeat = Dictionary(zip(chosen.startBeats, chosen.cells), uniquingKeysWith: { first, _ in first })
    return zip(played.startBeats, played.cells).map { beat, cell in (cell, chosenByBeat[beat]) }
}
