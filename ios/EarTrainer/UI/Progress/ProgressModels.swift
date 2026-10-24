import Foundation

/// What the accuracy list shows; each exercise has its own kind of filter.
enum ProgressFilter: Hashable {
    /// Interval accuracy for one playing style, or every style when nil.
    case intervalStyle(IntervalStyle?)
    /// Chord accuracy per quality or per inversion.
    case chordTarget(ChordTarget)
    /// Scale-degree accuracy in one mode, or every mode when nil.
    case scaleDegreeMode(Mode?)
    /// Per-chord accuracy within progressions in one mode; numerals differ between modes.
    case progressionMode(Mode)
    /// Per-note accuracy in dictations in one mode; each mode has its own seven notes.
    case dictationMode(Mode)
    /// Accuracy per rhythm figure; rhythms have a single view.
    case rhythm

    var exercise: ExerciseKind {
        switch self {
        case .intervalStyle: .intervals
        case .chordTarget: .chords
        case .scaleDegreeMode: .scaleDegrees
        case .progressionMode: .progressions
        case .dictationMode: .dictation
        case .rhythm: .rhythm
        }
    }

    static func options(for exercise: ExerciseKind) -> [ProgressFilter] {
        switch exercise {
        case .intervals: [.intervalStyle(nil)] + IntervalStyle.allCases.map { .intervalStyle($0) }
        case .chords: ChordTarget.allCases.map { .chordTarget($0) }
        case .scaleDegrees: [.scaleDegreeMode(nil)] + Mode.allCases.map { .scaleDegreeMode($0) }
        case .progressions: Mode.allCases.map { .progressionMode($0) }
        case .dictation: Mode.allCases.map { .dictationMode($0) }
        case .rhythm: [.rhythm]
        }
    }

    /**
     * Filters that together count every answer of [exercise] exactly once: every style or
     * mode at once where a filter allows it, otherwise each chord target or each mode in turn.
     */
    static func everyAnswer(of exercise: ExerciseKind) -> [ProgressFilter] {
        switch exercise {
        case .intervals: [.intervalStyle(nil)]
        case .chords: ChordTarget.allCases.map { .chordTarget($0) }
        case .scaleDegrees: [.scaleDegreeMode(nil)]
        case .progressions: Mode.allCases.map { .progressionMode($0) }
        case .dictation: Mode.allCases.map { .dictationMode($0) }
        case .rhythm: [.rhythm]
        }
    }

    var label: String {
        switch self {
        case .intervalStyle(let style): style?.displayName ?? Strings.filterAll
        case .chordTarget(let target): target.displayName
        case .scaleDegreeMode(let mode): mode?.displayName ?? Strings.filterAllModes
        case .progressionMode(let mode), .dictationMode(let mode): mode.displayName
        case .rhythm: Strings.filterAllRhythms
        }
    }

    var accuracyTitle: String {
        switch self {
        case .intervalStyle: "By interval"
        case .chordTarget(let target): target == .quality ? "By chord quality" : "By inversion"
        case .scaleDegreeMode: "By scale degree"
        case .progressionMode: "By chord, after the opening tonic"
        case .dictationMode: "By note, after the opening tonic"
        case .rhythm: "By rhythm figure"
        }
    }

    /// The style, chord target or mode the filter looks at, as a stored name; nil for all of them.
    var scope: String? {
        switch self {
        case .intervalStyle(let style): style?.name
        case .chordTarget(let target): target.name
        case .scaleDegreeMode(let mode): mode?.name
        case .progressionMode(let mode), .dictationMode(let mode): mode.name
        case .rhythm: nil
        }
    }
}

/// One row of the accuracy list, ready to display.
struct AccuracyItem: Hashable {
    /// Practiced items answered correctly less often than this need work.
    static let weakThreshold = 0.65

    let label: String
    let attempts: Int
    let correct: Int

    var accuracy: Double? { attempts == 0 ? nil : Double(correct) / Double(attempts) }

    /// Practiced, but answered correctly less often than `weakThreshold`.
    var needsWork: Bool { accuracy.map { $0 < Self.weakThreshold } ?? false }
}

