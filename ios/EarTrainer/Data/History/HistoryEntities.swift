import Foundation

/**
 * One stored answer. Enum values are stored by name so the database stays readable and
 * reorder-safe; tables and columns match the Android app's Room schema.
 *
 * The entities double as the backup file format (see `BackupFile`), with the same field names as
 * the Android app's: renaming or removing a property breaks importing older backups.
 */
protocol AttemptEntity: Codable, Hashable {
    static var table: String { get }
    /// Columns after the common ones, in the order of `specificValues`.
    static var specificColumns: [(name: String, type: String)] { get }

    var sessionId: String { get }
    var answeredAtMillis: Int64 { get }
    var isCorrect: Bool { get }
    var playCount: Int { get }
    var responseMillis: Int64 { get }
    var specificValues: [SQLValue] { get }

    init(row: SQLRow)

    /// Whether the app can interpret this row; rows from a backup that fail are skipped.
    var isValid: Bool { get }
}

extension AttemptEntity {
    static var columns: [String] {
        ["session_id", "answered_at"] + specificColumns.map(\.name) + ["is_correct", "play_count", "response_ms"]
    }

    var values: [SQLValue] {
        [SQLValue(sessionId), SQLValue(answeredAtMillis)] + specificValues + [SQLValue(isCorrect), SQLValue(playCount), SQLValue(responseMillis)]
    }

    static var createSQL: String {
        let specific = specificColumns.map { "`\($0.name)` \($0.type) NOT NULL" }.joined(separator: ", ")
        return """
        CREATE TABLE IF NOT EXISTS `\(table)` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `session_id` TEXT NOT NULL, \
        `answered_at` INTEGER NOT NULL, \(specific), `is_correct` INTEGER NOT NULL, `play_count` INTEGER NOT NULL, \
        `response_ms` INTEGER NOT NULL);
        CREATE INDEX IF NOT EXISTS `index_\(table)_session_id` ON `\(table)` (`session_id`);
        """
    }

    static var insertSQL: String {
        let placeholders = columns.indices.map { "?\($0 + 1)" }.joined(separator: ", ")
        return "INSERT INTO `\(table)` (\(columns.map { "`\($0)`" }.joined(separator: ", "))) VALUES (\(placeholders))"
    }

    /// An answer is identified by its session and the moment it was given, which no two answers share.
    var identity: String { "\(sessionId)|\(answeredAtMillis)" }

    var hasValidAnswerFields: Bool {
        !sessionId.trimmingCharacters(in: .whitespaces).isEmpty && answeredAtMillis > 0 && playCount >= 0 && responseMillis >= 0
    }
}

private let midiRange = 0...127

private extension String {
    func isName<E: StoredEnum>(of _: E.Type) -> Bool { E.named(self) != nil }

    func isList<E: StoredEnum>(of _: E.Type) -> Bool {
        !isEmpty && split(separator: ",", omittingEmptySubsequences: false).allSatisfy { E.named(String($0)) != nil }
    }

    var isStepList: Bool {
        !isEmpty && split(separator: ",", omittingEmptySubsequences: false).allSatisfy { Int($0).map((1...7).contains) ?? false }
    }

    var isRhythm: Bool { decodedRhythm() != nil }
}

struct IntervalAttemptEntity: AttemptEntity {
    static let table = "interval_attempts"
    static let specificColumns: [(name: String, type: String)] = [("interval", "TEXT"), ("style", "TEXT"), ("lower_midi", "INTEGER"), ("answer", "TEXT")]

    var sessionId: String
    var answeredAtMillis: Int64
    var interval: String
    var style: String
    var lowerMidi: Int
    var answer: String
    var isCorrect: Bool
    var playCount: Int
    var responseMillis: Int64

    init(sessionId: String, answeredAtMillis: Int64, interval: String, style: String, lowerMidi: Int, answer: String, isCorrect: Bool, playCount: Int, responseMillis: Int64) {
        self.sessionId = sessionId
        self.answeredAtMillis = answeredAtMillis
        self.interval = interval
        self.style = style
        self.lowerMidi = lowerMidi
        self.answer = answer
        self.isCorrect = isCorrect
        self.playCount = playCount
        self.responseMillis = responseMillis
    }

