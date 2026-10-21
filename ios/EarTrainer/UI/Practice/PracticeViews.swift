import UIKit

/// Text for one answer button: a main label and an optional short form, e.g. "Major third" / "M3".
struct ChoiceLabel {
    let title: String
    var subtitle: String?
}

/// Something that can be tinted to match the answer button it sits in, like drawn rhythm notation.
protocol TintableGraphic: UIView {
    var color: UIColor { get set }
}

/// One answer choice. Its look follows a `ChoiceAppearance`; icons back up the color change so the
/// result never relies on color alone.
final class AnswerButton: UIControl {
    private let titleLabel = UILabel(style: .headline, weight: .semibold)
    private let subtitleLabel = UILabel(style: .footnote)
    private let iconView = UIImageView()
    private let graphic: TintableGraphic?
    private let label: ChoiceLabel

    var appearance: ChoiceAppearance = .available {
        didSet { if appearance != oldValue { applyAppearance() } }
    }

    init(label: ChoiceLabel, graphic: TintableGraphic? = nil) {
        self.label = label
        self.graphic = graphic
        super.init(frame: .zero)
        layer.cornerRadius = 16
        layer.cornerCurve = .continuous
        constrainMinHeight(64)

        titleLabel.text = label.title
        subtitleLabel.text = label.subtitle
        subtitleLabel.isHidden = label.subtitle == nil
        iconView.contentMode = .scaleAspectFit
        iconView.setContentHuggingPriority(.required, for: .horizontal)
        iconView.constrainSize(width: 20, height: 20)

        let body: UIView
        if let graphic {
            body = graphic
        } else {
            body = UIStackView.vertical([titleLabel, subtitleLabel], spacing: 2)
        }
        let row = UIStackView.horizontal([body, iconView], spacing: 8)
        row.isUserInteractionEnabled = false
        addSubview(row)
        row.pinEdges(to: self, insets: UIEdgeInsets(top: 12, left: 16, bottom: 12, right: 12))

        isAccessibilityElement = true
        accessibilityIdentifier = "answer"
        accessibilityLabel = [label.title, label.subtitle].compactMap { $0 }.joined(separator: ", ")
        registerForTraitChanges([UITraitUserInterfaceStyle.self]) { (button: AnswerButton, _: UITraitCollection) in
            button.applyAppearance()
        }
        applyAppearance()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override var isHighlighted: Bool {
        didSet {
            UIView.animate(withDuration: 0.12) {
                self.transform = self.isHighlighted ? CGAffineTransform(scaleX: 0.97, y: 0.97) : .identity
                self.alpha = self.isHighlighted ? 0.85 : 1
            }
        }
    }

    private func applyAppearance() {
        let background: UIColor
        let content: UIColor
        let border: UIColor
        var borderWidth: CGFloat = 1
        var icon: String?
        var state: String?
        switch appearance {
        case .available:
            (background, content, border) = (Palette.card, Palette.text, Palette.outline)
        case .disabled:
            (background, content, border) = (Palette.card, .tertiaryLabel, Palette.outline)
        case .dimmed:
            (background, content, border) = (Palette.card, Palette.secondaryText, Palette.outline)
        case .selectedCorrect:
            (background, content, border) = (Palette.tertiaryContainer, Palette.onTertiaryContainer, Palette.tertiary)
            borderWidth = 2
            icon = "checkmark"
            state = Strings.choiceCorrect
        case .revealedCorrect:
            (background, content, border) = (Palette.tertiaryContainer, Palette.onTertiaryContainer, Palette.tertiary)
            icon = "checkmark"
            state = Strings.choiceCorrect
        case .selectedIncorrect:
            (background, content, border) = (Palette.errorContainer, Palette.onErrorContainer, Palette.error)
            borderWidth = 2
            icon = "xmark"
            state = Strings.choiceIncorrect
        }
        backgroundColor = background
        layer.borderColor = border.resolvedColor(with: traitCollection).cgColor
        layer.borderWidth = borderWidth
        titleLabel.textColor = content
        subtitleLabel.textColor = content.withAlphaComponent(0.8)
        graphic?.color = content
        iconView.image = icon.flatMap { UIImage(systemName: $0, withConfiguration: UIImage.SymbolConfiguration(weight: .bold)) }
        iconView.tintColor = content
        iconView.isHidden = icon == nil
        isEnabled = appearance == .available
        accessibilityValue = state
        accessibilityTraits = appearance == .available ? .button : [.button, .notEnabled]
    }
}

/// Answer choices in rows of [columns].
final class AnswerGridView<A: Hashable>: UIStackView {
    private var buttons: [(choice: A, button: AnswerButton)] = []

