import UIKit

/// Streaks, a calendar of the last few weeks, this week's answers, and recent sessions.
final class PracticeLogViewController: ScrollingViewController {
    static let sessionLimit = 20

    private let history = AppContainer.shared.history
    private var loadTask: Task<Void, Never>?
    private var observer: NSObjectProtocol?

    /// Monday-first short weekday names in the device language.
    private let weekdays: [String] = {
        let names = Calendar.current.shortWeekdaySymbols
        return [1, 2, 3, 4, 5, 6, 0].map { names[$0] }
    }()

    override func viewDidLoad() {
        super.viewDidLoad()
        title = Strings.logTitle
        navigationItem.largeTitleDisplayMode = .never
        observer = NotificationCenter.default.addObserver(forName: .practiceHistoryDidChange, object: nil, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.load() }
        }
        load()
    }

    private func load() {
        let history = history
        loadTask?.cancel()
        loadTask = Task { [weak self] in
            async let days = history.answersPerDay()
            async let sessions = history.recentSessions(limit: Self.sessionLimit)
            let state = PracticeLogUiState.from(days: await days, sessions: await sessions, today: localEpochDay(currentTimeMillis()))
            guard let self, !Task.isCancelled else { return }
            self.render(state)
        }
    }

    private func render(_ state: PracticeLogUiState) {
        contentStack.removeAllArrangedSubviews()
        contentStack.addArrangedSubviews([streakCards(state), calendarCard(state), weekCard(state), sessionsCard(state)])
    }

    private func streakCards(_ state: PracticeLogUiState) -> UIView {
        func card(_ days: Int, _ label: String, background: UIColor, foreground: UIColor) -> UIView {
            let card = CardView(color: background, spacing: 4, padding: 18, cornerRadius: 24)
            card.stack.addArrangedSubviews([UILabel(Strings.days(days), style: .title2, weight: .bold, color: foreground), UILabel(label, style: .subheadline, color: foreground)])
            card.isAccessibilityElement = true
            card.accessibilityLabel = "\(Strings.days(days)), \(label)"
            return card
        }
        return UIStackView.horizontal([
            card(state.currentStreak, Strings.currentStreak, background: Palette.primary, foreground: Palette.onPrimary),
            card(state.longestStreak, Strings.longestStreak, background: Palette.card, foreground: Palette.text),
        ], spacing: 12, alignment: .fill, distribution: .fillEqually)
    }

    private func calendarCard(_ state: PracticeLogUiState) -> UIView {
        let card = CardView()
        card.stack.addArrangedSubview(cardHeader(Strings.calendarTitle(PracticeLogUiState.weeks), note: Strings.answersPerDay))
        let grid = UIStackView.vertical(spacing: 6)
        grid.addArrangedSubview(UIStackView.horizontal(
            weekdays.map { UILabel(String($0.prefix(1)), style: .caption2, color: Palette.secondaryText, alignment: .center) },
            spacing: 6, distribution: .fillEqually
        ))
        for start in stride(from: 0, to: state.calendar.count, by: 7) {
            let week = state.calendar[start..<min(start + 7, state.calendar.count)]
            grid.addArrangedSubview(UIStackView.horizontal(week.map(calendarCell), spacing: 6, distribution: .fillEqually))
        }
        grid.isAccessibilityElement = true
        grid.accessibilityLabel = Strings.calendarDescription(state.practicedDays, state.calendar.filter { !$0.isFuture }.count)
        card.stack.addArrangedSubview(grid)

        let legend = UIStackView.horizontal([UILabel(Strings.less, style: .caption2, color: Palette.secondaryText)], spacing: 5)
        for level in 0...4 {
            let swatch = UIView()
            swatch.backgroundColor = levelColor(level)
            swatch.layer.cornerRadius = 4
            swatch.constrainSize(width: 14, height: 14)
            legend.addArrangedSubview(swatch)
        }
        legend.addArrangedSubview(UILabel(Strings.more, style: .caption2, color: Palette.secondaryText))
        legend.accessibilityElementsHidden = true
        card.stack.addArrangedSubview(UIStackView.horizontal([.spacer(), legend]))
        return card
    }

    /// Darker for more practice; the shades differ in lightness, not only in color.
    private func levelColor(_ level: Int) -> UIColor {
        level == 0 ? Palette.track : Palette.primary.withAlphaComponent([0.25, 0.45, 0.7, 1][level - 1])
    }

    private func calendarCell(_ day: CalendarDay) -> UIView {
        let cell = UIView()
        cell.layer.cornerRadius = 8
        cell.layer.cornerCurve = .continuous
        cell.backgroundColor = day.isFuture ? .clear : levelColor(day.level)
        let border: UIColor? = day.isToday ? Palette.text : day.isFuture ? Palette.outline : nil
        if let border {
            cell.layer.borderWidth = day.isToday ? 2 : 1
            cell.layer.borderColor = border.resolvedColor(with: traitCollection).cgColor
            cell.registerForTraitChanges([UITraitUserInterfaceStyle.self]) { (view: UIView, _: UITraitCollection) in
                view.layer.borderColor = border.resolvedColor(with: view.traitCollection).cgColor
            }
        }
        cell.constrainSize(height: 36)
        return cell
    }

    private func weekCard(_ state: PracticeLogUiState) -> UIView {
        let card = CardView()
        card.stack.addArrangedSubview(cardHeader(Strings.thisWeek, note: Strings.answers(state.weekAnswers)))
        let most = max(state.week.map(\.answers).max() ?? 0, 1)
        let bars = UIStackView.horizontal(spacing: 10, alignment: .bottom, distribution: .fillEqually)
        bars.constrainSize(height: 120)
        for day in state.week {
            let column = UIStackView.vertical(spacing: 4, alignment: .fill)
            if !day.isFuture { column.addArrangedSubview(UILabel(String(day.answers), style: .caption2, weight: .bold, alignment: .center)) }
            let bar = UIView()
            bar.backgroundColor = day.isToday ? Palette.primary : Palette.primary.withAlphaComponent(0.45)
            bar.layer.cornerRadius = 6
            bar.layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner]
            bar.constrainSize(height: max(92 * CGFloat(day.answers) / CGFloat(most), day.answers > 0 ? 3 : 0))
            column.addArrangedSubview(bar)
            bars.addArrangedSubview(column)
        }
        bars.isAccessibilityElement = true
        bars.accessibilityLabel = zip(state.week, weekdays).map { "\($1) \($0.answers)" }.joined(separator: ", ")
        card.stack.addArrangedSubview(bars)
        let names = UIStackView.horizontal(weekdays.map { UILabel($0, style: .caption2, color: Palette.secondaryText, lines: 1, alignment: .center) }, spacing: 10, distribution: .fillEqually)
        names.accessibilityElementsHidden = true
        card.stack.addArrangedSubview(names)
        return card
    }

    private func sessionsCard(_ state: PracticeLogUiState) -> UIView {
        let card = CardView()
        card.stack.addArrangedSubview(UILabel(Strings.recentSessions, style: .headline))
        if !state.hasHistory { card.stack.addArrangedSubview(UILabel(Strings.progressEmpty, style: .subheadline, color: Palette.secondaryText)) }
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        formatter.doesRelativeDateFormatting = true
        for session in state.sessions {
            let labels = ScoringLabels(session.exercise)
            let share = session.items == 0 ? 0 : Double(session.itemsCorrect) / Double(session.items)
            let weak = share < AccuracyItem.weakThreshold
            let tile = UIView()
            tile.backgroundColor = Palette.primaryContainer
            tile.layer.cornerRadius = 12
            tile.constrainSize(width: 40, height: 40)
            let icon = UIImageView(image: session.exercise.icon)
            icon.tintColor = Palette.onPrimaryContainer
            tile.addSubview(icon)
            icon.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activate([icon.centerXAnchor.constraint(equalTo: tile.centerXAnchor), icon.centerYAnchor.constraint(equalTo: tile.centerYAnchor)])
            let name = Strings.exerciseName(session.exercise)
            let when = formatter.string(from: Date(timeIntervalSince1970: Double(session.startedAtMillis) / 1000))
            let left = UIStackView.vertical([UILabel(name, style: .body, weight: .semibold), UILabel(when, style: .footnote, color: Palette.secondaryText)], spacing: 2)
            let score = UILabel(labels.sessionScore(session.itemsCorrect, session.items), style: .subheadline, weight: .bold, color: weak ? Palette.weakSpot : Palette.text, alignment: .right)
            let bar = BarView(height: 6, fraction: share, fill: weak ? Palette.weakSpot : Palette.primary)
            bar.widthAnchor.constraint(equalToConstant: 72).isActive = true
            let right = UIStackView.vertical([score, bar], spacing: 6, alignment: .trailing)
            right.setContentCompressionResistancePriority(.required, for: .horizontal)
            let row = UIStackView.horizontal([tile, left, right], spacing: 12)
            row.isAccessibilityElement = true
            row.accessibilityLabel = "\(name), \(when), \(labels.sessionScore(session.itemsCorrect, session.items))"
            card.stack.addArrangedSubview(row)
        }
        return card
    }
}
