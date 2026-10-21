import Foundation

/**
 * How often [asked] was answered as [answered]. [answered] equals [asked] for right answers, and
 * is nil when the answer had nothing in that place (a blank note, a rhythm split differently).
 */
struct Confusion<T: Hashable>: Hashable {
    let asked: T
    let answered: T?
    let count: Int

    func map<R: Hashable>(_ transform: (T) -> R) -> Confusion<R> {
        Confusion<R>(asked: transform(asked), answered: answered.map(transform), count: count)
    }
}

/// How often one answer value (an interval, a chord quality, ...) was identified correctly.
struct AnswerAccuracy<T> {
    let item: T
    let attempts: Int
    let correct: Int

    /// Fraction correct, or nil when it has not been practiced.
    var accuracy: Double? { attempts == 0 ? nil : Double(correct) / Double(attempts) }
}

/**
 * One practice session. [attempts] and [correct] count whole questions; [items] and
 * [itemsCorrect] count what the progress charts score: one per answer for intervals, chords and
 * scale degrees, but one per chord, note or rhythm figure for progressions, dictation and rhythm.
 */
struct SessionSummary: Hashable {
    let sessionId: String
    let exercise: ExerciseKind
    let startedAtMillis: Int64
    let attempts: Int
    let correct: Int
    var items: Int
    var itemsCorrect: Int

    init(sessionId: String, exercise: ExerciseKind, startedAtMillis: Int64, attempts: Int, correct: Int, items: Int? = nil, itemsCorrect: Int? = nil) {
        self.sessionId = sessionId
        self.exercise = exercise
        self.startedAtMillis = startedAtMillis
        self.attempts = attempts
        self.correct = correct
        self.items = items ?? attempts
        self.itemsCorrect = itemsCorrect ?? correct
    }
}

/// How many answers were given on one local calendar [day], counted in days since 1970-01-01.
struct DayCount: Hashable {
    let day: Int64
    let answers: Int
}

/// Sessions finished on one local calendar day, as days since 1970-01-01.
struct DaySessions: Hashable {
    let day: Int64
    let sessions: Int
}

// MARK: - Stored forms

extension ChordProgression {
    /// e.g. "1,6,4,5".
    var storedSteps: String { chords.map { String($0.step) }.joined(separator: ",") }
}

extension Melody {
    var storedNames: String { degrees.map(\.name).joined(separator: ",") }
}

extension Rhythm {
    var storedNames: String { cells.map(\.name).joined(separator: ",") }
}

extension String {
    /// Scale steps from a stored progression; unreadable parts are skipped.
    func decodedSteps() -> [Int] {
        split(separator: ",").compactMap { Int($0.trimmingCharacters(in: .whitespaces)) }
    }

    /// Degrees from a stored melody; an unknown name becomes nil so positions still line up.
    func decodedDegrees() -> [ScaleDegree?] {
        split(separator: ",", omittingEmptySubsequences: false).map { ScaleDegree.named($0.trimmingCharacters(in: .whitespaces)) }
    }

    /// Nil if a stored rhythm no longer parses (e.g. a renamed cell), so one bad row is skipped.
    func decodedRhythm() -> Rhythm? {
        var cells: [RhythmCell] = []
        for name in split(separator: ",", omittingEmptySubsequences: false) {
            guard let cell = RhythmCell.named(name.trimmingCharacters(in: .whitespaces)) else { return nil }
            cells.append(cell)
        }
        return Rhythm.validated(cells)
    }
}

// MARK: - Scoring item by item

/**
 * Pairs every chord after the opening tonic with the chord chosen in its place, or nil when the
 * chosen progression has no valid chord there.
 */
func progressionChordPairs(_ mode: Mode, asked: [Int], chosen: [Int]) -> [(asked: DiatonicChord, chosen: DiatonicChord?)] {
    guard asked.count > 1 else { return [] }
    return (1..<asked.count).compactMap { position in
        let step = asked[position]
        guard (1...7).contains(step) else { return nil }
        let chosenStep = position < chosen.count ? chosen[position] : nil
        return (DiatonicChord(mode, step), chosenStep.flatMap { (1...7).contains($0) ? DiatonicChord(mode, $0) : nil })
    }
}

