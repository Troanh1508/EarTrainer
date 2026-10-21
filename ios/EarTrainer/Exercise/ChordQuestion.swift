import Foundation

/// How a chord question's notes are played.
enum ChordStyle: String, StoredEnum {
    case block = "BLOCK"
    case arpeggiated = "ARPEGGIATED"

    var displayName: String {
        switch self {
        case .block: "Block chord"
        case .arpeggiated: "Arpeggio"
        }
    }
}

/// What the listener is asked to name.
enum ChordTarget: String, StoredEnum {
    case quality = "QUALITY"
    case inversion = "INVERSION"

    var displayName: String {
        switch self {
        case .quality: "Chord quality"
        case .inversion: "Inversion"
        }
    }
}

/// An answer to a chord question: either a quality or an inversion, depending on the target.
enum ChordAnswer: Hashable {
    case quality(ChordQuality)
    case inversion(ChordInversion)

    var displayName: String {
        switch self {
        case .quality(let quality): quality.displayName
        case .inversion(let inversion): inversion.displayName
        }
    }

    /// The stored name of the quality or inversion.
    var name: String {
        switch self {
        case .quality(let quality): quality.name
        case .inversion(let inversion): inversion.name
        }
    }

    /// Catalog position, for listing qualities or inversions in their usual order.
    var ordinal: Int {
        switch self {
        case .quality(let quality): quality.ordinal
        case .inversion(let inversion): inversion.ordinal
        }
    }
}

struct ChordQuestion: ExerciseQuestion {
    /// Lowest sounding note.
    let bass: Pitch
    let quality: ChordQuality
    let inversion: ChordInversion
    let style: ChordStyle
    let target: ChordTarget
    let choices: [ChordAnswer]

    init(bass: Pitch, quality: ChordQuality, inversion: ChordInversion, style: ChordStyle, target: ChordTarget, choices: [ChordAnswer]) {
        precondition(quality.inversions.contains(inversion), "\(quality.displayName) chords have no \(inversion.displayName)")
        precondition(Pitch.midiRange.contains(bass.midi + quality.offsetsAboveBass(inversion).last!), "Chord exceeds the MIDI range")
        self.bass = bass
        self.quality = quality
        self.inversion = inversion
        self.style = style
        self.target = target
        self.choices = choices
        requireValidChoices(choices, correctAnswer)
    }

    /// Sounding notes from the bass up, in close position.
    var pitches: [Pitch] { quality.offsetsAboveBass(inversion).map { bass.transposed(by: $0) } }

    /// Name of the chord root, spelled with sharps (e.g. "D♯").
    var rootName: String { bass.transposed(by: quality.rootOffsetAboveBass(inversion)).pitchClassName }

    var correctAnswer: ChordAnswer {
        switch target {
        case .quality: .quality(quality)
        case .inversion: .inversion(inversion)
        }
    }

    var prompt: ListeningPrompt {
        switch style {
        case .block: .simultaneous(pitches)
        case .arpeggiated: .arpeggiated(pitches)
        }
    }
}

struct ChordExerciseSettings: Equatable {
    var qualities: [ChordQuality] = [.major, .minor, .diminished, .augmented]
    var styles: Set<ChordStyle> = [.block]
    var target: ChordTarget = .quality
    /// When naming qualities, also play chords in inversions. Naming inversions always uses them.
    var includeInversions = false
    /// Every sounded pitch stays within this range.
    var pitchRange: ClosedRange<Int> = 48...84
    /// Ask more often about qualities or inversions recently answered wrongly.
    var focusOnWeakSpots = true

    /// Fewer than two qualities would make quality questions a giveaway.
    static let minQualities = 2

    /// Widest close-position shape in semitones (a major 7th chord in root position: 11).
    static let maxChordSpan: Int = ChordQuality.allCases.map { quality in
        quality.inversions.map { quality.offsetsAboveBass($0).last! }.max()!
    }.max()!

    var isValid: Bool {
        qualities.count >= Self.minQualities &&
            Set(qualities).count == qualities.count &&
            !styles.isEmpty &&
            Pitch.midiRange.contains(pitchRange.lowerBound) && Pitch.midiRange.contains(pitchRange.upperBound) &&
            pitchRange.upperBound - pitchRange.lowerBound >= Self.maxChordSpan
    }

    /// Inversions a question for [quality] may use under these settings.
    func inversions(for quality: ChordQuality) -> [ChordInversion] {
        target == .inversion || includeInversions ? quality.inversions : [.rootPosition]
    }

    /// Adds or removes [quality], refusing to drop below `minQualities`. Keeps catalog order.
    func withQualityToggled(_ quality: ChordQuality) -> ChordExerciseSettings {
        var copy = self
        if !qualities.contains(quality) {
            copy.qualities = ChordQuality.allCases.filter { qualities.contains($0) || $0 == quality }
        } else if qualities.count > Self.minQualities {
            copy.qualities = qualities.filter { $0 != quality }
        }
        return copy
    }

    /// Adds or removes [style], refusing to remove the last one.
    func withStyleToggled(_ style: ChordStyle) -> ChordExerciseSettings {
        var copy = self
        if !styles.contains(style) {
            copy.styles.insert(style)
        } else if styles.count > 1 {
            copy.styles.remove(style)
        }
        return copy
    }
}

final class ChordQuestionGenerator {
    private let settings: ChordExerciseSettings
    private let random: RandomSource
    /// Relative likelihood of asking about each answer; nil asks them all equally often.
    private let weightOf: ((ChordAnswer) -> Double)?

    init(settings: ChordExerciseSettings = ChordExerciseSettings(), random: RandomSource = .system, weightOf: ((ChordAnswer) -> Double)? = nil) {
        self.settings = settings
        self.random = random
        self.weightOf = weightOf
    }

    func generate() -> ChordQuestion {
        // Weights apply to whatever is being named: the quality, or the inversion.
        let quality: ChordQuality
        if let weightOf, settings.target == .quality {
            quality = settings.qualities.weightedRandom(random) { weightOf(.quality($0)) }
        } else {
            quality = random.element(of: settings.qualities)
        }
        let inversions = settings.inversions(for: quality)
        let inversion: ChordInversion
        if let weightOf, settings.target == .inversion {
            inversion = inversions.weightedRandom(random) { weightOf(.inversion($0)) }
        } else {
            inversion = random.element(of: inversions)
        }
        let span = quality.offsetsAboveBass(inversion).last!
        let bass = Pitch(random.int(in: settings.pitchRange.lowerBound...(settings.pitchRange.upperBound - span)))
        let choices: [ChordAnswer] = switch settings.target {
        case .quality: settings.qualities.map { .quality($0) }
        case .inversion: quality.inversions.map { .inversion($0) }
        }
        return ChordQuestion(
            bass: bass,
            quality: quality,
            inversion: inversion,
            style: random.element(of: settings.styles.inCatalogOrder),
            target: settings.target,
            choices: choices
        )
    }
}
