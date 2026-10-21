import UIKit

/// How a drill names and draws its answers.
struct ComparisonTexts<Q: ExerciseQuestion> {
    var labelOf: (Q.Answer) -> String
    /// How the two examples are made alike, e.g. "Both start from the same note."
    var examplesHint: String
    /// What is being compared, under the title; by default the two answers' labels.
    var subtitle: String?
    /// Draws each choice instead of writing it, e.g. rhythm notation; the label is still read aloud.
    var graphicOf: ((Q.Answer) -> TintableGraphic)?
    var columns = 2
    /// Shorter names for the example buttons, when the answers' labels are long.
    var exampleLabels: [String]?
    /**
     * Feedback to show instead of naming both answers, for drawn choices whose marks already
     * show what was chosen and what was right.
     */
    var answerSummary: String?
}

/**
 * A drill on two answers that are easily mixed up: listen to one example of each, then a round
 * of `comparisonQuestionsPerRound` questions with only those two choices, then the score.
 */
final class ComparisonViewController<C: Comparison>: UIViewController {
    typealias Q = C.Question

    private let comparison: C
    private let viewModel: PracticeViewModel<Q, C>
    private let texts: ComparisonTexts<Q>

    private let scrollView = UIScrollView()
    private let content = UIStackView.vertical(spacing: 24)
    private let progressLabel = UILabel(style: .headline)
    private let rightLabel = UILabel(style: .subheadline, color: Palette.secondaryText)
    private let progressBar = BarView(height: 6)
    private var playButton: UIButton!
    private var playBothButton: UIButton!
    private let answers = UIView()
    private var grid: AnswerGridView<Q.Answer>?
    private let feedback = FeedbackPanel()
    private let result = UIStackView.vertical(spacing: 16, alignment: .fill)
    private var showingResult = false
    private var questionKey: String?
    private var wasAnswered = false

    /// Share of a round that counts as telling the two apart reliably.
    private static var goodShare: Double { 0.9 }

    init(comparison: C, texts: ComparisonTexts<Q>, record: @escaping (ExerciseState<Q>, String) -> Void) {
        self.comparison = comparison
        self.texts = texts
        viewModel = PracticeViewModel(
            settings: { comparison },
            settingsChanged: nil,
            questionSource: { drill in drill.nextQuestion },
            recordAttempt: record,
            audioPlayer: AppContainer.shared.makeAudioPlayer(),
            // A round ends on the drill's own result screen, which shows only the score.
            itemsOf: { _, _ in [] },
            sessionLength: comparisonQuestionsPerRound
        )
        super.init(nibName: nil, bundle: nil)
        navigationItem.largeTitleDisplayMode = .never
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = Palette.background
        let first = texts.labelOf(comparison.answers[0])
        let second = texts.labelOf(comparison.answers[1])
        let titleLabel = UILabel(Strings.compareTitle, style: .headline, alignment: .center)
        let subtitle = UILabel(texts.subtitle ?? Strings.compareSubtitle(first, second), style: .caption1, color: Palette.secondaryText, alignment: .center)
        navigationItem.titleView = UIStackView.vertical([titleLabel, subtitle], alignment: .center)

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

        let examples = examplesCard(texts.exampleLabels ?? [first, second])
        rightLabel.setContentHuggingPriority(.required, for: .horizontal)
        let progress = UIStackView.vertical([UIStackView.horizontal([progressLabel, rightLabel], spacing: 8), progressBar], spacing: 8)
        playButton = actionButton(Strings.play, filled: true, icon: "play.fill") { [weak self] in
            guard let self, let state = self.viewModel.state else { return }
            if state.isPlaying { self.viewModel.onStop() } else { self.viewModel.onPlay() }
        }
        playButton.constrainMinHeight(60)
        let play = UIStackView.vertical([playButton], alignment: .center)
        playButton.widthAnchor.constraint(greaterThanOrEqualToConstant: 200).isActive = true
        content.addArrangedSubviews([examples, progress, play, answers, result])
        result.isHidden = true

        view.addSubview(feedback)
        feedback.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            feedback.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            feedback.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            feedback.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        feedback.isHidden = true
        feedback.onNext = { [weak self] in
            guard let self, let state = self.viewModel.state else { return }
            if state.exercise.questionNumber >= comparisonQuestionsPerRound && state.answer != nil {
                self.showingResult = true
                self.render()
            } else {
                self.viewModel.onNext()
            }
        }
        viewModel.onChange = { [weak self] in self?.render() }
        render()
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        viewModel.onScreenHidden()
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        if isMovingFromParent { viewModel.release() }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let covered = feedback.isHidden ? 0 : feedback.frame.height - view.safeAreaInsets.bottom
        scrollView.contentInset.bottom = covered
        scrollView.verticalScrollIndicatorInsets.bottom = covered
    }

