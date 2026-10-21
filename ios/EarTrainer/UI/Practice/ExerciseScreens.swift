import UIKit

/// A scale degree as listed in charts and summaries, e.g. "♭3 · Me".
func degreeLabel(_ degree: ScaleDegree) -> String { "\(degree.label) · \(degree.solfege)" }

/// A rhythm figure as listed in charts and summaries, e.g. "Two eighths".
func rhythmLabel(_ cell: RhythmCell) -> String { cell.displayName.capitalizedFirst }

/// Builds each exercise's practice screen and settings screen.
@MainActor
enum ExerciseScreens {

    static func practice(_ exercise: ExerciseKind) -> UIViewController {
        switch exercise {
        case .intervals: intervals()
        case .chords: chords()
        case .scaleDegrees: scaleDegrees()
        case .progressions: progressions()
        case .dictation: dictation()
        case .rhythm: rhythm()
        }
    }

    static func settings(_ exercise: ExerciseKind) -> UIViewController {
        switch exercise {
        case .intervals: IntervalSettingsViewController()
        case .chords: ChordSettingsViewController()
        case .scaleDegrees: ScaleDegreeSettingsViewController()
        case .progressions: ProgressionSettingsViewController()
        case .dictation: DictationSettingsViewController()
        case .rhythm: RhythmSettingsViewController()
        }
    }

    private static var container: AppContainer { .shared }

    private static func recentSessions(_ exercise: ExerciseKind) -> () async -> [SessionSummary] {
        let history = container.history
        return { await history.recentSessions(limit: PracticeSummary.averageOverSessions + 1, exercise: exercise) }
    }

    private static func intervals() -> UIViewController {
        let history = container.history
        let weakSpots = container.weakSpots
        let viewModel = PracticeViewModel<IntervalQuestion, IntervalExerciseSettings>(
            store: container.intervalSettings,
            questionSource: { settings in
                IntervalQuestionGenerator(settings: settings, weightOf: settings.focusOnWeakSpots ? weakSpots.intervalWeight : nil).generate
            },
            recordAttempt: { state, sessionId in IntervalAttempt(state, sessionId: sessionId).map { history.record($0) } },
            audioPlayer: container.makeAudioPlayer(),
            itemsOf: wholeAnswerItems { $0.displayName },
            recentSessions: recentSessions(.intervals)
        )
        let texts = PracticeTexts<IntervalQuestion>(
            title: Strings.exerciseTitle(.intervals),
            exercise: .intervals,
            instruction: { _ in Strings.intervalsInstruction },
            questionDetail: { $0.style.displayName },
            choiceLabel: { ChoiceLabel(title: $0.displayName, subtitle: $0.abbreviation) },
            correctSummary: { "\($0.interval.displayName) · \(Strings.semitones($0.interval.semitones))" }
        )
        return PracticeViewController(viewModel: viewModel, texts: texts, makeSettings: { settings(.intervals) })
    }

    private static func chords() -> UIViewController {
        let history = container.history
        let weakSpots = container.weakSpots
        let viewModel = PracticeViewModel<ChordQuestion, ChordExerciseSettings>(
            store: container.chordSettings,
            questionSource: { settings in
                ChordQuestionGenerator(settings: settings, weightOf: settings.focusOnWeakSpots ? weakSpots.chordWeight : nil).generate
            },
            recordAttempt: { state, sessionId in ChordAttempt(state, sessionId: sessionId).map { history.record($0) } },
            audioPlayer: container.makeAudioPlayer(),
            itemsOf: wholeAnswerItems { $0.displayName },
            recentSessions: recentSessions(.chords)
        )
        let texts = PracticeTexts<ChordQuestion>(
            title: Strings.exerciseTitle(.chords),
            exercise: .chords,
            instruction: { $0.target == .quality ? Strings.chordsInstructionQuality : Strings.chordsInstructionInversion },
            questionDetail: { $0.style.displayName },
            choiceLabel: { answer in
                // Which chord tone the bass note is, shown under each inversion choice.
                if case .inversion(let inversion) = answer { return ChoiceLabel(title: answer.displayName, subtitle: Strings.bassNote(inversion)) }
                return ChoiceLabel(title: answer.displayName)
            },
            correctSummary: { Strings.chordsSummary($0.rootName, $0.quality.displayName.lowercased(), $0.inversion.displayName) }
        )
        return PracticeViewController(viewModel: viewModel, texts: texts, makeSettings: { settings(.chords) })
    }

    private static func scaleDegrees() -> UIViewController {
        let history = container.history
        let weakSpots = container.weakSpots
        let viewModel = PracticeViewModel<ScaleDegreeQuestion, ScaleDegreeSettings>(
            store: container.scaleDegreeSettings,
            questionSource: { settings in
                ScaleDegreeQuestionGenerator(settings: settings, weightOf: settings.focusOnWeakSpots ? weakSpots.scaleDegreeWeight : nil).generate
            },
            recordAttempt: { state, sessionId in ScaleDegreeAttempt(state, sessionId: sessionId).map { history.record($0) } },
            audioPlayer: container.makeAudioPlayer(),
            itemsOf: wholeAnswerItems(degreeLabel),
            recentSessions: recentSessions(.scaleDegrees)
        )
        var texts = PracticeTexts<ScaleDegreeQuestion>(
            title: Strings.exerciseTitle(.scaleDegrees),
            exercise: .scaleDegrees,
            instruction: { _ in Strings.degreesInstruction },
            // The key is shown up front: knowing it is part of the exercise, not the answer.
            questionDetail: { $0.key.name },
            choiceLabel: { ChoiceLabel(title: $0.label, subtitle: $0.solfege) },
            correctSummary: { Strings.degreesSummary($0.degree.label, $0.degree.solfege, $0.key.name) }
        )
        texts.noteName = { question, pitch in question.key.noteName(pitch) + String(pitch.octave) }
        // Degrees are short ("♭3"), so four fit comfortably in a row.
        texts.choiceColumns = 4
        return PracticeViewController(viewModel: viewModel, texts: texts, makeSettings: { settings(.scaleDegrees) })
    }

