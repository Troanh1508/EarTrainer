import UIKit

/// What one item was answered as, with advice and drills for the pairs most often mixed up.
final class MistakesViewController: ScrollingViewController {
    private let filter: ProgressFilter
    private var itemLabel: String
    private let history = AppContainer.shared.history
    private var state: MistakesUiState?
    private var loadTask: Task<Void, Never>?
    private var observer: NSObjectProtocol?
    private let subtitleLabel = UILabel(style: .caption1, color: Palette.secondaryText, alignment: .center)

    init(filter: ProgressFilter, itemLabel: String) {
        self.filter = filter
        self.itemLabel = itemLabel
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func viewDidLoad() {
        super.viewDidLoad()
        navigationItem.largeTitleDisplayMode = .never
        let titleLabel = UILabel(Strings.mistakesTitle, style: .headline, alignment: .center)
        subtitleLabel.text = Strings.mistakesScope(Strings.exerciseName(filter.exercise), filter.label)
        navigationItem.titleView = UIStackView.vertical([titleLabel, subtitleLabel], alignment: .center)
        observer = NotificationCenter.default.addObserver(forName: .practiceHistoryDidChange, object: nil, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.load() }
        }
        load()
    }

    private func load() {
        let filter = filter
        let label = itemLabel
        let history = history
        loadTask?.cancel()
        loadTask = Task { [weak self] in
            async let accuracy = history.accuracy(for: filter)
            async let confusions = history.confusions(for: filter)
            let state = MistakesUiState.from(filter: filter, accuracy: await accuracy, confusions: await confusions, selectedLabel: label)
            guard let self, !Task.isCancelled else { return }
            self.state = state
            self.render()
        }
    }

    private func render() {
        contentStack.removeAllArrangedSubviews()
        guard let state else { return }
        contentStack.addArrangedSubviews([answersCard(state), pairsCard(state)])
    }

    private func answersCard(_ state: MistakesUiState) -> UIView {
        let slices = pieSlices(state)
        let total = slices.reduce(0) { $0 + $1.count }
        let card = CardView(spacing: 16)

        var configuration = UIButton.Configuration.bordered()
        configuration.cornerStyle = .capsule
        configuration.baseForegroundColor = Palette.text
        configuration.title = Strings.itemOption(state.selected.label, percent(state.selected.accuracy ?? 0))
        let picker = MenuButton(configuration: configuration, menu: UIMenu(title: Strings.chooseItem, children: state.items.map { item in
            UIAction(title: Strings.itemOption(item.label, percent(item.accuracy ?? 0)), state: item.label == state.selected.label ? .on : .off) { [weak self] _ in
                self?.itemLabel = item.label
                self?.load()
            }
        }))
        picker.accessibilityHint = Strings.chooseItem
        card.stack.addArrangedSubview(UIStackView.vertical([
            UILabel(Strings.whenLabel(state.exercise), style: .subheadline, color: Palette.secondaryText),
            UIStackView.horizontal([picker, .spacer()]),
        ], spacing: 8))

        let shares = slices.map { "\($0.label) \(sharePercent($0.count, total))" }.joined(separator: ", ")
        let pie = PieView(slices: slices, gapColor: Palette.card, total: total, description: Strings.pieDescription(state.selected.label, shares))
        card.stack.addArrangedSubview(UIStackView.vertical([pie], alignment: .center))

        let legend = UIStackView.vertical([UILabel(Strings.youAnswered, style: .subheadline, color: Palette.secondaryText)], spacing: 10)
        for slice in slices {
            let swatch = UIView()
            swatch.backgroundColor = slice.color
            swatch.layer.cornerRadius = 4
            swatch.constrainSize(width: 14, height: 14)
            let name = UILabel(slice.label, style: .body, weight: slice.isRight ? .bold : .regular)
            let count = UILabel(Strings.count(slice.count), style: .subheadline, color: Palette.secondaryText)
            let share = UILabel(sharePercent(slice.count, total), style: .body, weight: .bold, alignment: .right)
            share.widthAnchor.constraint(equalToConstant: 52).isActive = true
            count.setContentHuggingPriority(.required, for: .horizontal)
            let row = UIStackView.horizontal([swatch, name, count, share], spacing: 10)
            row.isAccessibilityElement = true
            row.accessibilityLabel = "\(slice.label), \(Strings.count(slice.count)), \(sharePercent(slice.count, total))"
            legend.addArrangedSubview(row)
        }
        card.stack.addArrangedSubview(legend)
        card.stack.addArrangedSubview(tip(state))
        return card
    }

