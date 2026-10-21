import UIKit

// MARK: - Building blocks

extension UIStackView {
    static func vertical(_ views: [UIView] = [], spacing: CGFloat = 0, alignment: UIStackView.Alignment = .fill) -> UIStackView {
        let stack = UIStackView(arrangedSubviews: views)
        stack.axis = .vertical
        stack.spacing = spacing
        stack.alignment = alignment
        return stack
    }

    static func horizontal(_ views: [UIView] = [], spacing: CGFloat = 0, alignment: UIStackView.Alignment = .center, distribution: UIStackView.Distribution = .fill) -> UIStackView {
        let stack = UIStackView(arrangedSubviews: views)
        stack.axis = .horizontal
        stack.spacing = spacing
        stack.alignment = alignment
        stack.distribution = distribution
        return stack
    }

    func removeAllArrangedSubviews() {
        for view in arrangedSubviews {
            removeArrangedSubview(view)
            view.removeFromSuperview()
        }
    }

    func addArrangedSubviews(_ views: [UIView]) { views.forEach(addArrangedSubview) }
}

extension UILabel {
    convenience init(
        _ text: String? = nil, style: UIFont.TextStyle = .body, weight: UIFont.Weight = .regular, color: UIColor = Palette.text,
        lines: Int = 0, alignment: NSTextAlignment = .natural
    ) {
        self.init()
        self.text = text
        font = .app(style, weight: weight)
        adjustsFontForContentSizeCategory = true
        textColor = color
        numberOfLines = lines
        textAlignment = alignment
    }
}

extension UIView {
    /// A view that keeps [content] inset by [insets].
    static func padded(_ content: UIView, _ insets: UIEdgeInsets) -> UIView {
        let container = UIView()
        container.addSubview(content)
        content.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            content.topAnchor.constraint(equalTo: container.topAnchor, constant: insets.top),
            content.bottomAnchor.constraint(equalTo: container.bottomAnchor, constant: -insets.bottom),
            content.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: insets.left),
            content.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -insets.right),
        ])
        return container
    }

    func pinEdges(to other: UIView, insets: UIEdgeInsets = .zero) {
        translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            topAnchor.constraint(equalTo: other.topAnchor, constant: insets.top),
            bottomAnchor.constraint(equalTo: other.bottomAnchor, constant: -insets.bottom),
            leadingAnchor.constraint(equalTo: other.leadingAnchor, constant: insets.left),
            trailingAnchor.constraint(equalTo: other.trailingAnchor, constant: -insets.right),
        ])
    }

    func constrainSize(width: CGFloat? = nil, height: CGFloat? = nil) {
        translatesAutoresizingMaskIntoConstraints = false
        if let width { widthAnchor.constraint(equalToConstant: width).isActive = true }
        if let height { heightAnchor.constraint(equalToConstant: height).isActive = true }
    }

    func constrainMinHeight(_ height: CGFloat) {
        translatesAutoresizingMaskIntoConstraints = false
        heightAnchor.constraint(greaterThanOrEqualToConstant: height).isActive = true
    }

    /// Empty space that stretches, for pushing neighbors in a stack apart.
    static func spacer() -> UIView {
        let view = UIView()
        view.setContentHuggingPriority(.init(1), for: .horizontal)
        view.setContentHuggingPriority(.init(1), for: .vertical)
        return view
    }
}

/// A rounded panel with its content in a vertical stack.
final class CardView: UIView {
    let stack: UIStackView

    init(color: UIColor = Palette.card, spacing: CGFloat = 14, padding: CGFloat = 20, cornerRadius: CGFloat = 22) {
        stack = .vertical(spacing: spacing)
        super.init(frame: .zero)
        backgroundColor = color
        layer.cornerRadius = cornerRadius
        layer.cornerCurve = .continuous
        addSubview(stack)
        stack.pinEdges(to: self, insets: UIEdgeInsets(top: padding, left: padding, bottom: padding, right: padding))
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }
}

/// A short text on a rounded background, e.g. a streak or a trend.
final class PillLabel: UIView {
    let label: UILabel

