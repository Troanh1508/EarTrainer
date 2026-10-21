import Foundation

/// How one exercise's numbers are labeled: what each chart counts, and how a session is scored.
struct ScoringLabels {
    /// Under a percentage, e.g. "of chords right".
    let donutCaption: String
    /// What is counted, e.g. "chords".
    let unit: (Int) -> String
    /// e.g. "12 of 20 chords right".
    let overall: (Int, Int) -> String
    let sessionScore: (Int, Int) -> String
    /// Whole questions answered fully right, for exercises scored item by item.
    let fullyRight: ((Int, Int) -> String)?
    let explanation: String

    init(_ exercise: ExerciseKind) {
        func counted(_ one: String, _ other: String) -> (Int, Int) -> String {
            { right, total in "\(right) of \(total) \(plural(total, one, other)) right" }
        }
        func fully(_ one: String, _ other: String) -> (Int, Int) -> String {
            { right, total in "\(right) of \(total) \(plural(total, one, other)) fully right" }
        }
        switch exercise {
        case .intervals, .scaleDegrees, .chords:
            donutCaption = "of answers right"
            unit = { plural($0, "answer", "answers") }
            overall = counted("answer", "answers")
            sessionScore = { right, total in "\(right) of \(total) right" }
            fullyRight = nil
            explanation = exercise == .chords
                ? "Each answer counts once, right or wrong. Questions about chord quality and about inversion are counted separately; choose which above."
                : "Each answer counts once, right or wrong."
        case .progressions:
            donutCaption = "of chords right"
            unit = { plural($0, "chord", "chords") }
            overall = counted("chord", "chords")
            sessionScore = counted("chord", "chords")
            fullyRight = fully("progression", "progressions")
            explanation = "Each chord after the opening tonic counts on its own, so one wrong chord costs one point rather than the whole progression. Each session also shows how many progressions were fully right."
        case .dictation:
            donutCaption = "of notes right"
            unit = { plural($0, "note", "notes") }
            overall = counted("note", "notes")
            sessionScore = counted("note", "notes")
            fullyRight = fully("melody", "melodies")
            explanation = "Each note after the opening tonic counts on its own, so one wrong note costs one point rather than the whole melody. Each session also shows how many melodies were fully right."
        case .rhythm:
            donutCaption = "of figures right"
            unit = { plural($0, "figure", "figures") }
            overall = counted("figure", "figures")
            sessionScore = counted("figure", "figures")
            fullyRight = fully("rhythm", "rhythms")
            explanation = "Each rhythm figure counts on its own, on the beat where it was played, so one wrong figure costs one point rather than the whole rhythm. Each session also shows how many rhythms were fully right."
        }
    }
}
