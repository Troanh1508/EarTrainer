import Foundation

/// Questions in one round of a comparison drill.
let comparisonQuestionsPerRound = 10

/**
 * A short drill on two answers that are easily mixed up: every question asks for one of the two,
 * with only those two as choices. The questions are the exercise's ordinary questions, so the
 * answers are recorded in history like any others.
 */
protocol Comparison: AnyObject {
    associatedtype Question: ExerciseQuestion

    /// The two answers, in the order they are offered.
    var answers: [Question.Answer] { get }

    func nextQuestion() -> Question

    /**
     * One example of each of `answers`, played exactly the same way apart from the difference
     * itself: from the same note, on the same bass or in the same key. Fixed for the drill.
     */
    var examples: [ListeningPrompt] { get }
}

/// Two intervals, from random notes; [style] nil mixes all three styles.
final class IntervalComparison: Comparison {
    let answers: [Interval]
    private(set) var examples: [ListeningPrompt] = []
    private let style: IntervalStyle?
    private let random: RandomSource
    private let pitchRange: ClosedRange<Int>
    private let widest: Int

    init(_ first: Interval, _ second: Interval, style: IntervalStyle?, random: RandomSource = .system, pitchRange: ClosedRange<Int> = 48...84) {
        precondition(first != second, "A comparison needs two different intervals")
        answers = [first, second].sorted { $0.semitones < $1.semitones }
        self.style = style
        self.random = random
        self.pitchRange = pitchRange
        widest = answers.map(\.semitones).max()!
        let lower = randomLowerPitch()
        examples = answers.map { IntervalQuestion(lowerPitch: lower, interval: $0, style: style ?? .melodicAscending, choices: answers).prompt }
    }

    private func randomLowerPitch() -> Pitch { Pitch(random.int(in: pitchRange.lowerBound...(pitchRange.upperBound - widest))) }

    func nextQuestion() -> IntervalQuestion {
        IntervalQuestion(
            lowerPitch: randomLowerPitch(),
            interval: random.element(of: answers),
            style: style ?? random.element(of: IntervalStyle.allCases),
            choices: answers
        )
    }
}

/// Two chord qualities, or two inversions, played as block chords from random bass notes.
final class ChordComparison: Comparison {
    let answers: [ChordAnswer]
    private(set) var examples: [ListeningPrompt] = []
    private let target: ChordTarget
    private let random: RandomSource
    private let pitchRange: ClosedRange<Int>
    /// Qualities that have both inversions being compared; triads first, so the common case stays simple.
    private let qualitiesForInversions: [ChordQuality]

    init(_ first: ChordAnswer, _ second: ChordAnswer, random: RandomSource = .system, pitchRange: ClosedRange<Int> = 48...84) {
        precondition(first != second, "A comparison needs two different answers")
        switch (first, second) {
        case (.quality, .quality): target = .quality
        case (.inversion, .inversion): target = .inversion
        default: preconditionFailure("Both answers must be qualities, or both inversions")
        }
        let sortedAnswers = [first, second].sorted { $0.ordinal < $1.ordinal }
        answers = sortedAnswers
        self.random = random
        self.pitchRange = pitchRange
        if case .inversion = first {
            let inversions = sortedAnswers.compactMap { answer -> ChordInversion? in
                if case .inversion(let inversion) = answer { return inversion }
                return nil
            }
            qualitiesForInversions = ChordQuality.allCases
                .filter { quality in inversions.allSatisfy(quality.inversions.contains) }
                .sorted { $0.inversions.count < $1.inversions.count }
            precondition(!qualitiesForInversions.isEmpty, "No chord quality has both inversions")
        } else {
            qualitiesForInversions = []
        }
        // One bass note low enough for every shape, so both examples share it.
        let bass = randomBass(span: ChordExerciseSettings.maxChordSpan)
        let quality = qualitiesForInversions.first
        examples = answers.map { question(for: $0, quality: quality) { _ in bass }.prompt }
    }

    private func question(for answer: ChordAnswer, quality: ChordQuality?, bassPicker: (Int) -> Pitch) -> ChordQuestion {
        let chordQuality: ChordQuality
        let inversion: ChordInversion
        switch answer {
        case .quality(let answerQuality):
            chordQuality = answerQuality
            inversion = .rootPosition
        case .inversion(let answerInversion):
            chordQuality = quality!
            inversion = answerInversion
        }
        let bass = bassPicker(chordQuality.offsetsAboveBass(inversion).last!)
        return ChordQuestion(bass: bass, quality: chordQuality, inversion: inversion, style: .block, target: target, choices: answers)
    }

    private func randomBass(span: Int) -> Pitch { Pitch(random.int(in: pitchRange.lowerBound...(pitchRange.upperBound - span))) }

