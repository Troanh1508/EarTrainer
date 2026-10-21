import Foundation

/// One answered question of the current session, kept for the session strip and the summary.
struct AnsweredQuestion<Q: ExerciseQuestion> {
    let question: Q
    let answer: Q.Answer
    let isCorrect: Bool
    /// Times the prompt was started before answering; 1 means no replays.
    let playCount: Int
    let firstPlayedAtMillis: Int64
    let answeredAtMillis: Int64

    var responseMillis: Int64 { max(0, answeredAtMillis - firstPlayedAtMillis) }
}

/**
 * One scored part of an answer: the whole answer for most exercises, or one chord, note or
 * rhythm figure where an exercise is scored item by item. [chosenLabel] is what was given in its
 * place, or nil when nothing matches it (e.g. a beat split differently).
 */
struct SummaryItem: Hashable {
    let label: String
    let chosenLabel: String?
    let isCorrect: Bool
}

/// How often an item came up this session and how often it was missed.
struct ItemTally: Hashable {
    let label: String
    let right: Int
    let missed: Int

    var total: Int { right + missed }
}

/// A wrong answer given for [label], [count] times this session.
struct SessionMistake: Hashable {
    let label: String
    let chosenLabel: String?
    let count: Int
}

/// The end-of-session results.
struct PracticeSummary: Equatable {
    /// Sessions the "recent average" is taken over.
    static let averageOverSessions = 20

    let questions: Int
    let questionsRight: Int
    /// Scored items: equal to [questions] unless the exercise is scored item by item.
    let items: Int
    let itemsRight: Int
    /// From first hearing the first question to answering the last.
    let durationMillis: Int64
    let averageResponseMillis: Int64
    /// Plays beyond the first of each question.
    let replays: Int
    /// Weakest first.
    let tallies: [ItemTally]
    /// Most frequent first.
    let mistakes: [SessionMistake]
    /// Item accuracy over recent earlier sessions of this exercise, or nil if there are none.
    var pastAccuracy: Double?

    var accuracy: Double { items == 0 ? 0 : Double(itemsRight) / Double(items) }
    var percent: Int { Int((accuracy * 100).rounded()) }
    var missedQuestions: Int { questions - questionsRight }

    /// Percentage points above (positive) or below the recent average, or nil without one.
    var pointsAboveAverage: Int? { pastAccuracy.map { percent - Int(($0 * 100).rounded()) } }

    /// The same summary compared with [sessions], leaving out [currentSessionId].
    func compared(with sessions: [SessionSummary], currentSessionId: String) -> PracticeSummary {
        let earlier = sessions.filter { $0.sessionId != currentSessionId && $0.items > 0 }
        let items = earlier.reduce(0) { $0 + $1.items }
        var copy = self
        copy.pastAccuracy = items == 0 ? nil : Double(earlier.reduce(0) { $0 + $1.itemsCorrect }) / Double(items)
        return copy
    }

    static func from<Q>(_ answered: [AnsweredQuestion<Q>], itemsOf: (Q, Q.Answer) -> [SummaryItem]) -> PracticeSummary {
        let items = answered.flatMap { itemsOf($0.question, $0.answer) }

        var tallyOrder: [String] = []
        var tallyCounts: [String: (right: Int, missed: Int)] = [:]
        for item in items {
            if tallyCounts[item.label] == nil { tallyOrder.append(item.label) }
            var counts = tallyCounts[item.label] ?? (0, 0)
            if item.isCorrect { counts.right += 1 } else { counts.missed += 1 }
            tallyCounts[item.label] = counts
        }
        let tallies = tallyOrder.enumerated()
            .map { index, label in (index, ItemTally(label: label, right: tallyCounts[label]!.right, missed: tallyCounts[label]!.missed)) }
            .sorted { a, b in
                let shareA = Double(a.1.right) / Double(a.1.total)
                let shareB = Double(b.1.right) / Double(b.1.total)
                if shareA != shareB { return shareA < shareB }
                if a.1.total != b.1.total { return a.1.total > b.1.total }
                return a.0 < b.0
            }
            .map(\.1)

        var mistakeOrder: [SummaryItem] = []
        var mistakeCounts: [SummaryItem: Int] = [:]
        for item in items where !item.isCorrect {
            let key = SummaryItem(label: item.label, chosenLabel: item.chosenLabel, isCorrect: false)
            if mistakeCounts[key] == nil { mistakeOrder.append(key) }
            mistakeCounts[key, default: 0] += 1
        }
        let mistakes = mistakeOrder.enumerated()
            .sorted { a, b in
                let countA = mistakeCounts[a.1]!
                let countB = mistakeCounts[b.1]!
                return countA != countB ? countA > countB : a.0 < b.0
            }
            .map { SessionMistake(label: $0.1.label, chosenLabel: $0.1.chosenLabel, count: mistakeCounts[$0.1]!) }

        return PracticeSummary(
            questions: answered.count,
            questionsRight: answered.filter(\.isCorrect).count,
            items: items.count,
            itemsRight: items.filter(\.isCorrect).count,
            durationMillis: answered.isEmpty ? 0 : answered.last!.answeredAtMillis - answered.first!.firstPlayedAtMillis,
            averageResponseMillis: answered.isEmpty ? 0 : answered.reduce(0) { $0 + $1.responseMillis } / Int64(answered.count),
            replays: answered.reduce(0) { $0 + max(0, $1.playCount - 1) },
            tallies: tallies,
            mistakes: mistakes
        )
    }
}

/// Scores each answer as one item, named by [labelOf], for exercises not scored item by item.
func wholeAnswerItems<Q: ExerciseQuestion>(_ labelOf: @escaping (Q.Answer) -> String) -> (Q, Q.Answer) -> [SummaryItem] {
    { question, answer in
        [SummaryItem(label: labelOf(question.correctAnswer), chosenLabel: labelOf(answer), isCorrect: question.isCorrect(answer))]
    }
}