    init(row: SQLRow) {
        self.init(
            sessionId: row.string("session_id"), answeredAtMillis: row.int64("answered_at"), interval: row.string("interval"),
            style: row.string("style"), lowerMidi: row.int("lower_midi"), answer: row.string("answer"),
            isCorrect: row.bool("is_correct"), playCount: row.int("play_count"), responseMillis: row.int64("response_ms")
        )
    }

    var specificValues: [SQLValue] { [SQLValue(interval), SQLValue(style), SQLValue(lowerMidi), SQLValue(answer)] }

    var isValid: Bool {
        hasValidAnswerFields && interval.isName(of: Interval.self) && style.isName(of: IntervalStyle.self) &&
            answer.isName(of: Interval.self) && midiRange.contains(lowerMidi)
    }
}

/// [answer] holds a quality or inversion name, depending on [target].
struct ChordAttemptEntity: AttemptEntity {
    static let table = "chord_attempts"
    static let specificColumns: [(name: String, type: String)] = [
        ("quality", "TEXT"), ("inversion", "TEXT"), ("style", "TEXT"), ("target", "TEXT"), ("bass_midi", "INTEGER"), ("answer", "TEXT"),
    ]

    var sessionId: String
    var answeredAtMillis: Int64
    var quality: String
    var inversion: String
    var style: String
    var target: String
    var bassMidi: Int
    var answer: String
    var isCorrect: Bool
    var playCount: Int
    var responseMillis: Int64

    init(sessionId: String, answeredAtMillis: Int64, quality: String, inversion: String, style: String, target: String, bassMidi: Int, answer: String, isCorrect: Bool, playCount: Int, responseMillis: Int64) {
        self.sessionId = sessionId
        self.answeredAtMillis = answeredAtMillis
        self.quality = quality
        self.inversion = inversion
        self.style = style
        self.target = target
        self.bassMidi = bassMidi
        self.answer = answer
        self.isCorrect = isCorrect
        self.playCount = playCount
        self.responseMillis = responseMillis
    }

    init(row: SQLRow) {
        self.init(
            sessionId: row.string("session_id"), answeredAtMillis: row.int64("answered_at"), quality: row.string("quality"),
            inversion: row.string("inversion"), style: row.string("style"), target: row.string("target"), bassMidi: row.int("bass_midi"),
            answer: row.string("answer"), isCorrect: row.bool("is_correct"), playCount: row.int("play_count"), responseMillis: row.int64("response_ms")
        )
    }

    var specificValues: [SQLValue] {
        [SQLValue(quality), SQLValue(inversion), SQLValue(style), SQLValue(target), SQLValue(bassMidi), SQLValue(answer)]
    }

    var isValid: Bool {
        guard hasValidAnswerFields, quality.isName(of: ChordQuality.self), inversion.isName(of: ChordInversion.self),
              style.isName(of: ChordStyle.self), midiRange.contains(bassMidi) else { return false }
        switch ChordTarget.named(target) {
        case .quality: return answer.isName(of: ChordQuality.self)
        case .inversion: return answer.isName(of: ChordInversion.self)
        case nil: return false
        }
    }
}

struct ScaleDegreeAttemptEntity: AttemptEntity {
    static let table = "scale_degree_attempts"
    static let specificColumns: [(name: String, type: String)] = [("tonic_midi", "INTEGER"), ("mode", "TEXT"), ("degree", "TEXT"), ("context", "TEXT"), ("answer", "TEXT")]

    var sessionId: String
    var answeredAtMillis: Int64
    var tonicMidi: Int
    var mode: String
    var degree: String
    var context: String
    var answer: String
    var isCorrect: Bool
    var playCount: Int
    var responseMillis: Int64

    init(sessionId: String, answeredAtMillis: Int64, tonicMidi: Int, mode: String, degree: String, context: String, answer: String, isCorrect: Bool, playCount: Int, responseMillis: Int64) {
        self.sessionId = sessionId
        self.answeredAtMillis = answeredAtMillis
        self.tonicMidi = tonicMidi
        self.mode = mode
        self.degree = degree
        self.context = context
        self.answer = answer
        self.isCorrect = isCorrect
        self.playCount = playCount
        self.responseMillis = responseMillis
    }

    init(row: SQLRow) {
        self.init(
            sessionId: row.string("session_id"), answeredAtMillis: row.int64("answered_at"), tonicMidi: row.int("tonic_midi"),
            mode: row.string("mode"), degree: row.string("degree"), context: row.string("context"), answer: row.string("answer"),
            isCorrect: row.bool("is_correct"), playCount: row.int("play_count"), responseMillis: row.int64("response_ms")
        )
    }

