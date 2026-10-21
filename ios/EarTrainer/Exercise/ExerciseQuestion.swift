import Foundation

/// The exercise types the app offers; stored in history to tell sessions apart.
enum ExerciseKind: String, StoredEnum {
    case intervals = "INTERVALS"
    case chords = "CHORDS"
    case scaleDegrees = "SCALE_DEGREES"
    case progressions = "PROGRESSIONS"
    case dictation = "DICTATION"
    case rhythm = "RHYTHM"
}

/**
 * A single listening question. Each exercise provides its own implementation with its own
 * answer type.
 *
 * Correctness is always judged against `correctAnswer`, never against a label or position
 * shown in the UI.
 */
protocol ExerciseQuestion {
    associatedtype Answer: Hashable

    var prompt: ListeningPrompt { get }
    var choices: [Answer] { get }
    var correctAnswer: Answer { get }

    func isCorrect(_ answer: Answer) -> Bool

    /**
     * Whether [answer] is a well-formed answer to this question; anything else is ignored rather
     * than scored. Multiple-choice questions accept their choices; exercises where the answer is
     * built up (like dictation) define their own rule.
     */
    func accepts(_ answer: Answer) -> Bool
}

extension ExerciseQuestion {
    func isCorrect(_ answer: Answer) -> Bool { answer == correctAnswer }
    func accepts(_ answer: Answer) -> Bool { choices.contains(answer) }
}

/// Validates the invariants every generated question must satisfy.
func requireValidChoices<A: Hashable>(_ choices: [A], _ correctAnswer: A) {
    precondition(!choices.isEmpty, "A question needs answer choices")
    precondition(choices.contains(correctAnswer), "Choices \(choices) must contain the correct answer \(correctAnswer)")
    precondition(Set(choices).count == choices.count, "Choices must not contain duplicates: \(choices)")
}
