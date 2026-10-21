import UIKit

final class IntervalSettingsViewController: ExerciseSettingsViewController<IntervalExerciseSettings> {
    init() { super.init(store: AppContainer.shared.intervalSettings, defaults: IntervalExerciseSettings()) }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func exerciseSections(_ settings: IntervalExerciseSettings) -> [FormSection] {
        [
            FormSection(
                header: Strings.intervalsTitle,
                footer: Strings.intervalsDescription(IntervalExerciseSettings.minIntervals),
                rows: options(Interval.allCases, selected: settings.intervals.contains, title: \.displayName) { $0.withIntervalToggled($1) } + [
                    .button(title: Strings.selectAll, enabled: settings.intervals.count < Interval.allCases.count) { [weak self] in
                        self?.update { var all = $0; all.intervals = Interval.allCases; return all }
                    },
                ]
            ),
            FormSection(
                header: Strings.stylesTitle, footer: Strings.stylesDescription,
                rows: options(IntervalStyle.allCases, selected: settings.styles.contains, title: \.displayName) { $0.withStyleToggled($1) }
            ),
            FormSection(
                header: Strings.choicesTitle, footer: Strings.choicesDescription,
                rows: options(IntervalExerciseSettings.choiceCountOptions, selected: { $0 == settings.choiceCount },
                              title: { $0 == Interval.allCases.count ? Strings.choicesAll : String($0) }) { settings, count in
                    var copy = settings
                    copy.choiceCount = count
                    return copy
                }
            ),
        ]
    }

    override func focus(_ settings: IntervalExerciseSettings) -> Bool { settings.focusOnWeakSpots }

    override func withFocus(_ settings: IntervalExerciseSettings, _ focus: Bool) -> IntervalExerciseSettings {
        var copy = settings
        copy.focusOnWeakSpots = focus
        return copy
    }
}

final class ChordSettingsViewController: ExerciseSettingsViewController<ChordExerciseSettings> {
    init() { super.init(store: AppContainer.shared.chordSettings, defaults: ChordExerciseSettings()) }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func exerciseSections(_ settings: ChordExerciseSettings) -> [FormSection] {
        // Naming inversions always uses them, so the switch only matters when naming qualities.
        let namingInversions = settings.target == .inversion
        return [
            FormSection(
                header: Strings.chordTargetTitle, footer: Strings.chordTargetDescription,
                rows: options(ChordTarget.allCases, selected: { $0 == settings.target }, title: \.displayName) { settings, target in
                    var copy = settings
                    copy.target = target
                    return copy
                }
            ),
            FormSection(
                header: Strings.chordQualitiesTitle,
                footer: Strings.chordQualitiesDescription(ChordExerciseSettings.minQualities),
                rows: options(ChordQuality.allCases, selected: settings.qualities.contains, title: \.displayName) { $0.withQualityToggled($1) } + [
                    .button(title: Strings.selectAll, enabled: settings.qualities.count < ChordQuality.allCases.count) { [weak self] in
                        self?.update { var all = $0; all.qualities = ChordQuality.allCases; return all }
                    },
                ]
            ),
            FormSection(header: nil, footer: namingInversions ? Strings.inversionsAlways : Strings.inversionsDescription, rows: [
                .toggle(title: Strings.inversionsTitle, subtitle: nil, isOn: namingInversions || settings.includeInversions, enabled: !namingInversions) { [weak self] isOn in
                    self?.update { var copy = $0; copy.includeInversions = isOn; return copy }
                },
            ]),
            FormSection(
                header: Strings.stylesTitle, footer: Strings.stylesDescription,
                rows: options(ChordStyle.allCases, selected: settings.styles.contains, title: \.displayName) { $0.withStyleToggled($1) }
            ),
        ]
    }

    override func focus(_ settings: ChordExerciseSettings) -> Bool { settings.focusOnWeakSpots }

    override func withFocus(_ settings: ChordExerciseSettings, _ focus: Bool) -> ChordExerciseSettings {
        var copy = settings
        copy.focusOnWeakSpots = focus
        return copy
    }
}

final class ScaleDegreeSettingsViewController: ExerciseSettingsViewController<ScaleDegreeSettings> {
    init() { super.init(store: AppContainer.shared.scaleDegreeSettings, defaults: ScaleDegreeSettings()) }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func exerciseSections(_ settings: ScaleDegreeSettings) -> [FormSection] {
        [
            FormSection(
                header: Strings.modesTitle, footer: Strings.modesDescription,
                rows: options(Mode.allCases, selected: settings.modes.contains, title: \.displayName) { $0.withModeToggled($1) }
            ),
            FormSection(
                header: Strings.degreesTitle, footer: Strings.degreesDescription,
                rows: options(DegreeSet.allCases, selected: { $0 == settings.degreeSet }, title: \.displayName) { settings, set in
                    var copy = settings
                    copy.degreeSet = set
                    return copy
                }
            ),
            FormSection(
                header: Strings.keyTitle, footer: Strings.keyDescription,
                rows: options(KeyChoice.allCases, selected: { $0 == settings.keyChoice }, title: \.displayName) { settings, choice in
                    var copy = settings
                    copy.keyChoice = choice
                    return copy
                }
            ),
            FormSection(
                header: Strings.contextTitle, footer: Strings.contextDescription,
                rows: options(KeyContext.allCases, selected: { $0 == settings.context }, title: \.displayName) { settings, context in
                    var copy = settings
                    copy.context = context
                    return copy
                }
            ),
        ]
    }

    override func focus(_ settings: ScaleDegreeSettings) -> Bool { settings.focusOnWeakSpots }