/**
 * Scores a progression answer chord by chord: every position after the opening tonic is one
 * result for the chord asked there, correct when the chosen progression has the same chord.
 */
func progressionChordResults(_ mode: Mode, asked: [Int], chosen: [Int]) -> [ItemResult<DiatonicChord>] {
    progressionChordPairs(mode, asked: asked, chosen: chosen).map { ItemResult(item: $0.asked, isCorrect: $0.chosen == $0.asked) }
}

/// Per-chord accuracy over many (asked, chosen) step lists; see `progressionChordResults`.
func progressionChordAccuracy(_ mode: Mode, _ answers: [(asked: [Int], chosen: [Int])]) -> [AnswerAccuracy<DiatonicChord>] {
    let results = answers.flatMap { progressionChordResults(mode, asked: $0.asked, chosen: $0.chosen) }
    return DiatonicChord.all(mode).map { chord in
        let forChord = results.filter { $0.item == chord }
        return AnswerAccuracy(item: chord, attempts: forChord.count, correct: forChord.filter(\.isCorrect).count)
    }
}

/// Pairs every note after the opening tonic with the note written in its place, or nil if none.
func dictationNotePairs(_ melody: [ScaleDegree?], _ answer: [ScaleDegree?]) -> [(played: ScaleDegree, written: ScaleDegree?)] {
    guard melody.count > 1 else { return [] }
    return (1..<melody.count).compactMap { position in
        guard let played = melody[position] else { return nil }
        return (played, position < answer.count ? answer[position] : nil)
    }
}

/**
 * Scores a dictation note by note: every note after the given opening tonic is one result for
 * the degree played there, correct when the written melody has the same degree in that place.
 */
func dictationNoteResults(_ melody: [ScaleDegree?], _ answer: [ScaleDegree?]) -> [ItemResult<ScaleDegree>] {
    dictationNotePairs(melody, answer).map { ItemResult(item: $0.played, isCorrect: $0.written == $0.played) }
}

/// Per-cell results for a stored rhythm answer, or none if either rhythm no longer parses.
func storedRhythmCellResults(rhythm: String, answer: String) -> [ItemResult<RhythmCell>] {
    guard let played = rhythm.decodedRhythm(), let chosen = answer.decodedRhythm() else { return [] }
    return rhythmCellResults(played, chosen)
}

/// Counts identical (asked, answered) pairs.
func countConfusions<T: Hashable>(_ pairs: [(T, T?)]) -> [Confusion<T>] {
    var counts: [Pair<T>: Int] = [:]
    var order: [Pair<T>] = []
    for (asked, answered) in pairs {
        let key = Pair(first: asked, second: answered)
        if counts[key] == nil { order.append(key) }
        counts[key, default: 0] += 1
    }
    return order.map { Confusion(asked: $0.first, answered: $0.second, count: counts[$0]!) }
}

private struct Pair<T: Hashable>: Hashable {
    let first: T
    let second: T?
}

/// Accuracy for every value in [all], in that order, from per-item totals; unpracticed values get zero attempts.
func completeAccuracy<T: StoredEnum>(_ totals: [String: (attempts: Int, correct: Int)], over all: [T]) -> [AnswerAccuracy<T>] {
    all.map { value in
        let total = totals[value.name]
        return AnswerAccuracy(item: value, attempts: total?.attempts ?? 0, correct: total?.correct ?? 0)
    }
}

/// Accuracy for every value in [all], in that order, from individual results.
func itemAccuracy<T: Hashable>(of results: [ItemResult<T>], over all: [T]) -> [AnswerAccuracy<T>] {
    all.map { value in
        let forValue = results.filter { $0.item == value }
        return AnswerAccuracy(item: value, attempts: forValue.count, correct: forValue.filter(\.isCorrect).count)
    }
}
