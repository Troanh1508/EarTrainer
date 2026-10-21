import Foundation

/*
 * How each group of settings is stored. Decoding ignores unknown names and falls back to
 * defaults for anything missing or invalid; the key names match the Android app's.
 */

enum IntervalSettingsCodec {
    private static let intervals = "interval_training.intervals"
    private static let styles = "interval_training.styles"
    private static let choiceCount = "interval_training.choice_count"
    private static let focus = "interval_training.focus_weak_spots"

    static func decode(_ preferences: Preferences) -> IntervalExerciseSettings {
        let defaults = IntervalExerciseSettings()
        var settings = defaults
        settings.intervals = (preferences.enumSet(intervals) as [Interval]?)?.sorted { $0.semitones < $1.semitones } ?? defaults.intervals
        settings.styles = (preferences.enumSet(styles) as [IntervalStyle]?).map(Set.init) ?? defaults.styles
        settings.choiceCount = preferences.int(choiceCount).flatMap { IntervalExerciseSettings.choiceCountOptions.contains($0) ? $0 : nil } ?? defaults.choiceCount
        settings.focusOnWeakSpots = preferences.bool(focus) ?? defaults.focusOnWeakSpots
        return settings.isValid ? settings : defaults
    }

    static func encode(_ settings: IntervalExerciseSettings, _ preferences: inout Preferences) {
        preferences[intervals] = .stringSet(Set(settings.intervals.map(\.name)))
        preferences[styles] = .stringSet(Set(settings.styles.map(\.name)))
        preferences[choiceCount] = .int(settings.choiceCount)
        preferences[focus] = .bool(settings.focusOnWeakSpots)
    }
}

enum ChordSettingsCodec {
    private static let qualities = "chord_training.qualities"
    private static let styles = "chord_training.styles"
    private static let target = "chord_training.target"
    private static let includeInversions = "chord_training.include_inversions"
    private static let focus = "chord_training.focus_weak_spots"

    static func decode(_ preferences: Preferences) -> ChordExerciseSettings {
        let defaults = ChordExerciseSettings()
        var settings = defaults
        settings.qualities = preferences.enumSet(qualities) ?? defaults.qualities
        settings.styles = (preferences.enumSet(styles) as [ChordStyle]?).map(Set.init) ?? defaults.styles
        settings.target = preferences.enumValue(target) ?? defaults.target
        settings.includeInversions = preferences.bool(includeInversions) ?? defaults.includeInversions
        settings.focusOnWeakSpots = preferences.bool(focus) ?? defaults.focusOnWeakSpots
        return settings.isValid ? settings : defaults
    }

    static func encode(_ settings: ChordExerciseSettings, _ preferences: inout Preferences) {
        preferences[qualities] = .stringSet(Set(settings.qualities.map(\.name)))
        preferences[styles] = .stringSet(Set(settings.styles.map(\.name)))
        preferences[target] = .string(settings.target.name)
        preferences[includeInversions] = .bool(settings.includeInversions)
        preferences[focus] = .bool(settings.focusOnWeakSpots)
    }
}

enum ScaleDegreeSettingsCodec {
    private static let modes = "scale_degrees.modes"
    private static let degreeSet = "scale_degrees.degree_set"
    private static let keyChoice = "scale_degrees.key_choice"
    private static let context = "scale_degrees.context"
    private static let focus = "scale_degrees.focus_weak_spots"

    static func decode(_ preferences: Preferences) -> ScaleDegreeSettings {
        let defaults = ScaleDegreeSettings()
        var settings = defaults
        settings.modes = (preferences.enumSet(modes) as [Mode]?).map(Set.init) ?? defaults.modes
        settings.degreeSet = preferences.enumValue(degreeSet) ?? defaults.degreeSet
        settings.keyChoice = preferences.enumValue(keyChoice) ?? defaults.keyChoice
        settings.context = preferences.enumValue(context) ?? defaults.context
        settings.focusOnWeakSpots = preferences.bool(focus) ?? defaults.focusOnWeakSpots
        return settings.isValid ? settings : defaults
    }

    static func encode(_ settings: ScaleDegreeSettings, _ preferences: inout Preferences) {
        preferences[modes] = .stringSet(Set(settings.modes.map(\.name)))
        preferences[degreeSet] = .string(settings.degreeSet.name)
        preferences[keyChoice] = .string(settings.keyChoice.name)
        preferences[context] = .string(settings.context.name)
        preferences[focus] = .bool(settings.focusOnWeakSpots)
    }
}

enum ProgressionSettingsCodec {
    private static let modes = "progressions.modes"
    private static let vocabulary = "progressions.vocabulary"
    private static let length = "progressions.length"
    private static let keyChoice = "progressions.key_choice"
    private static let source = "progressions.source"
    private static let focus = "progressions.focus_weak_spots"