    init(choices: [A], columns: Int, label: (A) -> ChoiceLabel, graphic: ((A) -> TintableGraphic)? = nil, onSelect: @escaping (A) -> Void) {
        super.init(frame: .zero)
        axis = .vertical
        spacing = 12
        for start in stride(from: 0, to: choices.count, by: columns) {
            let row = UIStackView.horizontal(spacing: 12, alignment: .fill, distribution: .fillEqually)
            for choice in choices[start..<min(start + columns, choices.count)] {
                let button = AnswerButton(label: label(choice), graphic: graphic?(choice))
                button.addAction(UIAction { _ in onSelect(choice) }, for: .touchUpInside)
                buttons.append((choice, button))
                row.addArrangedSubview(button)
            }
            for _ in 0..<(columns - row.arrangedSubviews.count) { row.addArrangedSubview(UIView()) }
            addArrangedSubview(row)
        }
    }

    required init(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    func update(_ appearanceOf: (A) -> ChoiceAppearance) {
        for (choice, button) in buttons { button.appearance = appearanceOf(choice) }
    }

    func button(for choice: A) -> AnswerButton? { buttons.first { $0.choice == choice }?.button }
}

/**
 * The question number, the score, and one segment per question (right, wrong, the current
 * question, then the ones still to come), so how the session is going reads at a glance.
 */
final class SessionStripView: UIView {
    private let questionLabel = UILabel(style: .subheadline, weight: .bold)
    private let scoreLabel = UILabel(style: .subheadline, weight: .semibold)
    private let segments = SegmentsView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        scoreLabel.setContentHuggingPriority(.required, for: .horizontal)
        let header = UIStackView.horizontal([questionLabel, scoreLabel], spacing: 8, alignment: .firstBaseline)
        segments.constrainSize(height: 6)
        let stack = UIStackView.vertical([header, segments], spacing: 8)
        addSubview(stack)
        stack.pinEdges(to: self)
        isAccessibilityElement = true
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    func update(questionNumber: Int, sessionLength: Int, results: [Bool], awaitingCurrent: Bool) {
        let right = results.filter { $0 }.count
        questionLabel.text = Strings.sessionQuestion(questionNumber, sessionLength)
        scoreLabel.text = results.isEmpty ? Strings.scoreEmpty : Strings.sessionRight(right, results.count)
        scoreLabel.textColor = right == 0 ? Palette.secondaryText : Palette.tertiary
        segments.colors = results.map { $0 ? Palette.tertiary : Palette.error } + (awaitingCurrent ? [Palette.primary] : [])
        segments.count = sessionLength
        accessibilityLabel = "\(questionLabel.text ?? ""), \(scoreLabel.text ?? "")"
    }

    private final class SegmentsView: DrawingView {
        var colors: [UIColor] = [] { didSet { setNeedsDisplay() } }
        var count = 0 { didSet { setNeedsDisplay() } }

        override func draw(_ rect: CGRect) {
            guard count > 0 else { return }
            let gap: CGFloat = 3
            let width = (bounds.width - gap * CGFloat(count - 1)) / CGFloat(count)
            for index in 0..<count {
                (index < colors.count ? colors[index] : Palette.track).setFill()
                let segment = CGRect(x: CGFloat(index) * (width + gap), y: 0, width: width, height: bounds.height)
                UIBezierPath(roundedRect: segment, cornerRadius: bounds.height / 2).fill()
            }
        }
    }
}

/**
 * How the question is played (e.g. "Ascending melodic"), a large play button with a halo that
 * pulses while sound is playing, and a hint underneath.
 */
final class PromptPlayerView: UIView {
    var onPlay: (() -> Void)?
    var onStop: (() -> Void)?