    override func withFocus(_ settings: ScaleDegreeSettings, _ focus: Bool) -> ScaleDegreeSettings {
        var copy = settings
        copy.focusOnWeakSpots = focus
        return copy
    }
}

final class ProgressionSettingsViewController: ExerciseSettingsViewController<ProgressionSettings> {
    init() { super.init(store: AppContainer.shared.progressionSettings, defaults: ProgressionSettings()) }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func exerciseSections(_ settings: ProgressionSettings) -> [FormSection] {
        [
            FormSection(
                header: Strings.sourceTitle, footer: Strings.sourceDescription,
                rows: options(ProgressionSource.allCases, selected: { $0 == settings.source }, title: \.displayName) { settings, source in
                    var copy = settings
                    copy.source = source
                    return copy
                }
            ),
            FormSection(
                header: Strings.modesTitle, footer: Strings.modesDescription,
                rows: options(Mode.allCases, selected: settings.modes.contains, title: \.displayName) { $0.withModeToggled($1) }
            ),
            FormSection(
                header: Strings.vocabularyTitle, footer: Strings.vocabularyDescription,
                rows: options(ChordVocabulary.allCases, selected: { $0 == settings.vocabulary }, title: \.displayName) { settings, vocabulary in
                    var copy = settings
                    copy.vocabulary = vocabulary
                    return copy
                }
            ),
            FormSection(
                header: Strings.progressionLengthTitle, footer: Strings.progressionLengthDescription,
                rows: options(ProgressionSettings.lengthOptions, selected: { $0 == settings.length }, title: { String($0) }) { settings, length in
                    var copy = settings
                    copy.length = length
                    return copy
                }
            ),
            FormSection(
                header: Strings.keyTitle, footer: Strings.keyDescription,
                rows: options(KeyChoice.allCases, selected: { $0 == settings.keyChoice }, title: \.displayName) { settings, choice in
                    var copy = settings
                    copy.keyChoice = choice
                    return copy
                }
            ),
        ]
    }

    override func focus(_ settings: ProgressionSettings) -> Bool { settings.focusOnWeakSpots }

    override func withFocus(_ settings: ProgressionSettings, _ focus: Bool) -> ProgressionSettings {
        var copy = settings
        copy.focusOnWeakSpots = focus
        return copy
    }
}

final class DictationSettingsViewController: ExerciseSettingsViewController<DictationSettings> {
    init() { super.init(store: AppContainer.shared.dictationSettings, defaults: DictationSettings()) }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func exerciseSections(_ settings: DictationSettings) -> [FormSection] {
        [
            FormSection(
                header: Strings.modesTitle, footer: Strings.modesDescription,
                rows: options(Mode.allCases, selected: settings.modes.contains, title: \.displayName) { $0.withModeToggled($1) }
            ),
            FormSection(
                header: Strings.dictationLengthTitle, footer: Strings.dictationLengthDescription,
                rows: options(DictationSettings.lengthOptions, selected: { $0 == settings.length }, title: { String($0) }) { settings, length in
                    var copy = settings
                    copy.length = length
                    return copy
                }
            ),
            FormSection(
                header: Strings.keyTitle, footer: Strings.keyDescription,
                rows: options(KeyChoice.allCases, selected: { $0 == settings.keyChoice }, title: \.displayName) { settings, choice in
                    var copy = settings
                    copy.keyChoice = choice
                    return copy
                }
            ),
            FormSection(
                header: Strings.contextTitle, footer: Strings.contextDescription,
                rows: options(KeyContext.allCases, selected: { $0 == settings.context }, title: \.displayName) { settings, context in
                    var copy = settings
                    copy.context = context
                    return copy
                }
            ),
        ]
    }

    override func focus(_ settings: DictationSettings) -> Bool { settings.focusOnWeakSpots }

    override func withFocus(_ settings: DictationSettings, _ focus: Bool) -> DictationSettings {
        var copy = settings
        copy.focusOnWeakSpots = focus
        return copy
    }
}

final class RhythmSettingsViewController: ExerciseSettingsViewController<RhythmSettings> {
    init() { super.init(store: AppContainer.shared.rhythmSettings, defaults: RhythmSettings()) }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func exerciseSections(_ settings: RhythmSettings) -> [FormSection] {
        [
            FormSection(
                header: Strings.rhythmVocabularyTitle, footer: Strings.rhythmVocabularyDescription,
                rows: options(RhythmVocabulary.allCases, selected: { $0 == settings.vocabulary }, title: \.displayName) { settings, vocabulary in
                    var copy = settings
                    copy.vocabulary = vocabulary
                    return copy
                }
            ),
            FormSection(
                header: Strings.barsTitle, footer: Strings.barsDescription,
                rows: options(RhythmSettings.barOptions, selected: { $0 == settings.bars }, title: Strings.bars) { settings, bars in
                    var copy = settings
                    copy.bars = bars
                    return copy
                }
            ),
            FormSection(
                header: Strings.tempoTitle, footer: Strings.tempoDescription,
                rows: options(RhythmTempo.allCases, selected: { $0 == settings.tempo }, title: { Strings.tempoOption($0.displayName, $0.bpm) }) { settings, tempo in
                    var copy = settings
                    copy.tempo = tempo
                    return copy
                }
            ),
        ]
    }

    override func focus(_ settings: RhythmSettings) -> Bool { settings.focusOnWeakSpots }

    override func withFocus(_ settings: RhythmSettings, _ focus: Bool) -> RhythmSettings {
        var copy = settings
        copy.focusOnWeakSpots = focus
        return copy
    }
}
