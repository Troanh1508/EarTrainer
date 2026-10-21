import Foundation

/// One sounded note or rest, with its length in beats (a quarter note is one beat).
struct RhythmEvent: Hashable {
    let beats: Double
    let isRest: Bool

    static func note(_ beats: Double) -> RhythmEvent { RhythmEvent(beats: beats, isRest: false) }
    static func rest(_ beats: Double) -> RhythmEvent { RhythmEvent(beats: beats, isRest: true) }
}

/**
 * A building block of rhythms, one or two beats long. Rhythms are written as sequences of these
 * so they always notate cleanly: beams stay within a beat, and two-beat figures start on beat 1
 * or 3 of a 4/4 bar.
 */
enum RhythmCell: String, StoredEnum {
    case quarter = "QUARTER"
    case eighths = "EIGHTHS"
    case quarterRest = "QUARTER_REST"
    case half = "HALF"
    case dottedQuarterEighth = "DOTTED_QUARTER_EIGHTH"
    case eighthRestEighth = "EIGHTH_REST_EIGHTH"
    case sixteenths = "SIXTEENTHS"
    case eighthTwoSixteenths = "EIGHTH_TWO_SIXTEENTHS"
    case twoSixteenthsEighth = "TWO_SIXTEENTHS_EIGHTH"

    var events: [RhythmEvent] {
        switch self {
        case .quarter: [.note(1)]
        case .eighths: [.note(0.5), .note(0.5)]
        case .quarterRest: [.rest(1)]
        case .half: [.note(2)]
        case .dottedQuarterEighth: [.note(1.5), .note(0.5)]
        case .eighthRestEighth: [.rest(0.5), .note(0.5)]
        case .sixteenths: [.note(0.25), .note(0.25), .note(0.25), .note(0.25)]
        case .eighthTwoSixteenths: [.note(0.5), .note(0.25), .note(0.25)]
        case .twoSixteenthsEighth: [.note(0.25), .note(0.25), .note(0.5)]
        }
    }

    var displayName: String {
        switch self {
        case .quarter: "quarter"
        case .eighths: "two eighths"
        case .quarterRest: "quarter rest"
        case .half: "half"
        case .dottedQuarterEighth: "dotted quarter and eighth"
        case .eighthRestEighth: "eighth rest and eighth"
        case .sixteenths: "four sixteenths"
        case .eighthTwoSixteenths: "eighth and two sixteenths"
        case .twoSixteenthsEighth: "two sixteenths and eighth"
        }
    }

    /// Length in whole beats: 1 or 2.
    var beats: Int { Int(events.reduce(0) { $0 + $1.beats }) }

    var noteCount: Int { events.filter { !$0.isRest }.count }
}

/// A rhythm in 4/4, as a sequence of cells filling whole bars.
struct Rhythm: Hashable {
    static let beatsPerBar = 4

    let cells: [RhythmCell]

    init(_ cells: [RhythmCell]) {
        precondition(Rhythm.isValid(cells), "Not a rhythm of whole 4/4 bars: \(cells)")
        self.cells = cells
    }

    /// The rhythm made of [cells], or nil if they do not fill whole bars with aligned figures.
    static func validated(_ cells: [RhythmCell]) -> Rhythm? { isValid(cells) ? Rhythm(cells) : nil }

    static func isValid(_ cells: [RhythmCell]) -> Bool {
        guard !cells.isEmpty else { return false }
        let total = cells.reduce(0) { $0 + $1.beats }
        guard total % beatsPerBar == 0 else { return false }
        // Two-beat cells must start on beat 1 or 3.
        return zip(startBeats(of: cells), cells).allSatisfy { start, cell in cell.beats == 1 || start % 2 == 0 }
    }

    private static func startBeats(of cells: [RhythmCell]) -> [Int] {
        var starts: [Int] = []
        var beat = 0
        for cell in cells {
            starts.append(beat)
            beat += cell.beats
        }
        return starts
    }

    var totalBeats: Int { cells.reduce(0) { $0 + $1.beats } }
    var bars: Int { totalBeats / Rhythm.beatsPerBar }

    /// Beat (from 0) on which each cell starts.
    var startBeats: [Int] { Rhythm.startBeats(of: cells) }

    /// Every note and rest with the beat it starts on.
    var timeline: [(start: Double, event: RhythmEvent)] {
        var result: [(start: Double, event: RhythmEvent)] = []
        var beat = 0.0
        for event in cells.flatMap(\.events) {
            result.append((beat, event))
            beat += event.beats
        }
        return result
    }

    /// The cells of each bar in turn; cells never cross a barline, since bars are filled exactly.
    var cellsByBar: [[RhythmCell]] {
        var bars: [[RhythmCell]] = []
        for (start, cell) in zip(startBeats, cells) {
            let bar = start / Rhythm.beatsPerBar
            if bar >= bars.count { bars.append([]) }
            bars[bar].append(cell)
        }
        return bars
    }

    /// Spoken description, e.g. "quarter, two eighths, quarter rest, quarter".
    var description: String { cells.map(\.displayName).joined(separator: ", ") }
}
