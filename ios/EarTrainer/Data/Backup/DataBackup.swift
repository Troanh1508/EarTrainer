import Foundation

/**
 * Converts stored settings to and from backup values.
 *
 * Every value passes through the settings codecs' own decode and encode, so a backup always
 * holds a complete, valid set of settings, and importing can never store a value the app would
 * misread: unknown keys and values of the wrong type are dropped, and invalid choices fall back
 * to their defaults.
 */
enum SettingsBackup {

    /// Copies one group of settings, replacing anything missing or invalid with defaults.
    private static let sections: [(Preferences, inout Preferences) -> Void] = [
        { from, to in IntervalSettingsCodec.encode(IntervalSettingsCodec.decode(from), &to) },
        { from, to in ChordSettingsCodec.encode(ChordSettingsCodec.decode(from), &to) },
        { from, to in ScaleDegreeSettingsCodec.encode(ScaleDegreeSettingsCodec.decode(from), &to) },
        { from, to in ProgressionSettingsCodec.encode(ProgressionSettingsCodec.decode(from), &to) },
        { from, to in DictationSettingsCodec.encode(DictationSettingsCodec.decode(from), &to) },
        { from, to in RhythmSettingsCodec.encode(RhythmSettingsCodec.decode(from), &to) },
        { from, to in SoundSettingsCodec.encode(SoundSettingsCodec.decode(from), &to) },
        { from, to in GoalSettingsCodec.encode(GoalSettingsCodec.decode(from), &to) },
    ]

    static func export(_ preferences: Preferences) -> [String: SettingValue] {
        normalized(preferences).mapValues(SettingValue.init)
    }

    /// The settings to store for [values] from a backup.
    static func importing(_ values: [String: SettingValue]) -> Preferences {
        // The defaults name every known key, with a value of the type the codecs expect.
        let expected = normalized([:])
        var accepted: Preferences = [:]
        for (name, value) in values {
            switch expected[name] {
            case .string: if let string = value.string { accepted[name] = .string(string) }
            case .int: if let int = value.int { accepted[name] = .int(int) }
            case .bool: if let boolean = value.boolean { accepted[name] = .bool(boolean) }
            case .stringSet: if let set = value.stringSet { accepted[name] = .stringSet(set) }
            case nil: break
            }
        }
        return normalized(accepted)
    }

    private static func normalized(_ preferences: Preferences) -> Preferences {
        var result: Preferences = [:]
        for copy in sections { copy(preferences, &result) }
        return result
    }
}

/// What importing a backup changed.
struct ImportResult {
    let added: Int
    let alreadyPresent: Int
    let unreadable: Int
    /// Whether the backup's settings replaced the current ones.
    let settingsRestored: Bool
}

/// Saves practice history and settings to a file, and brings them back.
@MainActor
final class DataBackup {
    private let history: PracticeHistory
    private let settings: SettingsStorage
    private let clock: () -> Int64

    init(history: PracticeHistory, settings: SettingsStorage, clock: @escaping () -> Int64 = currentTimeMillis) {
        self.history = history
        self.settings = settings
        self.clock = clock
    }

    /// Everything as a backup file, and the number of answers in it.
    func export() async throws -> (data: Data, answers: Int) {
        let answers = try await history.allAnswers()
        let file = BackupFile(exportedAtMillis: clock(), settings: SettingsBackup.export(settings.preferences), history: answers)
        let data = try await Task.detached { try BackupCodec.encode(file) }.value
        return (data, answers.size)
    }

    /**
     * Reads a backup. History is merged: answers already present are not added again. Settings
     * are replaced by the backup's.
     *
     * Throws `BackupError` if the file is not a backup this version can read; nothing changes then.
     */
    func importBackup(_ data: Data) async throws -> ImportResult {
        // Parsed and checked in full before anything is written, so a bad file changes nothing.
        let file = try await Task.detached { try BackupCodec.decode(data) }.value
        let counts = try await history.merge(file.history)
        // A backup without settings (none were saved, or the file was edited) leaves them alone.
        let restoreSettings = !file.settings.isEmpty
        if restoreSettings {
            let imported = SettingsBackup.importing(file.settings)
            settings.edit { $0 = imported }
        }
        return ImportResult(added: counts.added, alreadyPresent: counts.alreadyPresent, unreadable: counts.unreadable, settingsRestored: restoreSettings)
    }
}

/// Milliseconds since 1970, the time unit stored in history.
func currentTimeMillis() -> Int64 { Int64((Date().timeIntervalSince1970 * 1000).rounded()) }
