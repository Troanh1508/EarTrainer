import UIKit

enum PlaybackStatus {
    case idle, playing, failed
}

/// How an answer button should look; derived here so views contain no exercise logic.
enum ChoiceAppearance {
    case available, disabled, selectedCorrect, selectedIncorrect, revealedCorrect, dimmed
}

struct PracticeUiState<Q: ExerciseQuestion> {
    var exercise: ExerciseState<Q>
    var playback: PlaybackStatus = .idle
    /// This session's answered questions in order.
    var answered: [AnsweredQuestion<Q>] = []
    /// Questions in this session.
    var sessionLength: Int
    /// The results, once the last question has been answered and the listener moved on.
    var summary: PracticeSummary?
    /// Identifies the session, so views can tell a new session from a new question.
    var sessionId: String

    /// This session's answers in order, true where right; feeds the session strip.
    var results: [Bool] { answered.map(\.isCorrect) }
    var isLastQuestion: Bool { exercise.questionNumber >= sessionLength }

    var question: Q { exercise.question }
    var isPlaying: Bool { playback == .playing }
    var hasHeardPrompt: Bool { exercise.phase != .unheard }
    var canAnswer: Bool { exercise.phase == .awaitingAnswer }
    var answer: ExercisePhase<Q.Answer>.Answered? { exercise.phase.answered }

    /// From first hearing the question to answering it, once answered.
    var responseMillis: Int64? { exercise.responseMillis }

    func appearance(of choice: Q.Answer) -> ChoiceAppearance {
        guard let answer else { return canAnswer ? .available : .disabled }
        if choice == answer.selectedAnswer { return answer.isCorrect ? .selectedCorrect : .selectedIncorrect }
        return choice == question.correctAnswer ? .revealedCorrect : .dimmed
    }

    /// The notes in the order they sounded, e.g. "C4 → E4" or "C4 + E4 + G4", named by [nameOf].
    func notesAsPlayed(_ nameOf: (Pitch) -> String = { $0.name }) -> String {
        question.prompt.moments.map { $0.map(nameOf).joined(separator: " + ") }.joined(separator: " → ")
    }
}

/**
 * Drives any listen-and-answer exercise: playback, answering, scoring, advancing and recording.
 * Each exercise supplies how to make questions from its settings ([questionSource]) and how to
 * save an answered question ([recordAttempt]).
 *
 * Q is the question type, S the settings type.
 */
@MainActor
final class PracticeViewModel<Q: ExerciseQuestion, S> {
    /// Nil until the first session has started.
    private(set) var state: PracticeUiState<Q>? {
        didSet { onChange?() }
    }

    /// Called after every state change.
    var onChange: (() -> Void)?

    /// The settings the current session runs under.
    private(set) var currentSettings: S?

    private let settings: () -> S
    private let settingsChanged: ((S, S) -> Bool)?
    private let questionSource: (S) -> () -> Q
    private let recordAttempt: (ExerciseState<Q>, String) -> Void
    private let audioPlayer: AudioPlayer
    /// Splits an answer into its scored items for the summary.
    private let itemsOf: (Q, Q.Answer) -> [SummaryItem]
    /// This exercise's recent sessions, for comparing a finished session with the average.
    private let recentSessions: () async -> [SessionSummary]
    /// Questions in a session, after which `onNext` shows the summary.
    private let sessionLength: Int
    private let clock: () -> Int64

    private var engine: ExerciseEngine<Q>!
    private var sessionId = ""
    private var playbackTask: Task<Void, Never>?
    private var settingsObserver: NSObjectProtocol?

    /// Pause between prompts played back to back, so each is heard as its own example.
    private static var promptGapNanoseconds: UInt64 { 600_000_000 }

    /**
     * [settingsChanged] tells whether new settings differ from the session's; when given, a
     * change in the stored settings starts a fresh session with a fresh score.
     */
    init(
        settings: @escaping () -> S,
        settingsChanged: ((S, S) -> Bool)?,
        questionSource: @escaping (S) -> () -> Q,
        recordAttempt: @escaping (ExerciseState<Q>, String) -> Void,
        audioPlayer: AudioPlayer,
        itemsOf: @escaping (Q, Q.Answer) -> [SummaryItem],
        recentSessions: @escaping () async -> [SessionSummary] = { [] },
        sessionLength: Int = EarTrainer.sessionLength,
        clock: @escaping () -> Int64 = currentTimeMillis
    ) {
        self.settings = settings
        self.settingsChanged = settingsChanged
        self.questionSource = questionSource
        self.recordAttempt = recordAttempt
        self.audioPlayer = audioPlayer
        self.itemsOf = itemsOf
        self.recentSessions = recentSessions
        self.sessionLength = sessionLength
        self.clock = clock
        startSession(settings())
        if let settingsChanged {
            // A session is one run of questions under one set of settings, so changing settings
            // (e.g. from the settings screen) starts a fresh session with a fresh score.
            settingsObserver = NotificationCenter.default.addObserver(forName: .settingsDidChange, object: nil, queue: .main) { [weak self] _ in
                MainActor.assumeIsolated {
                    guard let self, let current = self.currentSettings else { return }
                    let latest = self.settings()
                    if settingsChanged(current, latest) { self.startSession(latest) }
                }
            }
        }
    }