/// How the accuracy list is ordered.
enum ItemOrder: CaseIterable {
    case weakestFirst
    case strongestFirst

    var label: String {
        switch self {
        case .weakestFirst: Strings.weakestFirst
        case .strongestFirst: Strings.strongestFirst
        }
    }
}

extension Array where Element == AccuracyItem {
    /// Practiced items from least to most accurate (more answers first on a tie), then unpracticed ones.
    var weakestFirst: [AccuracyItem] { practicedFirst(byAccuracy: <) }

    /// Practiced items from most to least accurate (more answers first on a tie), then unpracticed ones.
    var strongestFirst: [AccuracyItem] { practicedFirst(byAccuracy: >) }

    /// The items in [order].
    func ordered(_ order: ItemOrder) -> [AccuracyItem] {
        switch order {
        case .weakestFirst: weakestFirst
        case .strongestFirst: strongestFirst
        }
    }

    private func practicedFirst(byAccuracy precedes: (Double, Double) -> Bool) -> [AccuracyItem] {
        let practiced = enumerated().filter { $0.element.attempts > 0 }.sorted { a, b in
            let accuracyA = a.element.accuracy!
            let accuracyB = b.element.accuracy!
            if accuracyA != accuracyB { return precedes(accuracyA, accuracyB) }
            if a.element.attempts != b.element.attempts { return a.element.attempts > b.element.attempts }
            return a.offset < b.offset
        }.map(\.element)
        return practiced + filter { $0.attempts == 0 }
    }
}

struct ProgressUiState {
    /// Sessions the "recent change" needs before it says anything.
    static let minSessionsForChange = 4

    let filter: ProgressFilter
    let accuracy: [AccuracyItem]
    let recentSessions: [SessionSummary]
    /**
     * The latest sessions with answers matching [filter], oldest first, for the accuracy-over-time
     * chart; each counts only those answers, scored like [accuracy].
     */
    let trend: [SessionSummary]

    var exercise: ExerciseKind { filter.exercise }
    var filterOptions: [ProgressFilter] { ProgressFilter.options(for: exercise) }
    var totalAttempts: Int { accuracy.reduce(0) { $0 + $1.attempts } }
    var totalCorrect: Int { accuracy.reduce(0) { $0 + $1.correct } }
    var overallAccuracy: Double? { totalAttempts == 0 ? nil : Double(totalCorrect) / Double(totalAttempts) }

    /// Whether anything has ever been recorded, regardless of the current exercise or filter.
    var hasHistory: Bool { !recentSessions.isEmpty }

    /**
     * Change in percentage points between the older and the newer half of [trend], each half
     * pooled over all its answers; nil until there are enough sessions to compare.
     */
    var trendChange: Int? {
        guard trend.count >= Self.minSessionsForChange else { return nil }
        let half = trend.count / 2
        func pooled(_ sessions: ArraySlice<SessionSummary>) -> Double {
            Double(sessions.reduce(0) { $0 + $1.itemsCorrect }) / Double(sessions.reduce(0) { $0 + $1.items })
        }
        return Int(((pooled(trend.suffix(half)) - pooled(trend.prefix(half))) * 100).rounded())
    }
}

/**
 * How often something was answered as [answered]; nil [answered] means something else entirely.
 * The keys are the values' stable names (e.g. "MINOR_SIXTH"), for turning a label back into a
 * value; the short forms are compact labels (e.g. "m6"), and the semitones each value's pitch
 * where it has one.
 */
struct LabeledConfusion {
    let asked: String
    let answered: String?
    let count: Int
    var askedKey: String
    var answeredKey: String?
    var askedShort: String
    var answeredShort: String?
    var askedSemitones: Int?
    var answeredSemitones: Int?
}

extension Array {
    func labeled<T>(
        label: (T) -> String, key: (T) -> String, short: ((T) -> String)? = nil, semitones: ((T) -> Int)? = nil
    ) -> [LabeledConfusion] where Element == Confusion<T> {
        map { confusion in
            LabeledConfusion(
                asked: label(confusion.asked), answered: confusion.answered.map(label), count: confusion.count,
                askedKey: key(confusion.asked), answeredKey: confusion.answered.map(key),
                askedShort: short?(confusion.asked) ?? label(confusion.asked),
                answeredShort: confusion.answered.map { short?($0) ?? label($0) },
                askedSemitones: semitones?(confusion.asked), answeredSemitones: confusion.answered.flatMap { semitones?($0) }
            )
        }
    }
}

