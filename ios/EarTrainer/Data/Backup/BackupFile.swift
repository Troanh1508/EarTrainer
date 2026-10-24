import Foundation

/**
 * Everything a user can export: practice history and settings, as one JSON document. The format
 * is the Android app's, so a backup made on either platform can be imported on the other.
 *
 * Readers ignore unknown fields and fill in missing ones with defaults, so a newer app can add
 * fields without breaking older backups. A change that older readers cannot handle must raise
 * `currentFormatVersion`.
 */
struct BackupFile: Codable {
    // Marks a file as an Ear Trainer backup. It is part of the file format, not the bundle ID: it
    // stays the same so existing backups, including Android ones, still import.
    static let appId = "com.example.eartrainer"
    static let currentFormatVersion = 1

    // Required in the file, so that unrelated JSON is not mistaken for a backup.
    var app: String = BackupFile.appId
    var formatVersion: Int = BackupFile.currentFormatVersion
    var exportedAtMillis: Int64
    /// Setting values keyed by their stored name, e.g. "rhythm.bars".
    var settings: [String: SettingValue] = [:]
    var history = BackupHistory()

    init(exportedAtMillis: Int64, settings: [String: SettingValue], history: BackupHistory) {
        self.exportedAtMillis = exportedAtMillis
        self.settings = settings
        self.history = history
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        app = try container.decode(String.self, forKey: .app)
        formatVersion = try container.decode(Int.self, forKey: .formatVersion)
        exportedAtMillis = try container.decode(Int64.self, forKey: .exportedAtMillis)
        settings = try container.decodeIfPresent([String: SettingValue].self, forKey: .settings) ?? [:]
        history = try container.decodeIfPresent(BackupHistory.self, forKey: .history) ?? BackupHistory()
    }
}

/// Every stored answer, one list per exercise.
struct BackupHistory: Codable {
    var intervals: [IntervalAttemptEntity] = []
    var chords: [ChordAttemptEntity] = []
    var scaleDegrees: [ScaleDegreeAttemptEntity] = []
    var progressions: [ProgressionAttemptEntity] = []
    var dictations: [DictationAttemptEntity] = []
    var rhythms: [RhythmAttemptEntity] = []

    init(
        intervals: [IntervalAttemptEntity] = [], chords: [ChordAttemptEntity] = [], scaleDegrees: [ScaleDegreeAttemptEntity] = [],
        progressions: [ProgressionAttemptEntity] = [], dictations: [DictationAttemptEntity] = [], rhythms: [RhythmAttemptEntity] = []
    ) {
        self.intervals = intervals
        self.chords = chords
        self.scaleDegrees = scaleDegrees
        self.progressions = progressions
        self.dictations = dictations
        self.rhythms = rhythms
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        intervals = try container.decodeIfPresent([IntervalAttemptEntity].self, forKey: .intervals) ?? []
        chords = try container.decodeIfPresent([ChordAttemptEntity].self, forKey: .chords) ?? []
        scaleDegrees = try container.decodeIfPresent([ScaleDegreeAttemptEntity].self, forKey: .scaleDegrees) ?? []
        progressions = try container.decodeIfPresent([ProgressionAttemptEntity].self, forKey: .progressions) ?? []
        dictations = try container.decodeIfPresent([DictationAttemptEntity].self, forKey: .dictations) ?? []
        rhythms = try container.decodeIfPresent([RhythmAttemptEntity].self, forKey: .rhythms) ?? []
    }

    var size: Int { intervals.count + chords.count + scaleDegrees.count + progressions.count + dictations.count + rhythms.count }
}

/**
 * One setting value; exactly one field is set. A flat object rather than a tagged type, so a
 * value type added later is skipped by older readers instead of failing the whole file.
 */
struct SettingValue: Codable, Hashable {
    var string: String?
    var int: Int?
    var boolean: Bool?
    var stringSet: Set<String>?

    init(_ value: PreferenceValue) {
        switch value {
        case .string(let text): string = text
        case .int(let number): int = number
        case .bool(let flag): boolean = flag
        case .stringSet(let set): stringSet = set
        }
    }

    var preferenceValue: PreferenceValue? {
        if let string { return .string(string) }
        if let int { return .int(int) }
        if let boolean { return .bool(boolean) }
        if let stringSet { return .stringSet(stringSet) }
        return nil
    }
}

/// Why a backup could not be read.
enum BackupError: Error {
    /// Not JSON, or JSON from something other than this app.
    case notABackup
    /// Written by a newer version of the app in a format this version cannot read.
    case newerVersion
    /// Far larger than any real backup; refused rather than read into memory.
    case tooLarge
}

enum BackupCodec {
    /// Far beyond a lifetime of practice (roughly 300 bytes per answer).
    static let maxBackupBytes = 64 * 1024 * 1024

    static func encode(_ file: BackupFile) throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        return try encoder.encode(file)
    }

    /// Parses and checks a backup; throws `BackupError` if it is not one this app can read.
    static func decode(_ data: Data) throws -> BackupFile {
        if data.count > maxBackupBytes { throw BackupError.tooLarge }
        let file: BackupFile
        do {
            file = try JSONDecoder().decode(BackupFile.self, from: data)
        } catch {
            throw BackupError.notABackup
        }
        if file.app != BackupFile.appId { throw BackupError.notABackup }
        if file.formatVersion > BackupFile.currentFormatVersion { throw BackupError.newerVersion }
        return file
    }
}
