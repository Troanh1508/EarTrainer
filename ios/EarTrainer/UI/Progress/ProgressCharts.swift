import UIKit

/// A ring filled to [accuracy], with the percentage and what it counts in the middle; empty when nil.
func accuracyDonut(_ accuracy: Double?, caption: String, description: String, diameter: CGFloat = 136, lineWidth: CGFloat = 14) -> RingView {
    let ring = RingView(diameter: diameter, lineWidth: lineWidth)
    ring.fraction = accuracy ?? 0
    ring.content.addArrangedSubview(UILabel(accuracy.map(percent) ?? "—", style: .title1, weight: .bold, alignment: .center))
    ring.content.addArrangedSubview(UILabel(caption, style: .caption1, color: Palette.secondaryText, alignment: .center))
    ring.isAccessibilityElement = true
    ring.accessibilityLabel = description
    return ring
}

/**
 * One item's accuracy as a bar, orange when it needs work; unpracticed items get no bar. With
 * an action, a practiced row opens more detail and shows a chevron.
 */
final class AccuracyRowView: UIControl {
    init(item: AccuracyItem, action: (() -> Void)?) {
        super.init(frame: .zero)
        let barColor = item.needsWork ? Palette.weakSpot : Palette.primary
        let names = UIStackView.vertical([UILabel(item.label, style: .subheadline, weight: .semibold)], spacing: 1)
        names.widthAnchor.constraint(equalToConstant: 120).isActive = true
        let row = UIStackView.horizontal([names], spacing: 10)
        if let accuracy = item.accuracy {
            names.addArrangedSubview(UILabel(Strings.itemScore(item.correct, item.attempts), style: .caption2, color: Palette.secondaryText))
            row.addArrangedSubview(BarView(height: 12, fraction: accuracy, fill: barColor))
            let value = UILabel(percent(accuracy), style: .subheadline, weight: .bold, color: item.needsWork ? barColor : Palette.text, alignment: .right)
            value.widthAnchor.constraint(equalToConstant: 46).isActive = true
            row.addArrangedSubview(value)
        } else {
            row.addArrangedSubview(UILabel(Strings.notPracticed, style: .footnote, color: Palette.secondaryText, alignment: .right))
        }
        if action != nil {
            // Unpracticed rows get the same space, so every bar ends at the same place.
            let chevron = UIImageView(image: item.accuracy == nil ? nil : UIImage(systemName: "chevron.right"))
            chevron.tintColor = .tertiaryLabel
            chevron.contentMode = .center
            chevron.constrainSize(width: 16)
            row.addArrangedSubview(chevron)
        }
        row.isUserInteractionEnabled = false
        addSubview(row)
        row.pinEdges(to: self, insets: UIEdgeInsets(top: 4, left: 0, bottom: 4, right: 0))
        constrainMinHeight(48)

        isAccessibilityElement = true
        accessibilityLabel = item.label
        accessibilityValue = item.accuracy.map { "\(percent($0)), \(Strings.itemScore(item.correct, item.attempts))" } ?? Strings.notPracticed
        if let action, item.accuracy != nil {
            accessibilityTraits = .button
            accessibilityIdentifier = "accuracyRow"
            accessibilityHint = Strings.showMistakes
            addAction(UIAction { _ in action() }, for: .touchUpInside)
        } else {
            isUserInteractionEnabled = false
        }
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override var isHighlighted: Bool {
        didSet { alpha = isHighlighted ? 0.6 : 1 }
    }
}

/// Explains the two bar colors.
func accuracyLegend() -> UIView {
    UIStackView.horizontal([
        legendKey(Palette.weakSpot, Strings.legendNeedsWork(Int(AccuracyItem.weakThreshold * 100)), circle: true),
        legendKey(Palette.primary, Strings.legendOnTrack, circle: true),
    ], spacing: 16)
}

/**
 * Accuracy per session as a line over the given sessions (oldest first). The scale runs from
 * 50% to 100% while every session stays above half, so typical changes are visible, and from
 * 0% otherwise.
 */
final class TrendChartView: UIView {
    private static let chartHeight: CGFloat = 132
    /// Half the height of a scale label, so gridlines meet the labels' centers.
    private static let labelInset: CGFloat = 8