    init(_ text: String, background: UIColor, foreground: UIColor, style: UIFont.TextStyle = .subheadline, weight: UIFont.Weight = .semibold, icon: String? = nil) {
        label = UILabel(text, style: style, weight: weight, color: foreground)
        super.init(frame: .zero)
        backgroundColor = background
        layer.cornerRadius = 12
        layer.cornerCurve = .continuous
        let content = UIStackView.horizontal([], spacing: 6)
        if let icon {
            let image = UIImageView(image: UIImage(systemName: icon, withConfiguration: UIImage.SymbolConfiguration(textStyle: style, scale: .small)))
            image.tintColor = foreground
            content.addArrangedSubview(image)
        }
        content.addArrangedSubview(label)
        addSubview(content)
        content.pinEdges(to: self, insets: UIEdgeInsets(top: 5, left: 11, bottom: 5, right: 11))
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }
}

// MARK: - Drawing

/// A view drawn with Core Graphics that redraws when its size or the light/dark appearance changes.
class DrawingView: UIView {
    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        backgroundColor = .clear
        contentMode = .redraw
        registerForTraitChanges([UITraitUserInterfaceStyle.self]) { (view: DrawingView, _: UITraitCollection) in
            view.setNeedsDisplay()
        }
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }
}

/// A ring filled to [fraction] from the top, with optional content in the middle.
final class RingView: DrawingView {
    var fraction: CGFloat = 0 { didSet { setNeedsDisplay() } }
    var trackColor: UIColor = Palette.track { didSet { setNeedsDisplay() } }
    var fillColor: UIColor = Palette.primary { didSet { setNeedsDisplay() } }
    var lineWidth: CGFloat { didSet { setNeedsDisplay() } }
    let content = UIStackView.vertical(spacing: 0, alignment: .center)

    init(diameter: CGFloat, lineWidth: CGFloat) {
        self.lineWidth = lineWidth
        super.init(frame: .zero)
        constrainSize(width: diameter, height: diameter)
        addSubview(content)
        content.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            content.centerXAnchor.constraint(equalTo: centerXAnchor),
            content.centerYAnchor.constraint(equalTo: centerYAnchor),
            content.widthAnchor.constraint(lessThanOrEqualTo: widthAnchor, constant: -2 * lineWidth - min(12, diameter / 10)),
        ])
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func draw(_ rect: CGRect) {
        let inset = bounds.insetBy(dx: lineWidth / 2, dy: lineWidth / 2)
        let track = UIBezierPath(ovalIn: inset)
        track.lineWidth = lineWidth
        trackColor.setStroke()
        track.stroke()
        guard fraction > 0 else { return }
        let center = CGPoint(x: bounds.midX, y: bounds.midY)
        let start = -CGFloat.pi / 2
        let arc = UIBezierPath(arcCenter: center, radius: inset.width / 2, startAngle: start, endAngle: start + 2 * .pi * min(fraction, 1), clockwise: true)
        arc.lineWidth = lineWidth
        arc.lineCapStyle = .round
        fillColor.setStroke()
        arc.stroke()
    }
}

/// A rounded bar filled to [fraction] of its width.
final class BarView: DrawingView {
    var fraction: CGFloat = 0 { didSet { setNeedsDisplay() } }
    var trackColor: UIColor = Palette.track { didSet { setNeedsDisplay() } }
    var fillColor: UIColor = Palette.primary { didSet { setNeedsDisplay() } }

