import UIKit

/**
 * The end of a session: the score against the recent average, time and replays, how each item
 * went, what was mistaken for what, and what to do next.
 */
final class PracticeSummaryViewController: ScrollingViewController {
    var summary: PracticeSummary {
        didSet { if summary != oldValue && isViewLoaded { rebuild() } }
    }

    private let exercise: ExerciseKind
    private let onRetryMissed: () -> Void
    private let onNewSession: () -> Void
    private let onDone: () -> Void
    private let actions = UIStackView.vertical(spacing: 10)

    init(summary: PracticeSummary, exercise: ExerciseKind, onRetryMissed: @escaping () -> Void, onNewSession: @escaping () -> Void, onDone: @escaping () -> Void) {
        self.summary = summary
        self.exercise = exercise
        self.onRetryMissed = onRetryMissed
        self.onNewSession = onNewSession
        self.onDone = onDone
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func viewDidLoad() {
        super.viewDidLoad()
        let bar = UIView()
        bar.backgroundColor = Palette.background
        view.addSubview(bar)
        bar.translatesAutoresizingMaskIntoConstraints = false
        bar.addSubview(actions)
        actions.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            bar.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            bar.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            bar.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            actions.topAnchor.constraint(equalTo: bar.topAnchor, constant: 12),
            actions.leadingAnchor.constraint(equalTo: bar.readableContentGuide.leadingAnchor),
            actions.trailingAnchor.constraint(equalTo: bar.readableContentGuide.trailingAnchor),
            actions.bottomAnchor.constraint(equalTo: bar.safeAreaLayoutGuide.bottomAnchor, constant: -8),
        ])
        rebuild()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let covered = (actions.superview?.frame.height ?? 0) - view.safeAreaInsets.bottom
        scrollView.contentInset.bottom = covered
        scrollView.verticalScrollIndicatorInsets.bottom = covered
    }

    private func rebuild() {
        contentStack.removeAllArrangedSubviews()
        contentStack.addArrangedSubviews([scoreSection(), statsRow()])
        if !summary.tallies.isEmpty { contentStack.addArrangedSubview(talliesCard()) }
        contentStack.addArrangedSubview(mistakesCard())

        actions.removeAllArrangedSubviews()
        if summary.missedQuestions > 0 {
            actions.addArrangedSubview(actionButton(Strings.retryMissed(summary.missedQuestions), filled: true, action: onRetryMissed))
            let row = UIStackView.horizontal(
                [actionButton(Strings.newSession, filled: false, action: onNewSession), actionButton(Strings.done, filled: false, action: onDone)],
                spacing: 10, alignment: .fill, distribution: .fillEqually
            )
            actions.addArrangedSubview(row)
        } else {
            actions.addArrangedSubview(actionButton(Strings.newSession, filled: true, action: onNewSession))
            actions.addArrangedSubview(actionButton(Strings.done, filled: false, action: onDone))
        }
    }

    private func scoreSection() -> UIView {
        let labels = ScoringLabels(exercise)
        let heading = UILabel(Strings.summaryComplete, style: .subheadline, weight: .bold, color: Palette.secondaryText)
        heading.accessibilityTraits = .header
        let ring = RingView(diameter: 180, lineWidth: 16)
        ring.fraction = summary.accuracy
        ring.content.addArrangedSubview(UILabel(Strings.score(summary.itemsRight, summary.items), style: .largeTitle, weight: .heavy))
        ring.content.addArrangedSubview(UILabel(Strings.summaryPercent(summary.percent, labels.donutCaption), style: .subheadline, color: Palette.secondaryText, alignment: .center))
        ring.isAccessibilityElement = true
        ring.accessibilityLabel = labels.sessionScore(summary.itemsRight, summary.items)
        let section = UIStackView.vertical([heading, ring], spacing: 12, alignment: .center)
        // Exercises scored item by item also say how many whole answers were right.
        if let fullyRight = labels.fullyRight {
            section.addArrangedSubview(UILabel(fullyRight(summary.questionsRight, summary.questions), style: .subheadline, color: Palette.secondaryText, alignment: .center))
        }
        if let points = summary.pointsAboveAverage {
            let (background, foreground, icon, text): (UIColor, UIColor, String, String) =
                points > 0 ? (Palette.tertiaryContainer, Palette.onTertiaryContainer, "chart.line.uptrend.xyaxis", Strings.aboveAverage(points))
                : points < 0 ? (Palette.weakSpotContainer, Palette.onWeakSpotContainer, "chart.line.downtrend.xyaxis", Strings.belowAverage(-points))
                : (UIColor.tertiarySystemFill, Palette.secondaryText, "equal", Strings.levelWithAverage)
            section.addArrangedSubview(PillLabel(text, background: background, foreground: foreground, icon: icon))
        }
        return section
    }

    private func statsRow() -> UIView {
        let seconds = summary.durationMillis / 1000
        let cards = [
            statCard(Strings.duration(seconds), Strings.durationLabel),
            statCard(Strings.seconds(summary.averageResponseMillis), Strings.perAnswer),
            statCard(String(summary.replays), Strings.replays(summary.replays)),
        ]
        return UIStackView.horizontal(cards, spacing: 10, alignment: .fill, distribution: .fillEqually)
    }

    private func statCard(_ value: String, _ label: String) -> UIView {
        let card = CardView(spacing: 4, padding: 14, cornerRadius: 18)
        card.stack.addArrangedSubviews([UILabel(value, style: .title2, weight: .heavy, lines: 1), UILabel(label, style: .caption1, color: Palette.secondaryText)])
        card.isAccessibilityElement = true
        card.accessibilityLabel = "\(value) \(label)"
        return card
    }

    /// Right and missed counts per item, on one scale so items that came up more have longer bars.
    private func talliesCard() -> UIView {
        let card = CardView(spacing: 12)
        let title = UILabel(Strings.thisSession, style: .headline, weight: .bold)
        title.accessibilityTraits = .header
        let legend = UIStackView.horizontal([legendKey(Palette.primary, Strings.right), legendKey(Palette.weakSpot, Strings.missed)], spacing: 12)
        legend.accessibilityElementsHidden = true
        card.stack.addArrangedSubview(UIStackView.horizontal([title, .spacer(), legend], spacing: 8))
        let most = summary.tallies.map(\.total).max() ?? 1
        for tally in summary.tallies {
            let label = UILabel(tally.label, style: .subheadline, weight: .semibold)
            label.widthAnchor.constraint(equalToConstant: 112).isActive = true
            let bars = TallyBarView(right: tally.right, missed: tally.missed, most: most)
            let score = UILabel(Strings.score(tally.right, tally.total), style: .subheadline, weight: .bold, alignment: .right)
            score.widthAnchor.constraint(equalToConstant: 44).isActive = true
            let row = UIStackView.horizontal([label, bars, score], spacing: 10)
            row.isAccessibilityElement = true
            row.accessibilityLabel = Strings.tallyDescription(tally.label, tally.right, tally.total)
            card.stack.addArrangedSubview(row)
        }
        return card
    }

    private func mistakesCard() -> UIView {
        if summary.mistakes.isEmpty {
            let card = CardView(color: Palette.tertiaryContainer)
            card.stack.addArrangedSubview(UILabel(Strings.noMistakes, style: .subheadline, weight: .bold, color: Palette.onTertiaryContainer))
            return card
        }
        let card = CardView(color: Palette.weakSpotContainer, spacing: 12)
        let title = UILabel(Strings.youMissed, style: .subheadline, weight: .heavy, color: Palette.onWeakSpotContainer)
        title.accessibilityTraits = .header
        let flow = FlowLayoutView()
        flow.setViews(summary.mistakes.map { mistake in
            let text = mistake.chosenLabel.map { Strings.mistake(mistake.label, $0) } ?? mistake.label
            return PillLabel(mistake.count > 1 ? Strings.mistakeTimes(text, mistake.count) : text, background: .systemBackground, foreground: Palette.text, weight: .bold)
        })
        card.stack.addArrangedSubviews([title, flow])
        return card
    }
}

/// A right bar and a missed bar side by side, scaled so the busiest item fills the width.
private final class TallyBarView: DrawingView {
    private let right: Int
    private let missed: Int
    private let most: Int

    init(right: Int, missed: Int, most: Int) {
        self.right = right
        self.missed = missed
        self.most = max(most, 1)
        super.init(frame: .zero)
        constrainSize(height: 14)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func draw(_ rect: CGRect) {
        let gap: CGFloat = 3
        let unit = (bounds.width - gap) / CGFloat(most)
        var x: CGFloat = 0
        for (count, color) in [(right, Palette.primary), (missed, Palette.weakSpot)] where count > 0 {
            color.setFill()
            let width = max(unit * CGFloat(count) - (right > 0 && missed > 0 ? gap / 2 : 0), 4)
            UIBezierPath(roundedRect: CGRect(x: x, y: 0, width: width, height: bounds.height), cornerRadius: 4).fill()
            x += width + gap
        }
    }
}