    /// Plays the current question's prompt. Used for both the first listen and replays.
    func onPlay() {
        guard state != nil else { return }
        let previous = playbackTask
        playbackTask = Task { [weak self] in
            // Wait for the previous playback to finish releasing its audio before starting.
            previous?.cancel()
            await previous?.value
            guard let self, var current = self.state, !Task.isCancelled else { return }
            let session = self.sessionId
            let questionNumber = current.exercise.questionNumber
            current.playback = .playing
            current.exercise = current.exercise.promptPlayed(atMillis: self.clock())
            self.state = current
            let outcome: PlaybackStatus
            do {
                try await self.audioPlayer.play(current.question.prompt)
                outcome = .idle
            } catch {
                outcome = .failed
            }
            if Task.isCancelled { return }
            // Ignore results that arrive after the session has moved on to another question.
            if var latest = self.state, session == self.sessionId, latest.exercise.questionNumber == questionNumber {
                latest.playback = outcome
                self.state = latest
            }
        }
    }

    func onStop() { stopPlayback() }

    func onAnswerSelected(_ answer: Q.Answer) {
        guard var current = state, let next = current.exercise.submitAnswer(answer, atMillis: clock()), let answered = next.phase.answered else {
            // Invalid submissions (duplicate taps, not yet heard) change nothing.
            return
        }
        current.answered.append(AnsweredQuestion(
            question: next.question,
            answer: answered.selectedAnswer,
            isCorrect: answered.isCorrect,
            playCount: next.playCount,
            firstPlayedAtMillis: next.firstPlayedAtMillis ?? answered.answeredAtMillis,
            answeredAtMillis: answered.answeredAtMillis
        ))
        current.exercise = next
        state = current
        recordAttempt(next, sessionId)
        UIAccessibility.post(notification: .announcement, argument: answered.isCorrect ? Strings.feedbackCorrect : Strings.feedbackIncorrect)
    }

    func onNext() {
        guard var current = state, current.answer != nil else { return }
        stopPlayback()
        if current.isLastQuestion {
            finishSession(current)
            return
        }
        current.exercise = engine.advance(current.exercise)
        current.playback = .idle
        state = current
        onPlay()
    }

    /**
     * Plays [prompts] one after another, e.g. reference examples before answering. Unlike
     * `onPlay` this does not count as hearing the question.
     */
    func onPlayPrompts(_ prompts: [ListeningPrompt]) {
        guard state != nil, !prompts.isEmpty else { return }
        let previous = playbackTask
        playbackTask = Task { [weak self] in
            previous?.cancel()
            await previous?.value
            guard let self, !Task.isCancelled else { return }
            let session = self.sessionId
            self.state?.playback = .playing
            var outcome = PlaybackStatus.idle
            do {
                for (index, prompt) in prompts.enumerated() {
                    if index > 0 { try await Task.sleep(nanoseconds: Self.promptGapNanoseconds) }
                    try await self.audioPlayer.play(prompt)
                    if Task.isCancelled { return }
                }
            } catch is CancellationError {
                return
            } catch {
                outcome = .failed
            }
            if Task.isCancelled { return }
            if session == self.sessionId { self.state?.playback = outcome }
        }
    }

    /// Starts a fresh session with the same settings: new questions, a new score.
    func onRestart() {
        if let currentSettings { startSession(currentSettings) }
    }

    /**
     * Starts a short session of the questions just missed, in a new order, under the same
     * settings. Does nothing before a session has finished or when nothing was missed.
     */
    func onRetryMissed() {
        guard let currentSettings, let current = state, current.summary != nil else { return }
        let missed = current.answered.filter { !$0.isCorrect }.map(\.question)
        guard !missed.isEmpty else { return }
        var queue = missed.shuffled()
        startSession(currentSettings, nextQuestion: { queue.removeFirst() }, length: missed.count)
    }

    /// Called when the screen leaves the foreground so audio never keeps playing in the background.
    func onScreenHidden() { stopPlayback() }

    /// Stops playback for good; called when the screen closes.
    func release() {
        playbackTask?.cancel()
        audioPlayer.release()
        if let settingsObserver { NotificationCenter.default.removeObserver(settingsObserver) }
        settingsObserver = nil
    }

    private func startSession(_ settings: S) {
        startSession(settings, nextQuestion: questionSource(settings), length: sessionLength)
    }

    private func startSession(_ settings: S, nextQuestion: @escaping () -> Q, length: Int) {
        stopPlayback()
        currentSettings = settings
        engine = ExerciseEngine(nextQuestion: nextQuestion)
        sessionId = UUID().uuidString.lowercased()
        state = PracticeUiState(exercise: engine.start(), sessionLength: length, sessionId: sessionId)
    }

    /// Shows the results, then adds the comparison with recent sessions once it has loaded.
    private func finishSession(_ current: PracticeUiState<Q>) {
        let session = sessionId
        var finished = current
        finished.summary = PracticeSummary.from(current.answered, itemsOf: itemsOf)
        state = finished
        Task { [weak self] in
            guard let self else { return }
            let recent = await self.recentSessions()
            guard session == self.sessionId, let summary = self.state?.summary else { return }
            self.state?.summary = summary.compared(with: recent, currentSessionId: session)
        }
    }

    private func stopPlayback() {
        // Keep the task so the next play can wait for its cleanup.
        playbackTask?.cancel()
        if state?.isPlaying == true { state?.playback = .idle }
    }
}

extension PracticeViewModel where S: Equatable {
    /// A view model whose settings come from [store], restarting the session when they change.
    convenience init(
        store: SettingsStore<S>,
        questionSource: @escaping (S) -> () -> Q,
        recordAttempt: @escaping (ExerciseState<Q>, String) -> Void,
        audioPlayer: AudioPlayer,
        itemsOf: @escaping (Q, Q.Answer) -> [SummaryItem],
        recentSessions: @escaping () async -> [SessionSummary]
    ) {
        self.init(
            settings: { store.current },
            settingsChanged: { $0 != $1 },
            questionSource: questionSource,
            recordAttempt: recordAttempt,
            audioPlayer: audioPlayer,
            itemsOf: itemsOf,
            recentSessions: recentSessions
        )
    }
}