    init(height: CGFloat, fraction: CGFloat = 0, fill: UIColor = Palette.primary, track: UIColor = Palette.track) {
        self.fraction = fraction
        fillColor = fill
        trackColor = track
        super.init(frame: .zero)
        constrainSize(height: height)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func draw(_ rect: CGRect) {
        let radius = bounds.height / 2
        trackColor.setFill()
        UIBezierPath(roundedRect: bounds, cornerRadius: radius).fill()
        guard fraction > 0 else { return }
        fillColor.setFill()
        UIBezierPath(roundedRect: CGRect(x: 0, y: 0, width: bounds.width * min(fraction, 1), height: bounds.height), cornerRadius: radius).fill()
    }
}

/// A small rounded color swatch with a caption, for chart legends.
func legendKey(_ color: UIColor, _ text: String, circle: Bool = false) -> UIView {
    let swatch = UIView()
    swatch.backgroundColor = color
    swatch.layer.cornerRadius = circle ? 5 : 3
    swatch.constrainSize(width: 10, height: 10)
    return UIStackView.horizontal([swatch, UILabel(text, style: .caption1, color: Palette.secondaryText)], spacing: 5)
}

// MARK: - Wrapping layout

/// Lays out its subviews in rows, wrapping to a new row when one is full, like a flow of chips.
final class FlowLayoutView: UIView {
    var spacing: CGFloat = 8 { didSet { setNeedsLayout() } }
    var lineSpacing: CGFloat = 8 { didSet { setNeedsLayout() } }
    var centered = false

    private var lastWidth: CGFloat = 0

    func setViews(_ views: [UIView]) {
        subviews.forEach { $0.removeFromSuperview() }
        views.forEach(addSubview)
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let height = arrange(width: bounds.width, apply: true)
        if bounds.width != lastWidth || abs(height - intrinsicContentSize.height) > 0.5 {
            lastWidth = bounds.width
            invalidateIntrinsicContentSize()
        }
    }

    override var intrinsicContentSize: CGSize {
        CGSize(width: UIView.noIntrinsicMetric, height: arrange(width: lastWidth > 0 ? lastWidth : bounds.width, apply: false))
    }

    /// Places the subviews in rows within [width]; returns the height they need.
    @discardableResult
    private func arrange(width: CGFloat, apply: Bool) -> CGFloat {
        guard width > 0 else { return 0 }
        var rows: [[(UIView, CGSize)]] = [[]]
        var rowWidth: CGFloat = 0
        for view in subviews {
            var size = view.systemLayoutSizeFitting(CGSize(width: width, height: UIView.layoutFittingCompressedSize.height),
                                                    withHorizontalFittingPriority: .fittingSizeLevel, verticalFittingPriority: .fittingSizeLevel)
            size.width = min(size.width, width)
            let needed = rows[rows.count - 1].isEmpty ? size.width : rowWidth + spacing + size.width
            if needed > width && !rows[rows.count - 1].isEmpty {
                rows.append([(view, size)])
                rowWidth = size.width
            } else {
                rows[rows.count - 1].append((view, size))
                rowWidth = needed
            }
        }
        var y: CGFloat = 0
        for (index, row) in rows.enumerated() where !row.isEmpty {
            let rowHeight = row.map(\.1.height).max() ?? 0
            let used = row.map(\.1.width).reduce(0, +) + spacing * CGFloat(row.count - 1)
            var x = centered ? (width - used) / 2 : 0
            if apply {
                for (view, size) in row {
                    view.frame = CGRect(x: x, y: y + (rowHeight - size.height) / 2, width: size.width, height: size.height)
                    x += size.width + spacing
                }
            }
            y += rowHeight + (index < rows.count - 1 ? lineSpacing : 0)
        }
        return y
    }
}

// MARK: - Screens

/**
 * A screen whose content scrolls in one centered column, at most 560 points wide so it stays
 * readable on iPad.
 */
class ScrollingViewController: UIViewController {
    let scrollView = UIScrollView()
    let contentStack = UIStackView.vertical(spacing: 16)
    var contentInsets = UIEdgeInsets(top: 12, left: 16, bottom: 24, right: 16)

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = Palette.background
        scrollView.alwaysBounceVertical = true
        view.addSubview(scrollView)
        scrollView.pinEdges(to: view)
        scrollView.addSubview(contentStack)
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        let guide = scrollView.contentLayoutGuide
        let frame = scrollView.frameLayoutGuide
        let preferredWidth = contentStack.widthAnchor.constraint(equalTo: frame.widthAnchor, constant: -(contentInsets.left + contentInsets.right))
        preferredWidth.priority = .init(999)
        NSLayoutConstraint.activate([
            contentStack.topAnchor.constraint(equalTo: guide.topAnchor, constant: contentInsets.top),
            contentStack.bottomAnchor.constraint(equalTo: guide.bottomAnchor, constant: -contentInsets.bottom),
            contentStack.centerXAnchor.constraint(equalTo: frame.centerXAnchor),
            contentStack.widthAnchor.constraint(lessThanOrEqualToConstant: 560),
            contentStack.widthAnchor.constraint(lessThanOrEqualTo: frame.widthAnchor, constant: -(contentInsets.left + contentInsets.right)),
            preferredWidth,
        ])
    }
}

