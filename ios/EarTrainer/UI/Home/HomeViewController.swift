import UIKit

/// The dashboard: today's goal and streak, a suggestion of what to practice, and every exercise.
final class HomeViewController: ScrollingViewController {
    private let container = AppContainer.shared
    private var state: HomeUiState?
    private var loadTask: Task<Void, Never>?
    private var observers: [NSObjectProtocol] = []

    override func viewDidLoad() {
        super.viewDidLoad()
        title = Strings.appName
        navigationItem.largeTitleDisplayMode = .always
        navigationController?.navigationBar.prefersLargeTitles = true
        navigationItem.backButtonDisplayMode = .minimal
        let backup = UIBarButtonItem(image: UIImage(systemName: "gearshape"), primaryAction: UIAction { [weak self] _ in
            self?.navigationController?.pushViewController(BackupViewController(), animated: true)
        })
        backup.accessibilityLabel = Strings.openBackup
        navigationItem.rightBarButtonItem = backup
        for name in [Notification.Name.practiceHistoryDidChange, .settingsDidChange, UIApplication.significantTimeChangeNotification] {
            observers.append(NotificationCenter.default.addObserver(forName: name, object: nil, queue: .main) { [weak self] _ in
                MainActor.assumeIsolated { self?.load() }
            })
        }
        render()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        load()
    }

    private func load() {
        let history = container.history
        let goal = container.goalSettings.current.dailySessions
        loadTask?.cancel()
        loadTask = Task { [weak self] in
            async let days = history.answersPerDay()
            async let finished = history.finishedSessionsPerDay()
            async let last = history.recentSessions(limit: 1)
            // Every exercise's items over all its answers, from the same queries as the Progress screen.
            var accuracy: [ExerciseKind: [AccuracyItem]] = [:]
            for exercise in ExerciseKind.allCases {
                for filter in ProgressFilter.everyAnswer(of: exercise) {
                    accuracy[exercise, default: []] += await history.accuracy(for: filter)
                }
            }
            let state = HomeUiState.from(
                days: await days, finishedSessions: await finished, today: localEpochDay(currentTimeMillis()), dailyGoal: goal,
                lastExercise: (await last).first?.exercise, accuracy: accuracy
            )
            guard let self, !Task.isCancelled else { return }
            self.state = state
            self.render()
        }
    }

    private func open(_ exercise: ExerciseKind) {
        navigationController?.pushViewController(ExerciseScreens.practice(exercise), animated: true)
    }

    private func render() {
        contentStack.removeAllArrangedSubviews()
        let hour = Calendar.current.component(.hour, from: Date())
        let greeting = (5...11).contains(hour) ? Strings.greetingMorning : (12...17).contains(hour) ? Strings.greetingAfternoon : Strings.greetingEvening
        contentStack.addArrangedSubview(UIView.padded(UILabel(greeting, style: .subheadline, color: Palette.secondaryText), UIEdgeInsets(top: 0, left: 4, bottom: 0, right: 4)))
        if let state {
            contentStack.addArrangedSubview(todayCard(state))
            if let suggestion = state.suggestion { contentStack.addArrangedSubview(suggestionCard(suggestion)) }
        }

        let progress = UIButton(configuration: .plain(), primaryAction: UIAction(title: Strings.seeProgress) { [weak self] _ in
            self?.navigationController?.pushViewController(ProgressViewController(), animated: true)
        })
        let heading = UILabel(Strings.exercises, style: .title2, weight: .bold)
        heading.accessibilityTraits = .header
        contentStack.addArrangedSubview(UIView.padded(UIStackView.horizontal([heading, .spacer(), progress]), UIEdgeInsets(top: 4, left: 4, bottom: 0, right: 0)))

        let overviews = state?.exercises ?? ExerciseKind.allCases.map { ExerciseOverview(exercise: $0, attempts: 0, correct: 0) }
        for start in stride(from: 0, to: overviews.count, by: 2) {
            let row = UIStackView.horizontal(spacing: 12, alignment: .fill, distribution: .fillEqually)
            for overview in overviews[start..<min(start + 2, overviews.count)] {
                row.addArrangedSubview(ExerciseCardView(overview: overview) { [weak self] in self?.open(overview.exercise) })
            }
            if row.arrangedSubviews.count == 1 { row.addArrangedSubview(UIView()) }
            contentStack.addArrangedSubview(row)
        }
        for note in [Strings.offlineNote, Strings.soundCredit] {
            contentStack.addArrangedSubview(UIView.padded(UILabel(note, style: .footnote, color: Palette.secondaryText), UIEdgeInsets(top: 0, left: 4, bottom: 0, right: 4)))
        }
    }

