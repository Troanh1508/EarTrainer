import Foundation

/**
 * A short melody to write down by ear. It starts on the tonic, which is given; the listener
 * enters the remaining notes as scale degrees. Notes stay within the octave above the tonic, so
 * every degree names exactly one pitch.
 */
struct DictationQuestion: ExerciseQuestion {
    let key: Key
    let melody: Melody
    let context: KeyContext

    init(key: Key, melody: Melody, context: KeyContext) {
        precondition(melody.degrees.first == .tonic, "Dictation melodies start on the tonic")
        precondition(melody.degrees.allSatisfy(key.mode.diatonicDegrees.contains), "Dictation melodies use only notes of the key")
        self.key = key
        self.melody = melody
        self.context = context
    }

    /// Answers are entered note by note rather than picked from a list.
    var choices: [Melody] { [] }

    /// The notes that can be entered: the seven degrees of the key.
    var keypad: [ScaleDegree] { key.mode.diatonicDegrees }

    var correctAnswer: Melody { melody }

    /// A complete entry: the right length, the given tonic first, and only notes of the key.
    func accepts(_ answer: Melody) -> Bool {
        answer.degrees.count == melody.degrees.count &&
            answer.degrees.first == .tonic &&
            answer.degrees.allSatisfy(keypad.contains)
    }

    var prompt: ListeningPrompt {
        ListeningPrompt(moments: melody.pitches(in: key).map { [$0] }, articulation: .melodic, context: context.moments(in: key))
    }
}

struct DictationSettings: Equatable {
    var modes: Set<Mode> = [.major]
    /// Notes per melody, including the given opening tonic.
    var length = 4
    var keyChoice: KeyChoice = .random
    var context: KeyContext = .cadence
    /// Steer melodies toward degrees recently written down wrongly.
    var focusOnWeakSpots = true

    static let lengthOptions = [3, 4, 5, 6]

    var isValid: Bool { !modes.isEmpty && Self.lengthOptions.contains(length) }

    /// Adds or removes [mode], refusing to remove the last one.
    func withModeToggled(_ mode: Mode) -> DictationSettings {
        var copy = self
        if !modes.contains(mode) {
            copy.modes.insert(mode)
        } else if modes.count > 1 {
            copy.modes.remove(mode)
        }
        return copy
    }
}

final class DictationQuestionGenerator {
    private let settings: DictationSettings
    private let random: RandomSource
    /// Relative likelihood of each degree appearing; nil leaves the melody shape alone.
    private let weightOf: ((ScaleDegree) -> Double)?

    /// Likelihood by distance in scale steps: steps 4, thirds 2, fourths 1, fifths 0.5, wider 0.25.
    private static let motionWeights = [0.0, 4.0, 2.0, 1.0, 0.5, 0.25, 0.25]

    init(settings: DictationSettings = DictationSettings(), random: RandomSource = .system, weightOf: ((ScaleDegree) -> Double)? = nil) {
        self.settings = settings
        self.random = random
        self.weightOf = weightOf
    }

    func generate() -> DictationQuestion {
        let mode = random.element(of: settings.modes.inCatalogOrder)
        let key = Key(tonic: settings.keyChoice.pickTonic(random), mode: mode)
        return DictationQuestion(key: key, melody: melody(mode), context: settings.context)
    }

    /**
     * A random walk over the scale that sounds like a simple tune: mostly steps, sometimes a
     * third, rarely a wider leap, never a repeated note. Weak-spot weights multiply in, so
     * degrees the listener misses turn up more often without making the line jumpy.
     */
    private func melody(_ mode: Mode) -> Melody {
        let scale = mode.diatonicDegrees
        var steps = [0]
        for _ in 0..<(settings.length - 1) {
            let current = steps.last!
            let candidates = scale.indices.filter { $0 != current }
            let next = candidates.weightedRandom(random) { candidate in
                let distance = abs(candidate - current)
                let shape = distance < Self.motionWeights.count ? Self.motionWeights[distance] : 0
                return shape * (weightOf?(scale[candidate]) ?? 1)
            }
            steps.append(next)
        }
        return Melody(steps.map { scale[$0] })
    }
}