    init(sessions: [SessionSummary]) {
        super.init(frame: .zero)
        let values = sessions.map { Double($0.itemsCorrect) / Double(max($0.items, 1)) }
        let low = (values.min() ?? 0) >= 0.5 ? 0.5 : 0.0
        let levels = [1, (1 + low) / 2, low]

        let scale = UIStackView.vertical(levels.map { UILabel(percent($0), style: .caption2, color: Palette.secondaryText, alignment: .right) }, alignment: .trailing)
        scale.distribution = .equalSpacing
        scale.constrainSize(width: 36, height: Self.chartHeight)
        let plot = Plot(values: values, low: low, levels: levels)
        plot.constrainSize(height: Self.chartHeight)
        let chart = UIStackView.horizontal([scale, plot], spacing: 8, alignment: .top)

        let formatter = DateFormatter()
        formatter.setLocalizedDateFormatFromTemplate("MMMd")
        let first = UILabel(formatter.string(from: Date(timeIntervalSince1970: Double(sessions.first?.startedAtMillis ?? 0) / 1000)), style: .caption2, color: Palette.secondaryText)
        let last = UILabel(Strings.trendLatest, style: .caption2, color: Palette.secondaryText)
        let dates = UIStackView.horizontal([first, .spacer(), last])
        let stack = UIStackView.vertical([chart, UIView.padded(dates, UIEdgeInsets(top: 0, left: 44, bottom: 0, right: 0))], spacing: 6)
        addSubview(stack)
        stack.pinEdges(to: self)

        isAccessibilityElement = true
        accessibilityLabel = Strings.trendDescription(sessions.count, values.map(percent).joined(separator: ", "))
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    private final class Plot: DrawingView {
        let values: [Double]
        let low: Double
        let levels: [Double]

        init(values: [Double], low: Double, levels: [Double]) {
            self.values = values
            self.low = low
            self.levels = levels
            super.init(frame: .zero)
        }

        required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

        override func draw(_ rect: CGRect) {
            let top = TrendChartView.labelInset
            let bottom = bounds.height - TrendChartView.labelInset
            let edge: CGFloat = 8
            func y(_ value: Double) -> CGFloat { bottom - CGFloat((value - low) / (1 - low)) * (bottom - top) }

            Palette.outline.setStroke()
            for level in levels {
                let grid = UIBezierPath()
                grid.move(to: CGPoint(x: 0, y: y(level)))
                grid.addLine(to: CGPoint(x: bounds.width, y: y(level)))
                grid.lineWidth = 1
                grid.stroke()
            }
            guard values.count >= 2 else { return }
            let step = (bounds.width - 2 * edge) / CGFloat(values.count - 1)
            let points = values.enumerated().map { CGPoint(x: edge + CGFloat($0.offset) * step, y: y($0.element)) }
            let line = UIBezierPath()
            line.move(to: points[0])
            points.dropFirst().forEach(line.addLine)
            let area = line.copy() as! UIBezierPath
            area.addLine(to: CGPoint(x: points.last!.x, y: bottom))
            area.addLine(to: CGPoint(x: points[0].x, y: bottom))
            area.close()
            Palette.primary.withAlphaComponent(0.12).setFill()
            area.fill()
            line.lineWidth = 3
            line.lineCapStyle = .round
            line.lineJoinStyle = .round
            Palette.primary.setStroke()
            line.stroke()
            let dot = UIBezierPath(ovalIn: CGRect(x: points.last!.x - 6, y: points.last!.y - 6, width: 12, height: 12))
            Palette.card.setFill()
            dot.fill()
            dot.lineWidth = 3
            dot.stroke()
        }
    }
}

/// One slice of the answers pie and its legend row.
struct PieSlice {
    let label: String
    let count: Int
    let color: UIColor
    let isRight: Bool
}

/// A ring cut into slices by count, with the total in the middle.
final class PieView: DrawingView {
    private let slices: [PieSlice]
    private let gapColor: UIColor
    private static let diameter: CGFloat = 200
    private static let thickness: CGFloat = 40