    private let detailPill = PillLabel("", background: Palette.primaryContainer, foreground: Palette.onPrimaryContainer)
    private let outerHalo = UIView()
    private let innerHalo = UIView()
    private let button = UIButton(type: .custom)
    private let hintLabel = UILabel(style: .subheadline, color: Palette.secondaryText, alignment: .center)
    private var isPlaying = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        let halo = UIView()
        halo.constrainSize(width: 150, height: 150)
        for (view, size, alpha) in [(outerHalo, 150.0, 0.08), (innerHalo, 122.0, 0.14)] {
            view.backgroundColor = Palette.primary.withAlphaComponent(alpha)
            view.layer.cornerRadius = size / 2
            view.isUserInteractionEnabled = false
            halo.addSubview(view)
            view.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activate([
                view.widthAnchor.constraint(equalToConstant: size), view.heightAnchor.constraint(equalToConstant: size),
                view.centerXAnchor.constraint(equalTo: halo.centerXAnchor), view.centerYAnchor.constraint(equalTo: halo.centerYAnchor),
            ])
        }
        button.backgroundColor = Palette.primary
        button.tintColor = Palette.onPrimary
        button.layer.cornerRadius = 48
        button.accessibilityIdentifier = "play"
        halo.addSubview(button)
        button.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            button.widthAnchor.constraint(equalToConstant: 96), button.heightAnchor.constraint(equalToConstant: 96),
            button.centerXAnchor.constraint(equalTo: halo.centerXAnchor), button.centerYAnchor.constraint(equalTo: halo.centerYAnchor),
        ])
        button.addAction(UIAction { [weak self] _ in
            guard let self else { return }
            if self.isPlaying { self.onStop?() } else { self.onPlay?() }
        }, for: .touchUpInside)

        let stack = UIStackView.vertical([detailPill, halo, hintLabel], spacing: 10, alignment: .center)
        addSubview(stack)
        stack.pinEdges(to: self)
        hintLabel.widthAnchor.constraint(lessThanOrEqualTo: stack.widthAnchor).isActive = true
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    func update(detail: String, playback: PlaybackStatus, hasHeardPrompt: Bool) {
        detailPill.label.text = detail
        isPlaying = playback == .playing
        let (symbol, label) = isPlaying ? ("stop.fill", Strings.stop) : hasHeardPrompt ? ("arrow.clockwise", Strings.replay) : ("play.fill", Strings.play)
        button.setImage(UIImage(systemName: symbol, withConfiguration: UIImage.SymbolConfiguration(pointSize: 36, weight: .semibold)), for: .normal)
        button.accessibilityLabel = label
        let failed = playback == .failed
        hintLabel.text = failed ? Strings.playbackFailed : isPlaying ? Strings.playing : hasHeardPrompt ? Strings.hearAgain : Strings.listenFirst
        hintLabel.textColor = failed ? Palette.error : Palette.secondaryText
        setPulsing(isPlaying)
    }

    private func setPulsing(_ pulsing: Bool) {
        for halo in [outerHalo, innerHalo] {
            if pulsing {
                guard halo.layer.animation(forKey: "pulse") == nil else { continue }
                let animation = CABasicAnimation(keyPath: "transform.scale")
                animation.fromValue = 1
                animation.toValue = 1.08
                animation.duration = 0.7
                animation.autoreverses = true
                animation.repeatCount = .infinity
                animation.timingFunction = CAMediaTimingFunction(name: .easeInEaseOut)
                halo.layer.add(animation, forKey: "pulse")
            } else {
                halo.layer.removeAnimation(forKey: "pulse")
            }
        }
    }
}

/**
 * Right or wrong, what the answer was, the sounded notes and the time taken, with the button that
 * moves on. Shown in a panel fixed to the bottom, so "Next" stays reachable without scrolling.
 */
final class FeedbackPanel: UIView {
    var onNext: (() -> Void)?

    private let badge = UIImageView()
    private let badgeBackground = UIView()
    private let titleLabel = UILabel(style: .title2, weight: .bold)
    private let summaryLabel = UILabel(style: .headline, weight: .semibold)
    private let yourAnswerLabel = UILabel(style: .subheadline, color: Palette.secondaryText)
    private let detailsLabel = UILabel(style: .subheadline, color: Palette.secondaryText)
    private let nextButton: UIButton

