import UIKit

/**
 * Accuracy per exercise: overall, over time and item by item, with recent sessions. Tapping an
 * item shows what it was mistaken for.
 */
final class ProgressViewController: ScrollingViewController {
    static let recentSessionLimit = 10
    static let trendSessionLimit = 12

    private let history = AppContainer.shared.history
    private var filter: ProgressFilter = .intervalStyle(nil)
    private var state: ProgressUiState?
    private var showScoring = false
    private var loadTask: Task<Void, Never>?
    private var observer: NSObjectProtocol?

    override func viewDidLoad() {
        super.viewDidLoad()
        title = Strings.progressTitle
        navigationItem.largeTitleDisplayMode = .never
        let log = UIBarButtonItem(image: UIImage(systemName: "calendar"), primaryAction: UIAction { [weak self] _ in self?.openLog() })
        log.accessibilityLabel = Strings.logTitle
        navigationItem.rightBarButtonItem = log
        observer = NotificationCenter.default.addObserver(forName: .practiceHistoryDidChange, object: nil, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.load() }
        }
        load()
    }

    private func load() {
        let filter = filter
        let history = history
        loadTask?.cancel()
        loadTask = Task { [weak self] in
            async let accuracy = history.accuracy(for: filter)
            async let sessions = history.recentSessions(limit: Self.recentSessionLimit)
            async let trend = history.trend(for: filter, limit: Self.trendSessionLimit)
            let state = ProgressUiState(filter: filter, accuracy: await accuracy, recentSessions: await sessions, trend: (await trend).reversed())
            guard let self, !Task.isCancelled, self.filter == filter else { return }
            self.state = state
            self.render()
        }
    }

    private func select(_ newFilter: ProgressFilter) {
        guard newFilter != filter else { return }
        filter = newFilter
        load()
    }

    private func openLog() { navigationController?.pushViewController(PracticeLogViewController(), animated: true) }

    private func render() {
        guard let state else { return }
        contentStack.removeAllArrangedSubviews()
        guard state.hasHistory else {
            let empty = UILabel(Strings.progressEmpty, style: .body, color: Palette.secondaryText, alignment: .center)
            contentStack.addArrangedSubview(UIView.padded(empty, UIEdgeInsets(top: 120, left: 16, bottom: 0, right: 16)))
            return
        }
        contentStack.addArrangedSubview(exerciseTabs(state.exercise))
        // Exercises with a single view have nothing to filter.
        if state.filterOptions.count > 1 { contentStack.addArrangedSubview(filterMenu(state)) }
        contentStack.addArrangedSubviews([overviewCard(state), trendCard(state), accuracyCard(state)])

        let header = UIStackView.horizontal([
            UILabel(Strings.recentSessions, style: .title3, weight: .semibold),
            .spacer(),
            UIButton(configuration: .plain(), primaryAction: UIAction(title: Strings.logTitle) { [weak self] _ in self?.openLog() }),
        ])
        contentStack.setCustomSpacing(8, after: contentStack.arrangedSubviews.last!)
        contentStack.addArrangedSubview(UIView.padded(header, UIEdgeInsets(top: 12, left: 4, bottom: 0, right: 0)))
        let sessions = CardView(spacing: 14)
        state.recentSessions.forEach { sessions.stack.addArrangedSubview(sessionRow($0)) }
        contentStack.addArrangedSubview(sessions)
    }

    /// One capsule per exercise, scrolling sideways: six names do not fit side by side on a phone.
    private func exerciseTabs(_ selected: ExerciseKind) -> UIView {
        let scroll = UIScrollView()
        scroll.showsHorizontalScrollIndicator = false
        let row = UIStackView.horizontal(spacing: 8)
        for exercise in ExerciseKind.allCases {
            var configuration: UIButton.Configuration = exercise == selected ? .filled() : .gray()
            configuration.cornerStyle = .capsule
            configuration.title = Strings.exerciseName(exercise)
            if exercise == selected {
                configuration.baseBackgroundColor = Palette.primary
                configuration.baseForegroundColor = Palette.onPrimary
            } else {
                configuration.baseForegroundColor = Palette.text
            }
            let button = UIButton(configuration: configuration, primaryAction: UIAction { [weak self] _ in
                guard let self, self.filter.exercise != exercise else { return }
                self.select(ProgressFilter.options(for: exercise)[0])
            })
            button.accessibilityTraits = exercise == selected ? [.button, .selected] : .button
            row.addArrangedSubview(button)
        }
        scroll.addSubview(row)
        row.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            row.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
            row.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
            row.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor),
            row.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor),
            row.heightAnchor.constraint(equalTo: scroll.frameLayoutGuide.heightAnchor),
        ])
        scroll.constrainSize(height: 40)
        return scroll
    }

    private func filterMenu(_ state: ProgressUiState) -> UIView {
        let actions = state.filterOptions.map { option in
            UIAction(title: option.label, state: option == state.filter ? .on : .off) { [weak self] _ in self?.select(option) }
        }
        var configuration = UIButton.Configuration.bordered()
        configuration.cornerStyle = .capsule
        configuration.baseForegroundColor = Palette.primary
        let button = UIButton(configuration: configuration)
        button.menu = UIMenu(children: actions)
        button.showsMenuAsPrimaryAction = true
        button.changesSelectionAsPrimaryAction = true
        return UIStackView.horizontal([button, .spacer()])
    }

    private func overviewCard(_ state: ProgressUiState) -> UIView {
        let labels = ScoringLabels(state.exercise)
        let description = state.totalAttempts == 0 ? Strings.noAnswersForFilter : labels.overall(state.totalCorrect, state.totalAttempts)
        let card = CardView()
        let side = UIStackView.vertical(spacing: 12, alignment: .leading)
        if state.totalAttempts == 0 {
            side.addArrangedSubview(UILabel(description, style: .subheadline, color: Palette.secondaryText))
        } else {
            let total = UIStackView.vertical([
                UILabel(String(state.totalAttempts), style: .title2, weight: .bold),
                UILabel(labels.unit(state.totalAttempts), style: .subheadline, color: Palette.secondaryText),
            ])
            total.isAccessibilityElement = true
            total.accessibilityLabel = "\(state.totalAttempts) \(labels.unit(state.totalAttempts))"
            side.addArrangedSubview(total)
        }
        if let change = state.trendChange {
            let improved = change > 0
            let text = change > 0 ? Strings.changeUp(change) : change < 0 ? Strings.changeDown(-change) : Strings.changeSteady
            side.addArrangedSubview(PillLabel(
                text, background: improved ? Palette.tertiaryContainer : .tertiarySystemFill,
                foreground: improved ? Palette.onTertiaryContainer : Palette.secondaryText
            ))
        }
        card.stack.addArrangedSubview(UIStackView.horizontal([
            accuracyDonut(state.overallAccuracy, caption: labels.donutCaption, description: description), side,
        ], spacing: 20))

        var configuration = UIButton.Configuration.plain()
        configuration.title = Strings.scoringTitle
        configuration.image = UIImage(systemName: showScoring ? "info.circle.fill" : "info.circle")
        configuration.imagePadding = 6
        configuration.contentInsets = .zero
        let toggle = UIButton(configuration: configuration, primaryAction: UIAction { [weak self] _ in
            self?.showScoring.toggle()
            self?.render()
        })
        toggle.accessibilityValue = showScoring ? "Explanation shown" : "Explanation hidden"
        card.stack.addArrangedSubview(UIStackView.horizontal([toggle, .spacer()]))
        if showScoring { card.stack.addArrangedSubview(UILabel(labels.explanation, style: .subheadline, color: Palette.secondaryText)) }
        return card
    }

    private func trendCard(_ state: ProgressUiState) -> UIView {
        let trend = state.trend
        // Name the filter too, so it is clear which answers each point counts.
        let window: String? = trend.isEmpty ? nil
            : state.filterOptions.count > 1 ? Strings.trendWindowFiltered(state.filter.label, trend.count) : Strings.trendWindow(trend.count)
        let card = CardView()
        card.stack.addArrangedSubview(cardHeader(Strings.trendTitle, note: window))
        if trend.count >= 2 {
            card.stack.addArrangedSubview(TrendChartView(sessions: trend))
        } else {
            card.stack.addArrangedSubview(UILabel(Strings.trendNeedsMore, style: .subheadline, color: Palette.secondaryText))
        }
        return card
    }

    private func accuracyCard(_ state: ProgressUiState) -> UIView {
        let card = CardView(spacing: 8)
        card.stack.addArrangedSubview(cardHeader(state.filter.accuracyTitle, note: Strings.weakestFirst))
        let filter = state.filter
        for item in state.accuracy.weakestFirst {
            card.stack.addArrangedSubview(AccuracyRowView(item: item) { [weak self] in
                self?.navigationController?.pushViewController(MistakesViewController(filter: filter, itemLabel: item.label), animated: true)
            })
        }
        if state.totalAttempts > 0 { card.stack.addArrangedSubview(accuracyLegend()) }
        return card
    }
}

/// A card title with a short note at the end of the line.
func cardHeader(_ title: String, note: String?) -> UIView {
    let titleLabel = UILabel(title, style: .headline)
    titleLabel.accessibilityTraits = .header
    let row = UIStackView.horizontal([titleLabel], spacing: 12, alignment: .lastBaseline)
    if let note {
        let noteLabel = UILabel(note, style: .footnote, color: Palette.secondaryText, alignment: .right)
        noteLabel.setContentCompressionResistancePriority(.defaultHigh + 1, for: .horizontal)
        row.addArrangedSubview(noteLabel)
    }
    return row
}
