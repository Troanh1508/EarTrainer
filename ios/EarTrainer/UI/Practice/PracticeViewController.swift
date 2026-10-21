import UIKit

/// Exercise-specific wording and presentation for the shared practice screen.
struct PracticeTexts<Q: ExerciseQuestion> {
    var title: String
    /// Which exercise this is; sets how the summary counts and words its score.
    var exercise: ExerciseKind
    var instruction: (Q) -> String
    /// Short description of how this question is played, e.g. "Harmonic".
    var questionDetail: (Q) -> String
    var choiceLabel: (Q.Answer) -> ChoiceLabel
    /// What the right answer was, shown after answering.
    var correctSummary: (Q) -> String
    /// Whether feedback lists the sounded notes; long prompts like progressions summarize them instead.
    var showNotesAsPlayed = true
    /// How a sounded note is written, e.g. "E♭4" rather than "D♯4" when the question is in a flat key.
    var noteName: (Q, Pitch) -> String = { _, pitch in pitch.name }
    /// Draws each choice instead of showing its text label (which is still read aloud).
    var choiceGraphic: ((Q.Answer) -> TintableGraphic)?
    /// Whether feedback repeats the chosen answer in words; drawn choices already show it.
    var showSelectedInFeedback = true
    var choiceColumns = 2
}

/// Where the listener responds; updated with every state change.
class AnswerArea<Q: ExerciseQuestion>: UIView {
    func update(_ state: PracticeUiState<Q>) {}
}

/// The usual multiple-choice grid, rebuilt for each question.
final class ChoiceAnswerArea<Q: ExerciseQuestion>: AnswerArea<Q> {
    private let texts: PracticeTexts<Q>
    private let onSelect: (Q.Answer) -> Void
    private var grid: AnswerGridView<Q.Answer>?
    private var gridKey: String?

    init(texts: PracticeTexts<Q>, onSelect: @escaping (Q.Answer) -> Void) {
        self.texts = texts
        self.onSelect = onSelect
        super.init(frame: .zero)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func update(_ state: PracticeUiState<Q>) {
        let key = "\(state.sessionId)#\(state.exercise.questionNumber)"
        if key != gridKey {
            gridKey = key
            grid?.removeFromSuperview()
            let grid = AnswerGridView(
                choices: state.question.choices, columns: texts.choiceColumns, label: texts.choiceLabel,
                graphic: texts.choiceGraphic, onSelect: onSelect
            )
            addSubview(grid)
            grid.pinEdges(to: self)
            self.grid = grid
        }
        grid?.update(state.appearance(of:))
    }

    /// The button showing the right answer, so it can be scrolled into view after a mistake.
    func correctButton(_ state: PracticeUiState<Q>) -> UIView? { grid?.button(for: state.question.correctAnswer) }
}

/**
 * Everything a listening exercise screen shares: title bar, instruction and score, the play
 * button, and the feedback panel with "Next". The answer area is where the listener responds.
 * Once a session ends, its summary takes over the screen.
 */
final class PracticeViewController<Q: ExerciseQuestion, S>: UIViewController {
    private let viewModel: PracticeViewModel<Q, S>
    private let texts: PracticeTexts<Q>
    private let makeSettings: (() -> UIViewController)?
    private let answerArea: AnswerArea<Q>

    private let scrollView = UIScrollView()
    private let strip = SessionStripView()
    private let instructionLabel = UILabel(style: .body)
    private let player = PromptPlayerView()
    private let feedback = FeedbackPanel()
    private var summaryController: PracticeSummaryViewController?
    private var questionKey: String?
    private var wasAnswered = false

    init(
        viewModel: PracticeViewModel<Q, S>, texts: PracticeTexts<Q>, makeSettings: (() -> UIViewController)?,
        answerArea: ((PracticeViewModel<Q, S>) -> AnswerArea<Q>)? = nil
    ) {
        self.viewModel = viewModel
        self.texts = texts
        self.makeSettings = makeSettings
        self.answerArea = answerArea?(viewModel) ?? ChoiceAnswerArea(texts: texts) { [weak viewModel] in viewModel?.onAnswerSelected($0) }
        super.init(nibName: nil, bundle: nil)
        title = texts.title
        navigationItem.largeTitleDisplayMode = .never
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = Palette.background
        if makeSettings != nil {
            let settings = UIBarButtonItem(image: UIImage(systemName: "slider.horizontal.3"), primaryAction: UIAction { [weak self] _ in
                guard let self, let screen = self.makeSettings?() else { return }
                self.navigationController?.pushViewController(screen, animated: true)
            })
            settings.accessibilityLabel = Strings.openSettings
            navigationItem.rightBarButtonItem = settings
        }

        let content = UIStackView.vertical([strip, instructionLabel, player, answerArea], spacing: 24)
        content.setCustomSpacing(16, after: instructionLabel)
        scrollView.alwaysBounceVertical = true
        view.addSubview(scrollView)
        scrollView.pinEdges(to: view)
        scrollView.addSubview(content)
        content.translatesAutoresizingMaskIntoConstraints = false
        let frame = scrollView.frameLayoutGuide
        let preferredWidth = content.widthAnchor.constraint(equalTo: frame.widthAnchor, constant: -48)
        preferredWidth.priority = .init(999)
        NSLayoutConstraint.activate([
            content.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 16),
            content.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -24),
            content.centerXAnchor.constraint(equalTo: frame.centerXAnchor),
            content.widthAnchor.constraint(lessThanOrEqualToConstant: 560),
            content.widthAnchor.constraint(lessThanOrEqualTo: frame.widthAnchor, constant: -32),
            preferredWidth,
        ])

