import UIKit

/**
 * Draws a rhythm on a single-line percussion staff: noteheads with stems, notes shorter than a
 * beat beamed together within their beat, single eighths flagged, plus dots, rests and
 * barlines. Horizontal position follows time, so longer notes take more room. Each bar gets its
 * own line, which keeps sixteenths legible on a phone-width screen.
 *
 * Purely visual: callers provide the spoken description for accessibility.
 */
final class RhythmNotationView: DrawingView, TintableGraphic {
    var color: UIColor = Palette.text { didSet { setNeedsDisplay() } }

    private let bars: [[RhythmCell]]
    private static let barHeight: CGFloat = 64
    private static let barSpacing: CGFloat = 4

    init(_ rhythm: Rhythm) {
        bars = rhythm.cellsByBar
        super.init(frame: .zero)
        constrainSize(height: CGFloat(bars.count) * Self.barHeight + CGFloat(bars.count - 1) * Self.barSpacing)
        isAccessibilityElement = false
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func draw(_ rect: CGRect) {
        for (index, bar) in bars.enumerated() {
            let y = CGFloat(index) * (Self.barHeight + Self.barSpacing)
            drawBar(bar, in: CGRect(x: 0, y: y, width: bounds.width, height: Self.barHeight))
        }
    }

    /// Draws one 4/4 bar of [cells] across the full width, ending with a barline.
    private func drawBar(_ cells: [RhythmCell], in frame: CGRect) {
        let staffY = frame.minY + frame.height * 0.72
        let padStart: CGFloat = 8
        let padEnd: CGFloat = 4
        // Room after the last beat keeps a final note or flag clear of the barline.
        let endGap: CGFloat = 14
        let beatWidth = (frame.width - padStart - padEnd - endGap) / CGFloat(Rhythm.beatsPerBar)

        // Staff line and closing barline.
        stroke(from: CGPoint(x: frame.minX, y: staffY), to: CGPoint(x: frame.maxX, y: staffY), width: 1, color: color.withAlphaComponent(0.45))
        let barX = frame.maxX - padEnd / 2
        stroke(from: CGPoint(x: barX, y: staffY - 12), to: CGPoint(x: barX, y: staffY + 12), width: 1.5, color: color)

        let glyphs = Glyphs(color: color, staffY: staffY)
        // Each event sits a little right of where its time starts, leaving room for the barline.
        func xOf(_ beat: Double) -> CGFloat { frame.minX + padStart + CGFloat(beat) * beatWidth + beatWidth * 0.28 }

        var cellStart = 0.0
        for cell in cells {
            var beat = cellStart
            var stems: [(x: CGFloat, beats: Double)] = [] // stem x and note length, for beaming
            for event in cell.events {
                let x = xOf(beat)
                if event.isRest && event.beats >= 1 {
                    glyphs.quarterRest(x)
                } else if event.isRest {
                    glyphs.eighthRest(x)
                } else {
                    let stemX = glyphs.note(x, hollow: event.beats >= 2, dotted: event.beats == 1.5)
                    if event.beats < 1 { stems.append((stemX, event.beats)) }
                }
                beat += event.beats
            }
            if stems.count >= 2 {
                glyphs.beams(stems)
            } else if let stem = stems.first {
                glyphs.flag(stem.x)
            }
            cellStart += Double(cell.beats)
        }
    }

    private func stroke(from start: CGPoint, to end: CGPoint, width: CGFloat, color: UIColor) {
        let path = UIBezierPath()
        path.move(to: start)
        path.addLine(to: end)
        path.lineWidth = width
        color.setStroke()
        path.stroke()
    }

    /// Glyph drawing in point-based proportions, anchored on the staff line.
    private struct Glyphs {
        let color: UIColor
        let staffY: CGFloat
        let headWidth: CGFloat = 11
        let headHeight: CGFloat = 8
        let stemHeight: CGFloat = 31
        let stemWidth: CGFloat = 1.6
        let beamThickness: CGFloat = 4
        let beamGap: CGFloat = 2.6

        var stemTop: CGFloat { staffY - stemHeight }

        /// Draws a notehead (filled, or hollow for halves) with an up-stem; returns the stem's x.
        func note(_ x: CGFloat, hollow: Bool, dotted: Bool) -> CGFloat {
            if let context = UIGraphicsGetCurrentContext() {
                context.saveGState()
                context.translateBy(x: x, y: staffY)
                context.rotate(by: -20 * .pi / 180)
                let head = UIBezierPath(ovalIn: CGRect(x: -headWidth / 2, y: -headHeight / 2, width: headWidth, height: headHeight))
                if hollow {
                    head.lineWidth = 1.6
                    color.setStroke()
                    head.stroke()
                } else {
                    color.setFill()
                    head.fill()
                }
                context.restoreGState()
            }
            let stemX = x + headWidth / 2 - stemWidth / 2
            let stem = UIBezierPath()
            stem.move(to: CGPoint(x: stemX, y: staffY - 1))
            stem.addLine(to: CGPoint(x: stemX, y: stemTop))
            stem.lineWidth = stemWidth
            color.setStroke()
            stem.stroke()
            if dotted {
                color.setFill()
                UIBezierPath(ovalIn: CGRect(x: x + headWidth / 2 + 4.5 - 1.7, y: staffY - 2 - 1.7, width: 3.4, height: 3.4)).fill()
            }
            return stemX
        }

        /// One beam across all stems; a second beam joins neighboring sixteenths.
        func beams(_ stems: [(x: CGFloat, beats: Double)]) {
            color.setFill()
            let left = stems[0].x - stemWidth / 2
            let right = stems[stems.count - 1].x + stemWidth / 2
            UIBezierPath(rect: CGRect(x: left, y: stemTop, width: right - left, height: beamThickness)).fill()
            for (a, b) in zip(stems, stems.dropFirst()) where a.beats <= 0.25 && b.beats <= 0.25 {
                let y = stemTop + beamThickness + beamGap
                UIBezierPath(rect: CGRect(x: a.x - stemWidth / 2, y: y, width: b.x - a.x + stemWidth, height: beamThickness)).fill()
            }
        }

        /// The curved flag of a lone eighth note.
        func flag(_ stemX: CGFloat) {
            let path = UIBezierPath()
            path.move(to: CGPoint(x: stemX, y: stemTop))
            path.addCurve(
                to: CGPoint(x: stemX + 6, y: stemTop + 17),
                controlPoint1: CGPoint(x: stemX + 2, y: stemTop + 6),
                controlPoint2: CGPoint(x: stemX + 9, y: stemTop + 9)
            )
            path.lineWidth = 2
            path.lineCapStyle = .round
            color.setStroke()
            path.stroke()
        }

        /// A stylized quarter rest: a zig-zag with a hooked tail, centered on the staff line.
        func quarterRest(_ x: CGFloat) {
            let path = UIBezierPath()
            path.move(to: CGPoint(x: x - 2, y: staffY - 13))
            path.addLine(to: CGPoint(x: x + 3, y: staffY - 6))
            path.addLine(to: CGPoint(x: x - 2, y: staffY - 1))
            path.addLine(to: CGPoint(x: x + 3, y: staffY + 5))
            path.addQuadCurve(to: CGPoint(x: x - 1, y: staffY + 11), controlPoint: CGPoint(x: x - 5, y: staffY + 3))
            path.lineWidth = 2.4
            path.lineCapStyle = .round
            path.lineJoinStyle = .round
            color.setStroke()
            path.stroke()
        }

        /// A stylized eighth rest: a dot with a slanted stroke.
        func eighthRest(_ x: CGFloat) {
            color.setFill()
            UIBezierPath(ovalIn: CGRect(x: x - 2.5 - 2.2, y: staffY - 6 - 2.2, width: 4.4, height: 4.4)).fill()
            let path = UIBezierPath()
            path.move(to: CGPoint(x: x - 2.5, y: staffY - 4))
            path.addQuadCurve(to: CGPoint(x: x + 3.5, y: staffY - 7.5), controlPoint: CGPoint(x: x + 1, y: staffY - 3.5))
            path.addLine(to: CGPoint(x: x - 0.5, y: staffY + 7))
            path.lineWidth = 1.6
            path.lineCapStyle = .round
            path.lineJoinStyle = .round
            color.setStroke()
            path.stroke()
        }
    }
}