    private func todayCard(_ state: HomeUiState) -> UIView {
        let card = CardView(color: Palette.primary, padding: 20, cornerRadius: 28)
        let ring = RingView(diameter: 104, lineWidth: 12)
        ring.fraction = state.goalProgress
        ring.trackColor = Palette.onPrimary.withAlphaComponent(0.25)
        ring.fillColor = Palette.onPrimary
        ring.content.addArrangedSubview(UILabel(String(state.sessionsToday), style: .title1, weight: .bold, color: Palette.onPrimary))
        ring.content.addArrangedSubview(UILabel(Strings.goalOf(state.dailyGoal), style: .caption1, color: Palette.onPrimary.withAlphaComponent(0.85)))
        ring.isAccessibilityElement = true
        ring.accessibilityLabel = Strings.goalRing(state.sessionsToday, state.dailyGoal)

        let title = UILabel(Strings.goalTitle, style: .headline, weight: .bold, color: Palette.onPrimary)
        var configuration = UIButton.Configuration.plain()
        configuration.image = UIImage(systemName: "pencil", withConfiguration: UIImage.SymbolConfiguration(textStyle: .body))
        configuration.baseForegroundColor = Palette.onPrimary
        configuration.indicator = .none
        let goalSettings = container.goalSettings
        let edit = MenuButton(configuration: configuration, menu: UIMenu(title: Strings.goalDialogDescription(sessionLength), children: GoalSettings.dailySessionOptions.map { sessions in
            UIAction(title: Strings.goalOption(sessions), state: sessions == state.dailyGoal ? .on : .off) { _ in
                goalSettings.update { $0.dailySessions == sessions ? $0 : GoalSettings(dailySessions: sessions) }
            }
        }))
        edit.accessibilityLabel = Strings.goalChange
        edit.constrainSize(width: 40, height: 40)

        let message = state.sessionsToGoal == 0 ? Strings.goalDone
            : state.answersToday == 0 && state.currentStreak > 0 ? Strings.goalKeepStreak(state.currentStreak)
            : Strings.goalRemaining(state.sessionsToGoal)
        let side = UIStackView.vertical([
            UIStackView.horizontal([title, .spacer(), edit]),
            UILabel(message, style: .subheadline, color: Palette.onPrimary),
        ], spacing: 8, alignment: .fill)
        if state.currentStreak > 0 {
            side.addArrangedSubview(UIStackView.horizontal([
                PillLabel(Strings.streak(state.currentStreak), background: Palette.onPrimary, foreground: Palette.primary, weight: .bold, icon: "flame.fill"),
                .spacer(),
            ]))
        }
        card.stack.addArrangedSubview(UIStackView.horizontal([ring, side], spacing: 18))
        return card
    }

    private func suggestionCard(_ suggestion: Suggestion) -> UIView {
        let items = suggestion.items
        let names = switch items.count {
        case 1: items[0]
        case 2: Strings.joinTwo(items[0], items[1])
        default: Strings.joinThree(items[0], items[1], items[2])
        }
        let exercise = Strings.exerciseName(suggestion.exercise).lowercased()
        let card = CardView(color: Palette.weakSpotContainer, spacing: 12, cornerRadius: 24)
        card.stack.addArrangedSubviews([
            UILabel(Strings.suggestTitle, style: .subheadline, weight: .bold, color: Palette.onWeakSpotContainer),
            UILabel(Strings.suggestText(names, count: items.count, exercise: exercise), style: .body, color: Palette.onWeakSpotContainer),
            UIStackView.horizontal([
                actionButton(Strings.suggestButton(exercise), filled: true, color: Palette.weakSpot, foreground: Palette.onWeakSpot) { [weak self] in
                    self?.open(suggestion.exercise)
                },
                .spacer(),
            ]),
        ])
        return card
    }
}

/// One exercise on the Home screen: its icon, its overall accuracy as a ring, and what it asks.
private final class ExerciseCardView: UIControl {
    init(overview: ExerciseOverview, action: @escaping () -> Void) {
        super.init(frame: .zero)
        backgroundColor = Palette.card
        layer.cornerRadius = 22
        layer.cornerCurve = .continuous
        constrainMinHeight(150)

        let iconTile = UIView()
        iconTile.backgroundColor = Palette.primaryContainer
        iconTile.layer.cornerRadius = 12
        iconTile.layer.cornerCurve = .continuous
        iconTile.constrainSize(width: 40, height: 40)
        let icon = UIImageView(image: overview.exercise.icon)
        icon.tintColor = Palette.onPrimaryContainer
        iconTile.addSubview(icon)
        icon.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([icon.centerXAnchor.constraint(equalTo: iconTile.centerXAnchor), icon.centerYAnchor.constraint(equalTo: iconTile.centerYAnchor)])

        let ring = RingView(diameter: 44, lineWidth: 5)
        ring.fraction = overview.accuracy ?? 0
        ring.fillColor = overview.needsWork ? Palette.weakSpot : Palette.primary
        let ringLabel = UILabel(overview.accuracy.map(percent) ?? Strings.new, style: .caption2, weight: .bold, lines: 1, alignment: .center)
        ringLabel.adjustsFontSizeToFitWidth = true
        ringLabel.minimumScaleFactor = 0.6
        ring.content.addArrangedSubview(ringLabel)

        let name = UILabel(Strings.exerciseName(overview.exercise), style: .headline, weight: .bold)
        let detail = UILabel(Strings.cardDetail(overview.exercise), style: .footnote, color: Palette.secondaryText)
        let stack = UIStackView.vertical([
            UIStackView.horizontal([iconTile, .spacer(), ring]),
            .spacer(),
            UIStackView.vertical([name, detail], spacing: 2),
        ], spacing: 14)
        stack.isUserInteractionEnabled = false
        addSubview(stack)
        stack.pinEdges(to: self, insets: UIEdgeInsets(top: 16, left: 16, bottom: 16, right: 16))

        isAccessibilityElement = true
        accessibilityTraits = .button
        accessibilityIdentifier = "exercise.\(overview.exercise.name)"
        accessibilityLabel = "\(Strings.exerciseName(overview.exercise)), \(Strings.cardDetail(overview.exercise))"
        accessibilityValue = overview.accuracy.map(percent) ?? Strings.new
        addAction(UIAction { _ in action() }, for: .touchUpInside)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override var isHighlighted: Bool {
        didSet {
            UIView.animate(withDuration: 0.12) {
                self.transform = self.isHighlighted ? CGAffineTransform(scaleX: 0.97, y: 0.97) : .identity
            }
        }
    }
}