    func nextQuestion() -> ChordQuestion {
        question(for: random.element(of: answers), quality: random.elementOrNil(of: qualitiesForInversions), bassPicker: randomBass)
    }
}

/// Two scale degrees in [mode], each question in a random key after a cadence.
final class ScaleDegreeComparison: Comparison {
    let answers: [ScaleDegree]
    private(set) var examples: [ListeningPrompt] = []
    private let mode: Mode
    private let random: RandomSource

    init(_ first: ScaleDegree, _ second: ScaleDegree, mode: Mode, random: RandomSource = .system) {
        precondition(first != second, "A comparison needs two different degrees")
        answers = [first, second].sorted { $0.ordinal < $1.ordinal }
        self.mode = mode
        self.random = random
        let key = randomKey()
        examples = answers.map { ScaleDegreeQuestion(key: key, degree: $0, context: .cadence, choices: answers).prompt }
    }

    private func randomKey() -> Key { Key(tonic: KeyChoice.random.pickTonic(random), mode: mode) }

    func nextQuestion() -> ScaleDegreeQuestion {
        ScaleDegreeQuestion(key: randomKey(), degree: random.element(of: answers), context: .cadence, choices: answers)
    }

    /**
     * The mode to compare [first] and [second] in when the history mixes modes: the first
     * mode in which both are notes of the key, or major if none is.
     */
    static func mode(for first: ScaleDegree, _ second: ScaleDegree) -> Mode {
        Mode.allCases.first { $0.diatonicDegrees.contains(first) && $0.diatonicDegrees.contains(second) } ?? .major
    }
}

/**
 * Two chords of a key, each question being the tonic chord and then one of them, in a random key.
 * Answers are recorded as two-chord progressions; progression scoring skips the opening tonic,
 * so only the compared chord counts. When one of the two is the tonic itself, the question is
 * whether the chord changes at all.
 */
final class ProgressionChordComparison: Comparison {
    let answers: [ChordProgression]
    private(set) var examples: [ListeningPrompt] = []
    private let mode: Mode
    private let random: RandomSource

    init(_ first: DiatonicChord, _ second: DiatonicChord, random: RandomSource = .system) {
        precondition(first != second, "A comparison needs two different chords")
        precondition(first.mode == second.mode, "Both chords must be in the same mode")
        mode = first.mode
        self.random = random
        let tonic = DiatonicChord(first.mode, 1)
        answers = [first, second].sorted { $0.step < $1.step }.map { ChordProgression([tonic, $0]) }
        let key = randomKey()
        examples = answers.map { ProgressionQuestion(key: key, progression: $0, choices: answers).prompt }
    }

    private func randomKey() -> Key { Key(tonic: KeyChoice.random.pickTonic(random), mode: mode) }

    func nextQuestion() -> ProgressionQuestion {
        ProgressionQuestion(key: randomKey(), progression: random.element(of: answers), choices: answers)
    }
}

/**
 * Two rhythm figures, as one bar that alternates them, against the same bar in the opposite
 * order. Every figure in either rhythm is one of the two, so recording answers only ever scores
 * the figures being compared.
 */
final class RhythmCellComparison: Comparison {
    let answers: [Rhythm]
    private(set) var examples: [ListeningPrompt] = []
    private let tempo: RhythmTempo
    private let random: RandomSource

    init(_ first: RhythmCell, _ second: RhythmCell, tempo: RhythmTempo = .medium, random: RandomSource = .system) {
        precondition(first != second, "A comparison needs two different figures")
        // The figure listed first in the rhythm settings leads the first rhythm.
        let cells = [first, second].sorted { $0.ordinal < $1.ordinal }
        answers = [Self.alternating(cells[0], cells[1]), Self.alternating(cells[1], cells[0])]
        self.tempo = tempo
        self.random = random
        examples = answers.map { RhythmQuestion(rhythm: $0, tempo: tempo, choices: answers).prompt }
    }

    func nextQuestion() -> RhythmQuestion {
        RhythmQuestion(rhythm: random.element(of: answers), tempo: tempo, choices: answers)
    }

    /**
     * One bar taking [lead] and [other] in turn. A two-beat figure must start on beat 1 or 3
     * and fit before the barline; where it cannot, the other figure goes there instead.
     */
    static func alternating(_ lead: RhythmCell, _ other: RhythmCell) -> Rhythm {
        var cells: [RhythmCell] = []
        var beat = 0
        var next = lead
        while beat < Rhythm.beatsPerBar {
            let alternative = next == lead ? other : lead
            let fits = { (cell: RhythmCell) in cell.beats == 1 || (beat % 2 == 0 && beat + cell.beats <= Rhythm.beatsPerBar) }
            let cell = fits(next) ? next : (fits(alternative) ? alternative : .quarter)
            cells.append(cell)
            beat += cell.beats
            next = cell == lead ? other : lead
        }
        return Rhythm(cells)
    }
}