    var specificValues: [SQLValue] { [SQLValue(tonicMidi), SQLValue(mode), SQLValue(degree), SQLValue(context), SQLValue(answer)] }

    var isValid: Bool {
        hasValidAnswerFields && midiRange.contains(tonicMidi) && mode.isName(of: Mode.self) && degree.isName(of: ScaleDegree.self) &&
            context.isName(of: KeyContext.self) && answer.isName(of: ScaleDegree.self)
    }
}

/// Progressions are stored as comma-separated scale steps, e.g. "1,6,4,5".
struct ProgressionAttemptEntity: AttemptEntity {
    static let table = "progression_attempts"
    static let specificColumns: [(name: String, type: String)] = [("tonic_midi", "INTEGER"), ("mode", "TEXT"), ("progression", "TEXT"), ("answer", "TEXT")]

    var sessionId: String
    var answeredAtMillis: Int64
    var tonicMidi: Int
    var mode: String
    var progression: String
    var answer: String
    var isCorrect: Bool
    var playCount: Int
    var responseMillis: Int64

    init(sessionId: String, answeredAtMillis: Int64, tonicMidi: Int, mode: String, progression: String, answer: String, isCorrect: Bool, playCount: Int, responseMillis: Int64) {
        self.sessionId = sessionId
        self.answeredAtMillis = answeredAtMillis
        self.tonicMidi = tonicMidi
        self.mode = mode
        self.progression = progression
        self.answer = answer
        self.isCorrect = isCorrect
        self.playCount = playCount
        self.responseMillis = responseMillis
    }

    init(row: SQLRow) {
        self.init(
            sessionId: row.string("session_id"), answeredAtMillis: row.int64("answered_at"), tonicMidi: row.int("tonic_midi"),
            mode: row.string("mode"), progression: row.string("progression"), answer: row.string("answer"),
            isCorrect: row.bool("is_correct"), playCount: row.int("play_count"), responseMillis: row.int64("response_ms")
        )
    }

    var specificValues: [SQLValue] { [SQLValue(tonicMidi), SQLValue(mode), SQLValue(progression), SQLValue(answer)] }

    var isValid: Bool {
        hasValidAnswerFields && midiRange.contains(tonicMidi) && mode.isName(of: Mode.self) && progression.isStepList && answer.isStepList
    }
}

/// Melodies are stored as comma-separated degree names.
struct DictationAttemptEntity: AttemptEntity {
    static let table = "dictation_attempts"
    static let specificColumns: [(name: String, type: String)] = [("tonic_midi", "INTEGER"), ("mode", "TEXT"), ("melody", "TEXT"), ("answer", "TEXT")]

    var sessionId: String
    var answeredAtMillis: Int64
    var tonicMidi: Int
    var mode: String
    var melody: String
    var answer: String
    var isCorrect: Bool
    var playCount: Int
    var responseMillis: Int64

    init(sessionId: String, answeredAtMillis: Int64, tonicMidi: Int, mode: String, melody: String, answer: String, isCorrect: Bool, playCount: Int, responseMillis: Int64) {
        self.sessionId = sessionId
        self.answeredAtMillis = answeredAtMillis
        self.tonicMidi = tonicMidi
        self.mode = mode
        self.melody = melody
        self.answer = answer
        self.isCorrect = isCorrect
        self.playCount = playCount
        self.responseMillis = responseMillis
    }

    init(row: SQLRow) {
        self.init(
            sessionId: row.string("session_id"), answeredAtMillis: row.int64("answered_at"), tonicMidi: row.int("tonic_midi"),
            mode: row.string("mode"), melody: row.string("melody"), answer: row.string("answer"),
            isCorrect: row.bool("is_correct"), playCount: row.int("play_count"), responseMillis: row.int64("response_ms")
        )
    }

    var specificValues: [SQLValue] { [SQLValue(tonicMidi), SQLValue(mode), SQLValue(melody), SQLValue(answer)] }

    var isValid: Bool {
        hasValidAnswerFields && midiRange.contains(tonicMidi) && mode.isName(of: Mode.self) &&
            melody.isList(of: ScaleDegree.self) && answer.isList(of: ScaleDegree.self)
    }
}

/// Rhythms are stored as comma-separated cell names.
struct RhythmAttemptEntity: AttemptEntity {
    static let table = "rhythm_attempts"
    static let specificColumns: [(name: String, type: String)] = [("tempo", "TEXT"), ("rhythm", "TEXT"), ("answer", "TEXT")]