    static func decode(_ preferences: Preferences) -> ProgressionSettings {
        let defaults = ProgressionSettings()
        var settings = defaults
        settings.modes = (preferences.enumSet(modes) as [Mode]?).map(Set.init) ?? defaults.modes
        settings.vocabulary = preferences.enumValue(vocabulary) ?? defaults.vocabulary
        settings.length = preferences.int(length).flatMap { ProgressionSettings.lengthOptions.contains($0) ? $0 : nil } ?? defaults.length
        settings.keyChoice = preferences.enumValue(keyChoice) ?? defaults.keyChoice
        settings.source = preferences.enumValue(source) ?? defaults.source
        settings.focusOnWeakSpots = preferences.bool(focus) ?? defaults.focusOnWeakSpots
        return settings.isValid ? settings : defaults
    }

    static func encode(_ settings: ProgressionSettings, _ preferences: inout Preferences) {
        preferences[modes] = .stringSet(Set(settings.modes.map(\.name)))
        preferences[vocabulary] = .string(settings.vocabulary.name)
        preferences[length] = .int(settings.length)
        preferences[keyChoice] = .string(settings.keyChoice.name)
        preferences[source] = .string(settings.source.name)
        preferences[focus] = .bool(settings.focusOnWeakSpots)
    }
}

enum DictationSettingsCodec {
    private static let modes = "dictation.modes"
    private static let length = "dictation.length"
    private static let keyChoice = "dictation.key_choice"
    private static let context = "dictation.context"
    private static let focus = "dictation.focus_weak_spots"

    static func decode(_ preferences: Preferences) -> DictationSettings {
        let defaults = DictationSettings()
        var settings = defaults
        settings.modes = (preferences.enumSet(modes) as [Mode]?).map(Set.init) ?? defaults.modes
        settings.length = preferences.int(length).flatMap { DictationSettings.lengthOptions.contains($0) ? $0 : nil } ?? defaults.length
        settings.keyChoice = preferences.enumValue(keyChoice) ?? defaults.keyChoice
        settings.context = preferences.enumValue(context) ?? defaults.context
        settings.focusOnWeakSpots = preferences.bool(focus) ?? defaults.focusOnWeakSpots
        return settings.isValid ? settings : defaults
    }

    static func encode(_ settings: DictationSettings, _ preferences: inout Preferences) {
        preferences[modes] = .stringSet(Set(settings.modes.map(\.name)))
        preferences[length] = .int(settings.length)
        preferences[keyChoice] = .string(settings.keyChoice.name)
        preferences[context] = .string(settings.context.name)
        preferences[focus] = .bool(settings.focusOnWeakSpots)
    }
}

enum RhythmSettingsCodec {
    private static let vocabulary = "rhythm.vocabulary"
    private static let bars = "rhythm.bars"
    private static let tempo = "rhythm.tempo"
    private static let focus = "rhythm.focus_weak_spots"

    static func decode(_ preferences: Preferences) -> RhythmSettings {
        let defaults = RhythmSettings()
        return RhythmSettings(
            vocabulary: preferences.enumValue(vocabulary) ?? defaults.vocabulary,
            bars: preferences.int(bars).flatMap { RhythmSettings.barOptions.contains($0) ? $0 : nil } ?? defaults.bars,
            tempo: preferences.enumValue(tempo) ?? defaults.tempo,
            focusOnWeakSpots: preferences.bool(focus) ?? defaults.focusOnWeakSpots
        )
    }

    static func encode(_ settings: RhythmSettings, _ preferences: inout Preferences) {
        preferences[vocabulary] = .string(settings.vocabulary.name)
        preferences[bars] = .int(settings.bars)
        preferences[tempo] = .string(settings.tempo.name)
        preferences[focus] = .bool(settings.focusOnWeakSpots)
    }
}

enum SoundSettingsCodec {
    private static let instrument = "sound.instrument"

    static func decode(_ preferences: Preferences) -> SoundSettings {
        SoundSettings(instrument: preferences.enumValue(instrument) ?? SoundSettings().instrument)
    }

    static func encode(_ settings: SoundSettings, _ preferences: inout Preferences) {
        preferences[instrument] = .string(settings.instrument.name)
    }
}

/// How many finished sessions a day count as reaching the goal on the Home screen.
struct GoalSettings: Equatable {
    /// About ten to fifteen focused minutes.
    static let defaultDailySessions = 2
    static let dailySessionOptions = [1, 2, 3, 4, 5]

    var dailySessions = defaultDailySessions
}

enum GoalSettingsCodec {
    // A new key: goals saved as a number of answers do not carry over as sessions.
    private static let dailySessions = "goal.daily_sessions"

    static func decode(_ preferences: Preferences) -> GoalSettings {
        GoalSettings(dailySessions: preferences.int(dailySessions).flatMap { GoalSettings.dailySessionOptions.contains($0) ? $0 : nil }
            ?? GoalSettings.defaultDailySessions)
    }

    static func encode(_ settings: GoalSettings, _ preferences: inout Preferences) {
        preferences[dailySessions] = .int(settings.dailySessions)
    }
}

extension SettingsStorage {
    func store<T: Equatable>(_ decode: @escaping (Preferences) -> T, _ encode: @escaping (T, inout Preferences) -> Void) -> SettingsStore<T> {
        SettingsStore(storage: self, decode: decode, encode: encode)
    }
}