    /**
     * The right answer, the two most common wrong answers and everything else together, so the pie
     * stays readable however many different answers were given.
     */
    private func pieSlices(_ state: MistakesUiState) -> [PieSlice] {
        let wrongColors = [Palette.weakSpot, Palette.weakSpot.withAlphaComponent(0.5)]
        let right = state.answers.filter(\.isRight).map {
            PieSlice(label: Strings.rightAnswer(state.selected.label), count: $0.count, color: Palette.primary, isRight: true)
        }
        let wrong = state.answers.filter { !$0.isRight }
        let named = wrong.prefix(wrongColors.count).enumerated().map { index, share in
            PieSlice(label: share.label ?? Strings.somethingElse, count: share.count, color: wrongColors[index], isRight: false)
        }
        let rest = wrong.dropFirst(wrongColors.count)
        let other = rest.isEmpty ? [] : [PieSlice(label: Strings.otherAnswers, count: rest.reduce(0) { $0 + $1.count }, color: .systemGray, isRight: false)]
        return right + named + other
    }

    private func tip(_ state: MistakesUiState) -> UIView {
        guard let mistake = state.mostCommonMistake else {
            let card = CardView(color: Palette.tertiaryContainer, padding: 14, cornerRadius: 16)
            card.stack.addArrangedSubview(UILabel(Strings.mistakesNone, style: .subheadline, color: Palette.onTertiaryContainer))
            return card
        }
        let selected = state.selected.label
        let card = CardView(color: Palette.weakSpotContainer, spacing: 12, padding: 16, cornerRadius: 16)
        let text = NSMutableAttributedString()
        let font = UIFont.app(.subheadline)
        if let mixedUpWith = mistake.label {
            text.append(NSAttributedString(string: Strings.tipLead(mixedUpWith) + " ", attributes: [.font: UIFont.app(.subheadline, weight: .bold)]))
            let advice = state.semitonesBetween(selected, mixedUpWith).flatMap { $0 > 0 ? Strings.tipApart($0) : nil } ?? Strings.tipGeneral
            text.append(NSAttributedString(string: advice, attributes: [.font: font]))
        } else {
            text.append(NSAttributedString(string: Strings.tipSomethingElse, attributes: [.font: font]))
        }
        let label = UILabel(style: .subheadline, color: Palette.onWeakSpotContainer)
        label.attributedText = text
        card.stack.addArrangedSubview(label)
        if let mixedUpWith = mistake.label, let request = state.comparison(selected, mixedUpWith) {
            // Short names fit the button; the full names are what VoiceOver says.
            let button = actionButton(
                Strings.comparePair(state.shortLabel(of: selected), state.shortLabel(of: mixedUpWith)), filled: true,
                color: Palette.weakSpot, foreground: Palette.onWeakSpot
            ) { [weak self] in self?.openComparison(request) }
            button.accessibilityLabel = Strings.comparePair(selected, mixedUpWith)
            card.stack.addArrangedSubview(UIStackView.horizontal([button, .spacer()]))
        }
        return card
    }

    private func pairsCard(_ state: MistakesUiState) -> UIView {
        let card = CardView(spacing: 14)
        card.stack.addArrangedSubview(UILabel(Strings.pairsTitle, style: .headline))
        if state.confusedPairs.isEmpty {
            card.stack.addArrangedSubview(UILabel(Strings.pairsNone, style: .subheadline, color: Palette.secondaryText))
        }
        let most = state.confusedPairs.map(\.count).max() ?? 1
        for pair in state.confusedPairs {
            let name = UILabel(Strings.pair(pair.first, pair.second), style: .subheadline, weight: .semibold)
            let header = UIStackView.horizontal([name, .spacer()], spacing: 8)
            if let request = state.comparison(pair.first, pair.second) {
                let compare = UIButton(configuration: .plain(), primaryAction: UIAction(title: Strings.compare) { [weak self] _ in self?.openComparison(request) })
                compare.accessibilityLabel = Strings.comparePair(pair.first, pair.second)
                header.addArrangedSubview(compare)
            }
            let bar = BarView(height: 10, fraction: CGFloat(pair.count) / CGFloat(most), fill: Palette.weakSpot)
            let count = UILabel(Strings.pairCount(pair.count), style: .subheadline, color: Palette.secondaryText)
            count.setContentHuggingPriority(.required, for: .horizontal)
            count.setContentCompressionResistancePriority(.required, for: .horizontal)
            card.stack.addArrangedSubview(UIStackView.vertical([header, UIStackView.horizontal([bar, count], spacing: 10)], spacing: 4))
        }
        return card
    }

    private func openComparison(_ request: ComparisonRequest) {
        guard let screen = ComparisonScreens.make(request) else { return }
        navigationController?.pushViewController(screen, animated: true)
    }

    private func sharePercent(_ count: Int, _ total: Int) -> String {
        total == 0 ? "0%" : "\(Int((Double(count) * 100 / Double(total)).rounded()))%"
    }
}