    var sessionId: String
    var answeredAtMillis: Int64
    var tempo: String
    var rhythm: String
    var answer: String
    var isCorrect: Bool
    var playCount: Int
    var responseMillis: Int64

    init(sessionId: String, answeredAtMillis: Int64, tempo: String, rhythm: String, answer: String, isCorrect: Bool, playCount: Int, responseMillis: Int64) {
        self.sessionId = sessionId
        self.answeredAtMillis = answeredAtMillis
        self.tempo = tempo
        self.rhythm = rhythm
        self.answer = answer
        self.isCorrect = isCorrect
        self.playCount = playCount
        self.responseMillis = responseMillis
    }

    init(row: SQLRow) {
        self.init(
            sessionId: row.string("session_id"), answeredAtMillis: row.int64("answered_at"), tempo: row.string("tempo"),
            rhythm: row.string("rhythm"), answer: row.string("answer"), isCorrect: row.bool("is_correct"),
            playCount: row.int("play_count"), responseMillis: row.int64("response_ms")
        )
    }

    var specificValues: [SQLValue] { [SQLValue(tempo), SQLValue(rhythm), SQLValue(answer)] }

    var isValid: Bool { hasValidAnswerFields && tempo.isName(of: RhythmTempo.self) && rhythm.isRhythm && answer.isRhythm }
}

// MARK: - Conversions from answered questions

extension IntervalAttemptEntity {
    init(_ attempt: IntervalAttempt) {
        let info = attempt.info
        self.init(
            sessionId: info.sessionId, answeredAtMillis: info.answeredAtMillis, interval: attempt.interval.name, style: attempt.style.name,
            lowerMidi: attempt.lowerPitch.midi, answer: info.answer.name, isCorrect: info.isCorrect, playCount: info.playCount,
            responseMillis: info.responseMillis
        )
    }
}

extension ChordAttemptEntity {
    init(_ attempt: ChordAttempt) {
        let info = attempt.info
        self.init(
            sessionId: info.sessionId, answeredAtMillis: info.answeredAtMillis, quality: attempt.quality.name, inversion: attempt.inversion.name,
            style: attempt.style.name, target: attempt.target.name, bassMidi: attempt.bass.midi, answer: info.answer.name,
            isCorrect: info.isCorrect, playCount: info.playCount, responseMillis: info.responseMillis
        )
    }
}

extension ScaleDegreeAttemptEntity {
    init(_ attempt: ScaleDegreeAttempt) {
        let info = attempt.info
        self.init(
            sessionId: info.sessionId, answeredAtMillis: info.answeredAtMillis, tonicMidi: attempt.key.tonic.midi, mode: attempt.key.mode.name,
            degree: attempt.degree.name, context: attempt.context.name, answer: info.answer.name, isCorrect: info.isCorrect,
            playCount: info.playCount, responseMillis: info.responseMillis
        )
    }
}

extension ProgressionAttemptEntity {
    init(_ attempt: ProgressionAttempt) {
        let info = attempt.info
        self.init(
            sessionId: info.sessionId, answeredAtMillis: info.answeredAtMillis, tonicMidi: attempt.key.tonic.midi, mode: attempt.key.mode.name,
            progression: attempt.progression.storedSteps, answer: info.answer.storedSteps, isCorrect: info.isCorrect,
            playCount: info.playCount, responseMillis: info.responseMillis
        )
    }
}

extension DictationAttemptEntity {
    init(_ attempt: DictationAttempt) {
        let info = attempt.info
        self.init(
            sessionId: info.sessionId, answeredAtMillis: info.answeredAtMillis, tonicMidi: attempt.key.tonic.midi, mode: attempt.key.mode.name,
            melody: attempt.melody.storedNames, answer: info.answer.storedNames, isCorrect: info.isCorrect,
            playCount: info.playCount, responseMillis: info.responseMillis
        )
    }
}

extension RhythmAttemptEntity {
    init(_ attempt: RhythmAttempt) {
        let info = attempt.info
        self.init(
            sessionId: info.sessionId, answeredAtMillis: info.answeredAtMillis, tempo: attempt.tempo.name, rhythm: attempt.rhythm.storedNames,
            answer: info.answer.storedNames, isCorrect: info.isCorrect, playCount: info.playCount, responseMillis: info.responseMillis
        )
    }
}
