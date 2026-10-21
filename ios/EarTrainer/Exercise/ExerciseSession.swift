import Foundation

/// Questions in a normal practice session; a session with this many answers is finished.
let sessionLength = 20

/// Where the current question is in its lifecycle. Playback status is tracked separately.
enum ExercisePhase<A: Hashable>: Equatable {
    /// A new question whose prompt has not been played yet; answers are not accepted.
    case unheard
    /// The prompt has been played at least once and an answer can be submitted.
    case awaitingAnswer
    /// An answer was submitted. The question is locked until the session advances.
    case answered(Answered)

    struct Answered: Equatable {
        let selectedAnswer: A
        let isCorrect: Bool
        let answeredAtMillis: Int64
    }

    var answered: Answered? {
        if case .answered(let answered) = self { return answered }
        return nil
    }
}

struct SessionScore: Equatable {
    var answered = 0
    var correct = 0

    func record(isCorrect: Bool) -> SessionScore {
        SessionScore(answered: answered + 1, correct: correct + (isCorrect ? 1 : 0))
    }
}

/**
 * Immutable snapshot of an exercise session. Transitions that are not valid in the current
 * phase return nil, so duplicate taps or out-of-order events are no-ops instead of corrupting
 * the score.
 *
 * Times are passed in by the caller rather than read from a clock, keeping this type pure.
 */
struct ExerciseState<Q: ExerciseQuestion> {
    var question: Q
    /// 1-based; also identifies the question so stale async results can be discarded.
    var questionNumber: Int
    var phase: ExercisePhase<Q.Answer>
    var score: SessionScore
    /// How many times the current question's prompt has been started, including replays.
    var playCount = 0
    var firstPlayedAtMillis: Int64?

    /// Records that the prompt started playing. Replays never touch the phase or score.
    func promptPlayed(atMillis: Int64) -> ExerciseState {
        var next = self
        if phase == .unheard { next.phase = .awaitingAnswer }
        next.playCount += 1
        next.firstPlayedAtMillis = firstPlayedAtMillis ?? atMillis
        return next
    }

    /// The state after answering, or nil if [answer] cannot be submitted now.
    func submitAnswer(_ answer: Q.Answer, atMillis: Int64) -> ExerciseState? {
        guard phase == .awaitingAnswer, question.accepts(answer) else { return nil }
        let isCorrect = question.isCorrect(answer)
        var next = self
        next.phase = .answered(.init(selectedAnswer: answer, isCorrect: isCorrect, answeredAtMillis: atMillis))
        next.score = score.record(isCorrect: isCorrect)
        return next
    }

    /// From the first time the prompt started playing until the answer, once answered.
    var responseMillis: Int64? {
        guard let answered = phase.answered else { return nil }
        return max(0, answered.answeredAtMillis - (firstPlayedAtMillis ?? answered.answeredAtMillis))
    }
}

/// Creates sessions and moves them on to new questions supplied by [nextQuestion].
final class ExerciseEngine<Q: ExerciseQuestion> {
    private let nextQuestion: () -> Q

    init(nextQuestion: @escaping () -> Q) {
        self.nextQuestion = nextQuestion
    }

    func start() -> ExerciseState<Q> {
        ExerciseState(question: nextQuestion(), questionNumber: 1, phase: .unheard, score: SessionScore())
    }

    /// Moves to a fresh question, keeping the score. Only allowed once the current one is answered.
    func advance(_ state: ExerciseState<Q>) -> ExerciseState<Q> {
        guard state.phase.answered != nil else { return state }
        var next = state
        next.question = nextQuestion()
        next.questionNumber += 1
        next.phase = .unheard
        next.playCount = 0
        next.firstPlayedAtMillis = nil
        return next
    }
}