        view.addSubview(feedback)
        feedback.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            feedback.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            feedback.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            feedback.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        feedback.isHidden = true

        player.onPlay = { [weak self] in self?.viewModel.onPlay() }
        player.onStop = { [weak self] in self?.viewModel.onStop() }
        feedback.onNext = { [weak self] in self?.viewModel.onNext() }
        viewModel.onChange = { [weak self] in self?.render() }

        NotificationCenter.default.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.viewModel.onScreenHidden() }
        }
        render()
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        viewModel.onScreenHidden()
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        // Leaving the exercise, not just opening its settings: stop audio for good.
        if isMovingFromParent || navigationController?.isBeingDismissed == true { viewModel.release() }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let covered = feedback.isHidden ? 0 : feedback.frame.height - view.safeAreaInsets.bottom
        if scrollView.contentInset.bottom != covered {
            scrollView.contentInset.bottom = covered
            scrollView.verticalScrollIndicatorInsets.bottom = covered
        }
    }

    private func render() {
        guard let state = viewModel.state else { return }
        if let summary = state.summary {
            showSummary(summary)
            return
        }
        hideSummary()

        strip.update(questionNumber: state.exercise.questionNumber, sessionLength: state.sessionLength, results: state.results, awaitingCurrent: state.answer == nil)
        instructionLabel.text = texts.instruction(state.question)
        player.update(detail: texts.questionDetail(state.question), playback: state.playback, hasHeardPrompt: state.hasHeardPrompt)
        answerArea.update(state)

        if let answer = state.answer {
            var details: [String] = []
            if texts.showNotesAsPlayed { details.append(state.notesAsPlayed { self.texts.noteName(state.question, $0) }) }
            if let response = state.responseMillis { details.append(Strings.responseTime(response)) }
            feedback.update(
                isCorrect: answer.isCorrect,
                selectedLabel: texts.showSelectedInFeedback ? texts.choiceLabel(answer.selectedAnswer).title : nil,
                correctSummary: texts.correctSummary(state.question),
                details: details.isEmpty ? nil : details.joined(separator: " · "),
                nextLabel: state.isLastQuestion ? Strings.seeResults : Strings.next
            )
        }
        let answered = state.answer != nil
        if feedback.isHidden == answered {
            feedback.isHidden = !answered
            view.setNeedsLayout()
        }

        // Once answered, bring the choices (and the revealed right one) into view above the
        // feedback panel. The next question starts back at the top.
        let key = "\(state.sessionId)#\(state.exercise.questionNumber)"
        if key != questionKey {
            questionKey = key
            wasAnswered = false
            scrollView.setContentOffset(CGPoint(x: 0, y: -scrollView.adjustedContentInset.top), animated: true)
        }
        if answered && !wasAnswered {
            wasAnswered = true
            DispatchQueue.main.async { [weak self] in self?.scrollToEnd() }
        }
    }

    private func scrollToEnd() {
        view.layoutIfNeeded()
        let bottom = scrollView.contentSize.height + scrollView.adjustedContentInset.bottom - scrollView.bounds.height
        let top = -scrollView.adjustedContentInset.top
        scrollView.setContentOffset(CGPoint(x: 0, y: max(top, bottom)), animated: true)
    }

    private func showSummary(_ summary: PracticeSummary) {
        navigationItem.rightBarButtonItem?.isHidden = true
        if let summaryController {
            summaryController.summary = summary
            return
        }
        let controller = PracticeSummaryViewController(
            summary: summary,
            exercise: texts.exercise,
            onRetryMissed: { [weak self] in self?.viewModel.onRetryMissed() },
            onNewSession: { [weak self] in self?.viewModel.onRestart() },
            onDone: { [weak self] in self?.navigationController?.popViewController(animated: true) }
        )
        addChild(controller)
        view.addSubview(controller.view)
        controller.view.pinEdges(to: view)
        controller.didMove(toParent: self)
        summaryController = controller
        UIAccessibility.post(notification: .screenChanged, argument: controller.view)
    }

    private func hideSummary() {
        navigationItem.rightBarButtonItem?.isHidden = false
        guard let summaryController else { return }
        summaryController.willMove(toParent: nil)
        summaryController.view.removeFromSuperview()
        summaryController.removeFromParent()
        self.summaryController = nil
        questionKey = nil
    }
}