// MARK: - Exercise icons

/**
 * Line icons for the exercises: two notes at different heights for an interval, three notes on
 * one stem for a chord, a staircase of scale steps, blocks of chords in a row, a pair of notes
 * for dictation, and bars of different lengths for rhythm. Drawn as template images so they take
 * the tint of wherever they are shown.
 */
extension ExerciseKind {
    var icon: UIImage {
        UIGraphicsImageRenderer(size: CGSize(width: 24, height: 24)).image { _ in
            let path = UIBezierPath()
            func circle(_ x: CGFloat, _ y: CGFloat, _ radius: CGFloat) {
                path.move(to: CGPoint(x: x + radius, y: y))
                path.addArc(withCenter: CGPoint(x: x, y: y), radius: radius, startAngle: 0, endAngle: 2 * .pi, clockwise: true)
            }
            func line(_ points: [(CGFloat, CGFloat)]) {
                path.move(to: CGPoint(x: points[0].0, y: points[0].1))
                for point in points.dropFirst() { path.addLine(to: CGPoint(x: point.0, y: point.1)) }
            }
            switch self {
            case .intervals:
                circle(6, 17, 2.5)
                line([(8.5, 17), (8.5, 7)])
                circle(16, 12, 2.5)
                line([(18.5, 12), (18.5, 3)])
            case .chords:
                circle(9, 19, 2.4)
                circle(9, 14, 2.4)
                circle(9, 9, 2.4)
                line([(11.4, 19), (11.4, 3)])
            case .scaleDegrees:
                line([(3, 20), (7, 20), (7, 16), (11, 16), (11, 12), (15, 12), (15, 8), (19, 8), (19, 4)])
            case .progressions:
                path.append(UIBezierPath(rect: CGRect(x: 4, y: 7, width: 4, height: 10)))
                path.append(UIBezierPath(rect: CGRect(x: 10, y: 5, width: 4, height: 14)))
                path.append(UIBezierPath(rect: CGRect(x: 16, y: 9, width: 4, height: 6)))
            case .dictation:
                line([(9, 18), (9, 5), (21, 3), (21, 16)])
                circle(6, 18, 3)
                circle(18, 16, 3)
            case .rhythm:
                line([(5, 20), (5, 8)])
                line([(12, 20), (12, 4)])
                line([(19, 20), (19, 11)])
                line([(3, 20), (21, 20)])
            }
            path.lineWidth = 2
            path.lineCapStyle = .round
            path.lineJoinStyle = .round
            UIColor.black.setStroke()
            path.stroke()
        }.withRenderingMode(.alwaysTemplate)
    }
}

// MARK: - Formatting

/// e.g. "76%".
func percent(_ fraction: Double) -> String { "\(Int((fraction * 100).rounded()))%" }

/// The local calendar day of [millis], in days since 1970-01-01: the same days history counts answers in.
func localEpochDay(_ millis: Int64, timeZone: TimeZone = .current) -> Int64 {
    let offset = Int64(timeZone.secondsFromGMT(for: Date(timeIntervalSince1970: Double(millis) / 1000))) * 1000
    let shifted = millis + offset
    let dayMillis: Int64 = 86_400_000
    return shifted >= 0 ? shifted / dayMillis : (shifted - dayMillis + 1) / dayMillis
}

/// Weekday of an epoch day, 0 for Monday to 6 for Sunday. 1970-01-01 was a Thursday.
func weekdayOf(_ day: Int64) -> Int { Int(((day + 3) % 7 + 7) % 7) }