    /// One button per example and one for both, so the difference can be heard before answering.
    private func examplesCard(_ labels: [String]) -> UIView {
        let card = CardView(spacing: 12)
        card.stack.addArrangedSubviews([
            UILabel(Strings.compareListenTitle, style: .headline),
            UILabel(texts.examplesHint, style: .subheadline, color: Palette.secondaryText),
        ])
        for (index, label) in labels.enumerated() {
            let button = actionButton(Strings.playExample(label), filled: false, icon: "play.fill") { [weak self] in
                guard let self else { return }
                self.viewModel.onPlayPrompts([self.comparison.examples[index]])
            }
            card.stack.addArrangedSubview(button)
        }
        playBothButton = UIButton(configuration: .plain(), primaryAction: UIAction(title: Strings.playBoth) { [weak self] _ in
            guard let self else { return }
            self.viewModel.onPlayPrompts(self.comparison.examples)
        })
        card.stack.addArrangedSubview(playBothButton)
        return card
    }

    private func render() {
        guard let state = viewModel.state else { return }
        let total = comparisonQuestionsPerRound
        let key = "\(state.sessionId)#\(state.exercise.questionNumber)"
        if key != questionKey {
            questionKey = key
            wasAnswered = false
            if grid == nil || state.exercise.questionNumber == 1 { rebuildGrid() }
        }
        content.arrangedSubviews.forEach { $0.isHidden = showingResult && $0 !== result }
        result.isHidden = !showingResult
        if showingResult {
            renderResult(correct: state.exercise.score.correct, total: state.exercise.score.answered)
            feedback.isHidden = true
            view.setNeedsLayout()
            return
        }

        progressLabel.text = Strings.compareQuestion(state.exercise.questionNumber, total)
        rightLabel.text = Strings.rightSoFar(state.exercise.score.correct)
        progressBar.fraction = CGFloat(state.exercise.questionNumber) / CGFloat(total)
        var configuration = playButton.configuration
        let (title, symbol) = state.isPlaying ? (Strings.stop, "stop.fill") : state.hasHeardPrompt ? (Strings.replay, "arrow.clockwise") : (Strings.play, "play.fill")
        configuration?.attributedTitle = AttributedString(title, attributes: AttributeContainer([.font: UIFont.app(.headline, weight: .semibold)]))
        configuration?.image = UIImage(systemName: symbol)
        playButton.configuration = configuration
        playBothButton.isEnabled = !state.isPlaying
        grid?.update(state.appearance(of:))

        if let answer = state.answer {
            let roundFinished = state.exercise.questionNumber >= total
            feedback.update(
                isCorrect: answer.isCorrect,
                selectedLabel: texts.answerSummary == nil ? texts.labelOf(answer.selectedAnswer) : nil,
                correctSummary: texts.answerSummary ?? Strings.itWas(texts.labelOf(state.question.correctAnswer)),
                details: nil,
                nextLabel: roundFinished ? Strings.seeResult : Strings.next
            )
        }
        let answered = state.answer != nil
        if feedback.isHidden == answered {
            feedback.isHidden = !answered
            view.setNeedsLayout()
        }
        if answered && !wasAnswered {
            wasAnswered = true
            // Show the end of the page so both choices stay in view above the feedback panel.
            DispatchQueue.main.async { [weak self] in
                guard let self else { return }
                self.view.layoutIfNeeded()
                let bottom = self.scrollView.contentSize.height + self.scrollView.adjustedContentInset.bottom - self.scrollView.bounds.height
                self.scrollView.setContentOffset(CGPoint(x: 0, y: max(-self.scrollView.adjustedContentInset.top, bottom)), animated: true)
            }
        }
    }

    private func rebuildGrid() {
        grid?.removeFromSuperview()
        let grid = AnswerGridView(
            choices: comparison.answers, columns: texts.columns, label: { [texts] in ChoiceLabel(title: texts.labelOf($0)) },
            graphic: texts.graphicOf, onSelect: { [weak self] in self?.viewModel.onAnswerSelected($0) }
        )
        answers.addSubview(grid)
        grid.pinEdges(to: answers)
        self.grid = grid
    }