    init(slices: [PieSlice], gapColor: UIColor, total: Int, description: String) {
        self.slices = slices
        self.gapColor = gapColor
        super.init(frame: .zero)
        constrainSize(width: Self.diameter, height: Self.diameter)
        let center = UIStackView.vertical([
            UILabel(String(total), style: .title1, weight: .bold, alignment: .center),
            UILabel(Strings.timesHeard(total), style: .caption1, color: Palette.secondaryText, alignment: .center),
        ], alignment: .center)
        addSubview(center)
        center.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([center.centerXAnchor.constraint(equalTo: centerXAnchor), center.centerYAnchor.constraint(equalTo: centerYAnchor)])
        isAccessibilityElement = true
        accessibilityLabel = description
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func draw(_ rect: CGRect) {
        let total = slices.reduce(0) { $0 + $1.count }
        guard total > 0 else { return }
        let center = CGPoint(x: bounds.midX, y: bounds.midY)
        let radius = (bounds.width - Self.thickness) / 2
        var start = -CGFloat.pi / 2
        for slice in slices {
            let sweep = 2 * .pi * CGFloat(slice.count) / CGFloat(total)
            let arc = UIBezierPath(arcCenter: center, radius: radius, startAngle: start, endAngle: start + sweep, clockwise: true)
            arc.lineWidth = Self.thickness
            slice.color.setStroke()
            arc.stroke()
            start += sweep
        }
        // A thin line in the card's color keeps neighboring slices apart.
        guard slices.count > 1 else { return }
        start = -CGFloat.pi / 2
        let gap = 0.6 * CGFloat.pi / 180
        for slice in slices {
            let edge = UIBezierPath(arcCenter: center, radius: radius, startAngle: start - gap, endAngle: start + gap, clockwise: true)
            edge.lineWidth = Self.thickness
            gapColor.setStroke()
            edge.stroke()
            start += 2 * .pi * CGFloat(slice.count) / CGFloat(total)
        }
    }
}

/// A session in a list: when, which exercise, and how it went.
func sessionRow(_ session: SessionSummary, dateStyle: DateFormatter.Style = .medium) -> UIView {
    let labels = ScoringLabels(session.exercise)
    let formatter = DateFormatter()
    formatter.dateStyle = dateStyle
    formatter.timeStyle = .short
    let date = Date(timeIntervalSince1970: Double(session.startedAtMillis) / 1000)
    let left = UIStackView.vertical([
        UILabel(formatter.string(from: date), style: .body),
        UILabel(Strings.exerciseName(session.exercise), style: .footnote, color: Palette.secondaryText),
    ], spacing: 2)
    let right = UIStackView.vertical([UILabel(labels.sessionScore(session.itemsCorrect, session.items), style: .subheadline, alignment: .right)], spacing: 2, alignment: .trailing)
    if let fullyRight = labels.fullyRight {
        right.addArrangedSubview(UILabel(fullyRight(session.correct, session.attempts), style: .footnote, color: Palette.secondaryText, alignment: .right))
    }
    right.setContentCompressionResistancePriority(.required, for: .horizontal)
    let row = UIStackView.horizontal([left, right], spacing: 12)
    row.isAccessibilityElement = true
    row.accessibilityLabel = [formatter.string(from: date), Strings.exerciseName(session.exercise), labels.sessionScore(session.itemsCorrect, session.items)].joined(separator: ", ")
    return row
}
