import Foundation

/// Which degrees questions can ask about.
enum DegreeSet: String, StoredEnum {
    /// The seven notes of the question's mode.
    case diatonic = "DIATONIC"
    /// All twelve notes, including ones outside the key.
    case chromatic = "CHROMATIC"

    var displayName: String {
        switch self {
        case .diatonic: "Notes in the key"
        case .chromatic: "All 12 notes"
        }
    }
}

struct ScaleDegreeQuestion: ExerciseQuestion {
    let key: Key
    let degree: ScaleDegree
    let context: KeyContext
    let choices: [ScaleDegree]

    init(key: Key, degree: ScaleDegree, context: KeyContext, choices: [ScaleDegree]) {
        requireValidChoices(choices, degree)
        self.key = key
        self.degree = degree
        self.context = context
        self.choices = choices
    }

    /// The note to identify, in the octave above the tonic.
    var target: Pitch { key.pitchOf(degree) }

    var correctAnswer: ScaleDegree { degree }

    var prompt: ListeningPrompt { ListeningPrompt(moments: [[target]], context: context.moments(in: key)) }
}

struct ScaleDegreeSettings: Equatable {
    var modes: Set<Mode> = [.major]
    var degreeSet: DegreeSet = .diatonic
    var keyChoice: KeyChoice = .random
    var context: KeyContext = .cadence
    /// Ask more often about degrees recently answered wrongly.
    var focusOnWeakSpots = true

    var isValid: Bool { !modes.isEmpty }

    /// Adds or removes [mode], refusing to remove the last one.
    func withModeToggled(_ mode: Mode) -> ScaleDegreeSettings {
        var copy = self
        if !modes.contains(mode) {
            copy.modes.insert(mode)
        } else if modes.count > 1 {
            copy.modes.remove(mode)
        }
        return copy
    }

    /// The degrees a question in [mode] can ask about, which are also its answer choices.
    func degrees(for mode: Mode) -> [ScaleDegree] {
        switch degreeSet {
        case .diatonic: mode.diatonicDegrees
        case .chromatic: ScaleDegree.allCases
        }
    }
}

final class ScaleDegreeQuestionGenerator {
    private let settings: ScaleDegreeSettings
    private let random: RandomSource
    /// Relative likelihood of asking each degree; nil asks them all equally often.
    private let weightOf: ((ScaleDegree) -> Double)?

    init(settings: ScaleDegreeSettings = ScaleDegreeSettings(), random: RandomSource = .system, weightOf: ((ScaleDegree) -> Double)? = nil) {
        self.settings = settings
        self.random = random
        self.weightOf = weightOf
    }

    func generate() -> ScaleDegreeQuestion {
        let mode = random.element(of: settings.modes.inCatalogOrder)
        let tonic = settings.keyChoice.pickTonic(random)
        let choices = settings.degrees(for: mode)
        let degree = weightOf.map { choices.weightedRandom(random, weightOf: $0) } ?? random.element(of: choices)
        return ScaleDegreeQuestion(key: Key(tonic: tonic, mode: mode), degree: degree, context: settings.context, choices: choices)
    }
}