    override init(frame: CGRect) {
        var configuration = UIButton.Configuration.filled()
        configuration.cornerStyle = .large
        configuration.baseBackgroundColor = Palette.primary
        configuration.baseForegroundColor = Palette.onPrimary
        nextButton = UIButton(configuration: configuration)
        super.init(frame: frame)
        backgroundColor = .secondarySystemGroupedBackground
        layer.cornerRadius = 28
        layer.cornerCurve = .continuous
        layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner]
        layer.shadowColor = UIColor.black.cgColor
        layer.shadowOpacity = 0.12
        layer.shadowRadius = 12
        layer.shadowOffset = CGSize(width: 0, height: -2)

        badgeBackground.layer.cornerRadius = 22
        badgeBackground.constrainSize(width: 44, height: 44)
        badgeBackground.addSubview(badge)
        badge.contentMode = .center
        badge.pinEdges(to: badgeBackground)
        let texts = UIStackView.vertical([titleLabel, summaryLabel, yourAnswerLabel, detailsLabel], spacing: 2)
        let header = UIStackView.horizontal([badgeBackground, texts], spacing: 14, alignment: .top)
        header.isAccessibilityElement = true
        nextButton.constrainMinHeight(52)
        nextButton.accessibilityIdentifier = "next"
        nextButton.addAction(UIAction { [weak self] _ in self?.onNext?() }, for: .touchUpInside)
        let stack = UIStackView.vertical([header, nextButton], spacing: 14)
        addSubview(stack)
        stack.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: topAnchor, constant: 18),
            stack.leadingAnchor.constraint(equalTo: readableContentGuide.leadingAnchor, constant: 8),
            stack.trailingAnchor.constraint(equalTo: readableContentGuide.trailingAnchor, constant: -8),
            stack.bottomAnchor.constraint(equalTo: safeAreaLayoutGuide.bottomAnchor, constant: -12),
        ])
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    func update(isCorrect: Bool, selectedLabel: String?, correctSummary: String, details: String?, nextLabel: String) {
        badgeBackground.backgroundColor = isCorrect ? Palette.tertiaryContainer : Palette.errorContainer
        badge.image = UIImage(systemName: isCorrect ? "checkmark" : "xmark", withConfiguration: UIImage.SymbolConfiguration(pointSize: 20, weight: .bold))
        badge.tintColor = isCorrect ? Palette.onTertiaryContainer : Palette.onErrorContainer
        titleLabel.text = isCorrect ? Strings.feedbackCorrect : Strings.feedbackIncorrect
        titleLabel.textColor = isCorrect ? Palette.tertiary : Palette.error
        summaryLabel.text = correctSummary
        yourAnswerLabel.text = selectedLabel.map(Strings.feedbackYourAnswer)
        yourAnswerLabel.isHidden = isCorrect || selectedLabel == nil
        detailsLabel.text = details
        detailsLabel.isHidden = details == nil
        nextButton.configuration?.attributedTitle = AttributedString(nextLabel, attributes: AttributeContainer([.font: UIFont.app(.headline, weight: .semibold)]))
        let header = badgeBackground.superview
        header?.accessibilityLabel = [titleLabel.text, summaryLabel.text, yourAnswerLabel.isHidden ? nil : yourAnswerLabel.text, details]
            .compactMap { $0 }.joined(separator: ". ")
    }
}

/// A large filled or outlined button, as used for the main actions of a screen.
func actionButton(_ title: String, filled: Bool, color: UIColor = Palette.primary, foreground: UIColor = Palette.onPrimary, icon: String? = nil, action: @escaping () -> Void) -> UIButton {
    var configuration = filled ? UIButton.Configuration.filled() : UIButton.Configuration.bordered()
    configuration.cornerStyle = .large
    if filled {
        configuration.baseBackgroundColor = color
        configuration.baseForegroundColor = foreground
    } else {
        configuration.baseForegroundColor = color
    }
    configuration.attributedTitle = AttributedString(title, attributes: AttributeContainer([.font: UIFont.app(.headline, weight: .semibold)]))
    if let icon {
        configuration.image = UIImage(systemName: icon)
        configuration.imagePadding = 8
    }
    let button = UIButton(configuration: configuration, primaryAction: UIAction { _ in action() })
    button.constrainMinHeight(50)
    return button
}