    private static func progressions() -> UIViewController {
        let history = container.history
        let weakSpots = container.weakSpots
        let viewModel = PracticeViewModel<ProgressionQuestion, ProgressionSettings>(
            store: container.progressionSettings,
            questionSource: { settings in
                ProgressionQuestionGenerator(settings: settings, weightOf: settings.focusOnWeakSpots ? weakSpots.progressionChordWeight : nil).generate
            },
            recordAttempt: { state, sessionId in ProgressionAttempt(state, sessionId: sessionId).map { history.record($0) } },
            audioPlayer: container.makeAudioPlayer(),
            itemsOf: { question, answer in
                let asked = question.progression
                return progressionChordPairs(asked.mode, asked: asked.chords.map(\.step), chosen: answer.chords.map(\.step)).map {
                    SummaryItem(label: $0.asked.numeral, chosenLabel: $0.chosen?.numeral, isCorrect: $0.chosen == $0.asked)
                }
            },
            recentSessions: recentSessions(.progressions)
        )
        var texts = PracticeTexts<ProgressionQuestion>(
            title: Strings.exerciseTitle(.progressions),
            exercise: .progressions,
            instruction: { _ in Strings.progressionsInstruction },
            questionDetail: { $0.key.name },
            choiceLabel: { ChoiceLabel(title: $0.label) },
            correctSummary: { Strings.progressionsSummary($0.progression.label, $0.key.name, $0.progression.symbols(in: $0.key)) }
        )
        // Listing every note of every chord would be a wall of text; chord names say it better.
        texts.showNotesAsPlayed = false
        // Progression labels are long, so each choice gets a full-width row.
        texts.choiceColumns = 1
        return PracticeViewController(viewModel: viewModel, texts: texts, makeSettings: { settings(.progressions) })
    }

    private static func dictation() -> UIViewController {
        let history = container.history
        let weakSpots = container.weakSpots
        let viewModel = PracticeViewModel<DictationQuestion, DictationSettings>(
            store: container.dictationSettings,
            questionSource: { settings in
                DictationQuestionGenerator(settings: settings, weightOf: settings.focusOnWeakSpots ? weakSpots.dictationDegreeWeight : nil).generate
            },
            recordAttempt: { state, sessionId in DictationAttempt(state, sessionId: sessionId).map { history.record($0) } },
            audioPlayer: container.makeAudioPlayer(),
            itemsOf: { question, answer in
                dictationNotePairs(question.melody.degrees, answer.degrees).map {
                    SummaryItem(label: degreeLabel($0.played), chosenLabel: $0.written.map(degreeLabel), isCorrect: $0.written == $0.played)
                }
            },
            recentSessions: recentSessions(.dictation)
        )
        var texts = PracticeTexts<DictationQuestion>(
            title: Strings.exerciseTitle(.dictation),
            exercise: .dictation,
            instruction: { _ in Strings.dictationInstruction },
            questionDetail: { $0.key.name },
            choiceLabel: { ChoiceLabel(title: $0.label) },
            correctSummary: { Strings.dictationSummary($0.melody.label, $0.melody.solfege, $0.key.name) }
        )
        texts.noteName = { question, pitch in question.key.noteName(pitch) + String(pitch.octave) }
        return PracticeViewController(
            viewModel: viewModel, texts: texts, makeSettings: { settings(.dictation) },
            answerArea: { viewModel in DictationAnswerArea { [weak viewModel] in viewModel?.onAnswerSelected($0) } }
        )
    }

    private static func rhythm() -> UIViewController {
        let history = container.history
        let weakSpots = container.weakSpots
        let viewModel = PracticeViewModel<RhythmQuestion, RhythmSettings>(
            store: container.rhythmSettings,
            questionSource: { settings in
                RhythmQuestionGenerator(settings: settings, weightOf: settings.focusOnWeakSpots ? weakSpots.rhythmCellWeight : nil).generate
            },
            recordAttempt: { state, sessionId in RhythmAttempt(state, sessionId: sessionId).map { history.record($0) } },
            audioPlayer: container.makeAudioPlayer(),
            itemsOf: { question, answer in
                rhythmCellPairs(question.rhythm, answer).map {
                    SummaryItem(label: rhythmLabel($0.played), chosenLabel: $0.chosen.map(rhythmLabel), isCorrect: $0.chosen == $0.played)
                }
            },
            recentSessions: recentSessions(.rhythm)
        )
        var texts = PracticeTexts<RhythmQuestion>(
            title: Strings.exerciseTitle(.rhythm),
            exercise: .rhythm,
            instruction: { _ in Strings.rhythmInstruction },
            questionDetail: { Strings.rhythmDetail($0.tempo.bpm) },
            // Read aloud for each drawn choice and used in "You answered …".
            choiceLabel: { ChoiceLabel(title: $0.description) },
            correctSummary: { _ in Strings.rhythmSummary }
        )
        // The notes are all the same pitch, so listing them says nothing.
        texts.showNotesAsPlayed = false
        texts.choiceGraphic = { RhythmNotationView($0) }
        texts.showSelectedInFeedback = false
        // Notation needs the full width to stay legible, so choices are stacked.
        texts.choiceColumns = 1
        return PracticeViewController(viewModel: viewModel, texts: texts, makeSettings: { settings(.rhythm) })
    }
}