/// One answer the selected item was given as, and how often. [label] is nil for "something else".
struct AnswerShare: Hashable {
    let label: String?
    let count: Int
    let isRight: Bool
}

/// Two items mistaken for each other [count] times, in either direction.
struct ConfusedPair: Hashable {
    let first: String
    let second: String
    let count: Int
}

/// What one item was answered as, plus the exercise's most confused pairs, under one filter.
struct MistakesUiState {
    static let pairLimit = 5

    let filter: ProgressFilter
    /// Practiced items, weakest first, to choose from.
    let items: [AccuracyItem]
    let selected: AccuracyItem
    /// The right answer first, then wrong answers from most to least frequent.
    let answers: [AnswerShare]
    /// Most frequent first.
    let confusedPairs: [ConfusedPair]
    /// The stable name of every item and answer, by its label.
    let keys: [String: String]
    /// Compact labels, e.g. "m6" for "Minor sixth", where an exercise has them.
    let shortLabels: [String: String]
    /// Each item's pitch in semitones, for exercises where items are pitches.
    let semitones: [String: Int]

    var exercise: ExerciseKind { filter.exercise }

    /// The wrong answer given most often, if there was any.
    var mostCommonMistake: AnswerShare? { answers.first { !$0.isRight } }

    func shortLabel(of label: String) -> String { shortLabels[label] ?? label }

    /// How many semitones apart two items are, when both are pitches.
    func semitonesBetween(_ first: String, _ second: String) -> Int? {
        guard let a = semitones[first], let b = semitones[second] else { return nil }
        return abs(a - b)
    }

    /**
     * A drill on the two items labeled [first] and [second], under the same style, chord target
     * or mode as these mistakes; nil if a label is unknown.
     */
    func comparison(_ first: String, _ second: String) -> ComparisonRequest? {
        guard let firstKey = keys[first], let secondKey = keys[second], firstKey != secondKey else { return nil }
        return ComparisonRequest(exercise: exercise, scope: filter.scope, first: firstKey, second: secondKey)
    }

    /// Nil when [selectedLabel] has no answers under [filter], e.g. after the filter changed.
    static func from(filter: ProgressFilter, accuracy: [AccuracyItem], confusions: [LabeledConfusion], selectedLabel: String) -> MistakesUiState? {
        let items = accuracy.weakestFirst.filter { $0.attempts > 0 }
        guard let selected = items.first(where: { $0.label == selectedLabel }) else { return nil }
        let answers = confusions
            .filter { $0.asked == selected.label }
            .map { AnswerShare(label: $0.answered, count: $0.count, isRight: $0.answered == $0.asked) }
            .enumerated()
            .sorted { a, b in
                if a.element.isRight != b.element.isRight { return a.element.isRight }
                if a.element.count != b.element.count { return a.element.count > b.element.count }
                return a.offset < b.offset
            }
            .map(\.element)

        // Each pair is named weaker item first, the order the items are listed in.
        let rank = Dictionary(items.enumerated().map { ($0.element.label, $0.offset) }, uniquingKeysWith: { first, _ in first })
        var pairCounts: [[String]: Int] = [:]
        var pairOrder: [[String]] = []
        for confusion in confusions {
            guard let answered = confusion.answered, answered != confusion.asked else { continue }
            let pair = [confusion.asked, answered].sorted { (rank[$0] ?? .max, $0) < (rank[$1] ?? .max, $1) }
            if pairCounts[pair] == nil { pairOrder.append(pair) }
            pairCounts[pair, default: 0] += confusion.count
        }
        let pairs = pairOrder
            .map { ConfusedPair(first: $0[0], second: $0[1], count: pairCounts[$0]!) }
            .sorted { a, b in a.count != b.count ? a.count > b.count : (rank[a.first] ?? .max) < (rank[b.first] ?? .max) }
            .prefix(pairLimit)

        var keys: [String: String] = [:]
        var shortLabels: [String: String] = [:]
        var semitones: [String: Int] = [:]
        for confusion in confusions {
            keys[confusion.asked] = confusion.askedKey
            shortLabels[confusion.asked] = confusion.askedShort
            semitones[confusion.asked] = confusion.askedSemitones
            if let answered = confusion.answered {
                keys[answered] = confusion.answeredKey
                shortLabels[answered] = confusion.answeredShort
                if let answeredSemitones = confusion.answeredSemitones { semitones[answered] = answeredSemitones }
            }
        }
        return MistakesUiState(
            filter: filter, items: items, selected: selected, answers: answers, confusedPairs: Array(pairs),
            keys: keys, shortLabels: shortLabels, semitones: semitones
        )
    }
}