    private func renderResult(correct: Int, total: Int) {
        result.removeAllArrangedSubviews()
        let score = UILabel(Strings.compareResult(correct, total), style: .largeTitle, weight: .bold, alignment: .center)
        let message = UILabel(
            Double(correct) >= Double(total) * Self.goodShare ? Strings.compareResultGood : Strings.compareResultKeepGoing,
            style: .body, color: Palette.secondaryText, alignment: .center
        )
        let again = actionButton(Strings.compareAgain, filled: true) { [weak self] in
            self?.showingResult = false
            self?.viewModel.onRestart()
        }
        let done = actionButton(Strings.done, filled: false) { [weak self] in self?.navigationController?.popViewController(animated: true) }
        result.addArrangedSubviews([UIView.padded(score, UIEdgeInsets(top: 32, left: 0, bottom: 0, right: 0)), message, again, done])
        UIAccessibility.post(notification: .screenChanged, argument: score)
    }
}

/// Builds the drill for a request. The drill's answers are recorded like ordinary practice of that
/// exercise; a dictation mix-up is drilled, and recorded, as scale degrees.
@MainActor
enum ComparisonScreens {
    static func make(_ request: ComparisonRequest) -> UIViewController? {
        let history = AppContainer.shared.history
        let hint = Strings.compareHint(request.exercise)
        switch request.exercise {
        case .intervals:
            guard let first = Interval.named(request.first), let second = Interval.named(request.second), first != second else { return nil }
            let style = request.scope.flatMap(IntervalStyle.named)
            return ComparisonViewController(
                comparison: IntervalComparison(first, second, style: style),
                texts: ComparisonTexts(labelOf: \.displayName, examplesHint: hint),
                record: { state, session in IntervalAttempt(state, sessionId: session).map { history.record($0) } }
            )
        case .chords:
            let target = request.scope.flatMap(ChordTarget.named) ?? .quality
            guard let first = chordAnswer(target, request.first), let second = chordAnswer(target, request.second), first != second else { return nil }
            return ComparisonViewController(
                comparison: ChordComparison(first, second),
                texts: ComparisonTexts(labelOf: \.displayName, examplesHint: hint),
                record: { state, session in ChordAttempt(state, sessionId: session).map { history.record($0) } }
            )
        case .scaleDegrees, .dictation:
            guard let first = ScaleDegree.named(request.first), let second = ScaleDegree.named(request.second), first != second else { return nil }
            let mode = request.scope.flatMap(Mode.named) ?? ScaleDegreeComparison.mode(for: first, second)
            return ComparisonViewController(
                comparison: ScaleDegreeComparison(first, second, mode: mode),
                texts: ComparisonTexts(labelOf: degreeLabel, examplesHint: hint),
                record: { state, session in ScaleDegreeAttempt(state, sessionId: session).map { history.record($0) } }
            )
        case .progressions:
            // Progressions are always looked at one mode at a time, and the chords are scale steps.
            guard let mode = request.scope.flatMap(Mode.named),
                  let first = Int(request.first), let second = Int(request.second),
                  (1...7).contains(first), (1...7).contains(second), first != second else { return nil }
            return ComparisonViewController(
                comparison: ProgressionChordComparison(DiatonicChord(mode, first), DiatonicChord(mode, second)),
                texts: ComparisonTexts(labelOf: { $0.chords.last!.numeral }, examplesHint: hint),
                record: { state, session in ProgressionAttempt(state, sessionId: session).map { history.record($0) } }
            )
        case .rhythm:
            guard let first = RhythmCell.named(request.first), let second = RhythmCell.named(request.second), first != second else { return nil }
            let ordered = [first, second].sorted { $0.ordinal < $1.ordinal }
            var texts = ComparisonTexts<RhythmQuestion>(labelOf: { $0.description.capitalizedFirst }, examplesHint: hint)
            texts.subtitle = Strings.compareSubtitle(first.displayName.capitalizedFirst, second.displayName)
            texts.graphicOf = { RhythmNotationView($0) }
            // Whole rhythms are long to read out; each example is named by how it starts.
            texts.answerSummary = Strings.rhythmSummary
            texts.exampleLabels = ordered.map { Strings.startsWith($0.displayName) }
            // Notation needs the full width to stay legible.
            texts.columns = 1
            return ComparisonViewController(
                comparison: RhythmCellComparison(first, second),
                texts: texts,
                record: { state, session in RhythmAttempt(state, sessionId: session).map { history.record($0) } }
            )
        }
    }

    private static func chordAnswer(_ target: ChordTarget, _ name: String) -> ChordAnswer? {
        switch target {
        case .quality: ChordQuality.named(name).map(ChordAnswer.quality)
        case .inversion: ChordInversion.named(name).map(ChordAnswer.inversion)
        }
    }
}