/**
 * What to compare: two answers' stable names (e.g. "MINOR_SIXTH") and the [scope] they were
 * mixed up under, the style, chord target or mode that was being looked at, or nil for all.
 */
struct ComparisonRequest: Hashable {
    let exercise: ExerciseKind
    let scope: String?
    let first: String
    let second: String
}

extension PracticeHistory {
    /*
     * Accuracy per item under a progress filter, labeled for display. Shared by the Progress and
     * Home screens so both count and name answers the same way.
     */

    func accuracy(for filter: ProgressFilter) async -> [AccuracyItem] {
        func items<T>(_ list: [AnswerAccuracy<T>], _ label: (T) -> String) -> [AccuracyItem] {
            list.map { AccuracyItem(label: label($0.item), attempts: $0.attempts, correct: $0.correct) }
        }
        switch filter {
        case .intervalStyle(let style): return items(await intervalAccuracy(style: style), \.displayName)
        case .chordTarget(let target): return items(await chordAccuracy(target: target), \.displayName)
        case .scaleDegreeMode(let mode): return items(await scaleDegreeAccuracy(mode: mode), degreeLabel)
        case .progressionMode(let mode): return items(await progressionAccuracy(mode: mode), \.numeral)
        case .dictationMode(let mode): return items(await dictationAccuracy(mode: mode), degreeLabel)
        case .rhythm: return items(await rhythmAccuracy(), rhythmLabel)
        }
    }

    /// The same answers as `accuracy(for:)`, so the chart and the bars measure the same thing.
    func trend(for filter: ProgressFilter, limit: Int) async -> [SessionSummary] {
        switch filter {
        case .intervalStyle(let style): await intervalSessions(style: style, limit: limit)
        case .chordTarget(let target): await chordSessions(target: target, limit: limit)
        case .scaleDegreeMode(let mode): await scaleDegreeSessions(mode: mode, limit: limit)
        case .progressionMode(let mode): await progressionSessions(mode: mode, limit: limit)
        case .dictationMode(let mode): await dictationSessions(mode: mode, limit: limit)
        case .rhythm: await rhythmSessions(limit: limit)
        }
    }

    /// Labeled exactly like `accuracy(for:)`, so items and answers match the bars they came from.
    func confusions(for filter: ProgressFilter) async -> [LabeledConfusion] {
        switch filter {
        case .intervalStyle(let style):
            return await intervalConfusions(style: style).labeled(label: \.displayName, key: \.name, short: \.abbreviation, semitones: \.semitones)
        case .chordTarget(let target):
            return await chordConfusions(target: target).labeled(label: \.displayName, key: \.name)
        case .scaleDegreeMode(let mode):
            return await scaleDegreeConfusions(mode: mode).labeled(label: degreeLabel, key: \.name, semitones: \.semitonesAboveTonic)
        case .progressionMode(let mode):
            return await progressionConfusions(mode: mode).labeled(label: \.numeral, key: { String($0.step) })
        case .dictationMode(let mode):
            return await dictationConfusions(mode: mode).labeled(label: degreeLabel, key: \.name, semitones: \.semitonesAboveTonic)
        case .rhythm:
            return await rhythmConfusions().labeled(label: rhythmLabel, key: \.name)
        }
    }
}
