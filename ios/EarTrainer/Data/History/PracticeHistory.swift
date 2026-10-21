import Foundation
import os

extension Notification.Name {
    /// Posted on the main queue after an answer is saved or a backup is imported.
    static let practiceHistoryDidChange = Notification.Name("EarTrainer.practiceHistoryDidChange")
}

/**
 * One stored answer with its session, for scoring item by item: [asked] and [chosen] hold the
 * progression, melody or rhythm as stored; [mode] is nil for rhythms.
 */
private struct SessionAnswerRow {
    let sessionId: String
    let answeredAtMillis: Int64
    let mode: String?
    let asked: String
    let chosen: String
    let isCorrect: Bool

    init(_ row: SQLRow) {
        sessionId = row.string("sessionId")
        answeredAtMillis = row.int64("answeredAtMillis")
        mode = row.optionalString("mode")
        asked = row.string("asked")
        chosen = row.string("chosen")
        isCorrect = row.bool("isCorrect")
    }
}

/**
 * Local record of every answered question, used for progress views and weak-spot practice.
 *
 * Everything runs on one serial queue, so a read always sees the writes made before it. Writes
 * are fire-and-forget: losing one history row must never interrupt practice.
 */
final class PracticeHistory: @unchecked Sendable {
    static let schemaVersion = 6

    private let database: SQLiteDatabase
    private let queue = DispatchQueue(label: "EarTrainer.history", qos: .userInitiated)
    private let log = Logger(subsystem: "EarTrainer", category: "PracticeHistory")

    private static let entityTypes: [any AttemptEntity.Type] = [
        IntervalAttemptEntity.self, ChordAttemptEntity.self, ScaleDegreeAttemptEntity.self,
        ProgressionAttemptEntity.self, DictationAttemptEntity.self, RhythmAttemptEntity.self,
    ]

    init(database: SQLiteDatabase) throws {
        self.database = database
        try queue.sync {
            for type in Self.entityTypes { try database.execute(type.createSQL) }
            // The same lookup indices as the Room schema.
            try database.execute("""
            CREATE INDEX IF NOT EXISTS `index_interval_attempts_interval` ON `interval_attempts` (`interval`);
            CREATE INDEX IF NOT EXISTS `index_chord_attempts_target` ON `chord_attempts` (`target`);
            CREATE INDEX IF NOT EXISTS `index_scale_degree_attempts_degree` ON `scale_degree_attempts` (`degree`);
            CREATE INDEX IF NOT EXISTS `index_progression_attempts_mode` ON `progression_attempts` (`mode`);
            CREATE INDEX IF NOT EXISTS `index_dictation_attempts_mode` ON `dictation_attempts` (`mode`);
            """)
            database.userVersion = Self.schemaVersion
        }
    }

    /// The history stored in the app's Application Support folder, which device backups include.
    static func openDefault() throws -> PracticeHistory {
        let folder = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
        return try PracticeHistory(database: SQLiteDatabase(path: folder.appendingPathComponent("ear_trainer.db").path))
    }

    // MARK: - Writing

    func record(_ attempt: IntervalAttempt) { write(IntervalAttemptEntity(attempt)) }
    func record(_ attempt: ChordAttempt) { write(ChordAttemptEntity(attempt)) }
    func record(_ attempt: ScaleDegreeAttempt) { write(ScaleDegreeAttemptEntity(attempt)) }
    func record(_ attempt: ProgressionAttempt) { write(ProgressionAttemptEntity(attempt)) }
    func record(_ attempt: DictationAttempt) { write(DictationAttemptEntity(attempt)) }
    func record(_ attempt: RhythmAttempt) { write(RhythmAttemptEntity(attempt)) }

    private func write<E: AttemptEntity>(_ entity: E) {
        queue.async {
            do {
                try self.database.run(E.insertSQL, entity.values)
            } catch {
                self.log.warning("Could not save practice attempt: \(String(describing: error))")
            }
            Self.announceChange()
        }
    }

    private static func announceChange() {
        DispatchQueue.main.async { NotificationCenter.default.post(name: .practiceHistoryDidChange, object: nil) }
    }

    /// Runs [body] on the history queue; a failing query logs and returns [fallback].
    private func read<T>(_ fallback: T, _ body: @escaping (SQLiteDatabase) throws -> T) async -> T {
        await withCheckedContinuation { continuation in
            queue.async {
                do {
                    continuation.resume(returning: try body(self.database))
                } catch {
                    self.log.error("History query failed: \(String(describing: error))")
                    continuation.resume(returning: fallback)
                }
            }
        }
    }

    // MARK: - Accuracy per item

    /// Accuracy for every interval, including unpracticed ones; [style] nil means all styles.
    func intervalAccuracy(style: IntervalStyle?) async -> [AnswerAccuracy<Interval>] {
        await read([]) { db in
            let rows = try db.query("""
            SELECT interval AS item, COUNT(*) AS attempts, SUM(is_correct) AS correct FROM interval_attempts
            WHERE ?1 IS NULL OR style = ?1 GROUP BY interval
            """, [SQLValue(style?.name)])
            return completeAccuracy(Self.totals(rows), over: Interval.allCases)
        }
    }

    /**
     * Accuracy for every possible answer to chord questions with [target]: each quality, or
     * each inversion. Unpracticed answers are included with zero attempts.
     */
    func chordAccuracy(target: ChordTarget) async -> [AnswerAccuracy<ChordAnswer>] {
        await read([]) { db in
            switch target {
            case .quality:
                let rows = try db.query("""
                SELECT quality AS item, COUNT(*) AS attempts, SUM(is_correct) AS correct FROM chord_attempts
                WHERE target = 'QUALITY' GROUP BY quality
                """)
                return completeAccuracy(Self.totals(rows), over: ChordQuality.allCases)
                    .map { AnswerAccuracy(item: .quality($0.item), attempts: $0.attempts, correct: $0.correct) }
            case .inversion:
                let rows = try db.query("""
                SELECT inversion AS item, COUNT(*) AS attempts, SUM(is_correct) AS correct FROM chord_attempts
                WHERE target = 'INVERSION' GROUP BY inversion
                """)
                return completeAccuracy(Self.totals(rows), over: ChordInversion.allCases)
                    .map { AnswerAccuracy(item: .inversion($0.item), attempts: $0.attempts, correct: $0.correct) }
            }
        }
    }

    /// Accuracy for every scale degree, including unpracticed ones; [mode] nil means all modes.
    func scaleDegreeAccuracy(mode: Mode?) async -> [AnswerAccuracy<ScaleDegree>] {
        await read([]) { db in
            let rows = try db.query("""
            SELECT degree AS item, COUNT(*) AS attempts, SUM(is_correct) AS correct FROM scale_degree_attempts
            WHERE ?1 IS NULL OR mode = ?1 GROUP BY degree
            """, [SQLValue(mode?.name)])
            return completeAccuracy(Self.totals(rows), over: ScaleDegree.allCases)
        }
    }

    /**
     * How often each chord of [mode] was identified correctly inside progressions, counted per
     * position. The opening tonic is given rather than tested, so it is not counted.
     */
    func progressionAccuracy(mode: Mode) async -> [AnswerAccuracy<DiatonicChord>] {
        await read([]) { db in
            let rows = try db.query("SELECT progression, answer FROM progression_attempts WHERE mode = ?1", [SQLValue(mode.name)])
            return progressionChordAccuracy(mode, rows.map { ($0.string("progression").decodedSteps(), $0.string("answer").decodedSteps()) })
        }
    }

    /**
     * How often each degree of [mode] was written down correctly in dictations, counted per note.
     * The opening tonic is given rather than tested, so it is not counted.
     */
    func dictationAccuracy(mode: Mode) async -> [AnswerAccuracy<ScaleDegree>] {
        await read([]) { db in
            let rows = try db.query("SELECT melody, answer FROM dictation_attempts WHERE mode = ?1", [SQLValue(mode.name)])
            let results = rows.flatMap { dictationNoteResults($0.string("melody").decodedDegrees(), $0.string("answer").decodedDegrees()) }
            return itemAccuracy(of: results, over: mode.diatonicDegrees)
        }
    }

    /// How often each rhythm cell was heard correctly, counted per cell of every rhythm asked.
    func rhythmAccuracy() async -> [AnswerAccuracy<RhythmCell>] {
        await read([]) { db in
            let rows = try db.query("SELECT rhythm, answer FROM rhythm_attempts")
            let results = rows.flatMap { storedRhythmCellResults(rhythm: $0.string("rhythm"), answer: $0.string("answer")) }
            return itemAccuracy(of: results, over: RhythmCell.allCases)
        }
    }

    // MARK: - Confusions: what each item was answered as, counted like the matching accuracy

    func intervalConfusions(style: IntervalStyle?) async -> [Confusion<Interval>] {
        await read([]) { db in
            let rows = try db.query("""
            SELECT interval AS asked, answer AS answered, COUNT(*) AS count FROM interval_attempts
            WHERE ?1 IS NULL OR style = ?1 GROUP BY interval, answer
            """, [SQLValue(style?.name)])
            return Self.confusions(rows, Interval.self)
        }
    }

    func chordConfusions(target: ChordTarget) async -> [Confusion<ChordAnswer>] {
        await read([]) { db in
            let rows = try db.query("""
            SELECT CASE target WHEN 'QUALITY' THEN quality ELSE inversion END AS asked, answer AS answered, COUNT(*) AS count
            FROM chord_attempts WHERE target = ?1 GROUP BY asked, answered
            """, [SQLValue(target.name)])
            switch target {
            case .quality: return Self.confusions(rows, ChordQuality.self).map { $0.map(ChordAnswer.quality) }
            case .inversion: return Self.confusions(rows, ChordInversion.self).map { $0.map(ChordAnswer.inversion) }
            }
        }
    }

    func scaleDegreeConfusions(mode: Mode?) async -> [Confusion<ScaleDegree>] {
        await read([]) { db in
            let rows = try db.query("""
            SELECT degree AS asked, answer AS answered, COUNT(*) AS count FROM scale_degree_attempts
            WHERE ?1 IS NULL OR mode = ?1 GROUP BY degree, answer
            """, [SQLValue(mode?.name)])
            return Self.confusions(rows, ScaleDegree.self)
        }
    }

    func progressionConfusions(mode: Mode) async -> [Confusion<DiatonicChord>] {
        await read([]) { db in
            let rows = try db.query("SELECT progression, answer FROM progression_attempts WHERE mode = ?1", [SQLValue(mode.name)])
            return countConfusions(rows.flatMap { row in
                progressionChordPairs(mode, asked: row.string("progression").decodedSteps(), chosen: row.string("answer").decodedSteps())
                    .map { ($0.asked, $0.chosen) }
            })
        }
    }

    func dictationConfusions(mode: Mode) async -> [Confusion<ScaleDegree>] {
        await read([]) { db in
            let rows = try db.query("SELECT melody, answer FROM dictation_attempts WHERE mode = ?1", [SQLValue(mode.name)])
            return countConfusions(rows.flatMap { row in
                dictationNotePairs(row.string("melody").decodedDegrees(), row.string("answer").decodedDegrees()).map { ($0.played, $0.written) }
            })
        }
    }

    func rhythmConfusions() async -> [Confusion<RhythmCell>] {
        await read([]) { db in
            let rows = try db.query("SELECT rhythm, answer FROM rhythm_attempts")
            return countConfusions(rows.flatMap { row -> [(RhythmCell, RhythmCell?)] in
                guard let played = row.string("rhythm").decodedRhythm(), let chosen = row.string("answer").decodedRhythm() else { return [] }
                return rhythmCellPairs(played, chosen).map { ($0.played, $0.chosen) }
            })
        }
    }

    // MARK: - Days and sessions

    /// Every exercise's answers, for the queries that look across exercises.
    private static let allAnswers = entityTypes.map { "SELECT session_id, answered_at, is_correct FROM \($0.table)" }.joined(separator: " UNION ALL ")

    /**
     * Answers given on each local calendar day that had any, oldest first. A day is the local
     * calendar date the answer was given on, as days since 1970-01-01.
     */
    func answersPerDay() async -> [DayCount] {
        await read([]) { db in
            try db.query("""
            SELECT CAST(strftime('%s', answered_at / 1000, 'unixepoch', 'localtime', 'start of day') AS INTEGER) / 86400 AS day,
                   COUNT(*) AS answers
            FROM (\(Self.allAnswers)) GROUP BY day ORDER BY day
            """).map { DayCount(day: $0.int64("day"), answers: $0.int("answers")) }
        }
    }

    /**
     * Finished sessions (every question of a `sessionLength`-question session answered) on each
     * local calendar day that had any, oldest first, counted on the day of the last answer.
     * Shorter runs, like retries of missed questions and comparison drill rounds, are not counted.
     */
    func finishedSessionsPerDay() async -> [DaySessions] {
        await read([]) { db in
            try db.query("""
            SELECT CAST(strftime('%s', finished_at / 1000, 'unixepoch', 'localtime', 'start of day') AS INTEGER) / 86400 AS day,
                   COUNT(*) AS sessions
            FROM (
                SELECT session_id, MAX(answered_at) AS finished_at FROM (\(Self.allAnswers))
                GROUP BY session_id HAVING COUNT(*) >= ?1
            )
            GROUP BY day ORDER BY day
            """, [SQLValue(sessionLength)]).map { DaySessions(day: $0.int64("day"), sessions: $0.int("sessions")) }
        }
    }

    /**
     * Most recent sessions first, of every exercise or only [exercise]. Exercises scored item by
     * item have their items counted from their answers.
     */
    func recentSessions(limit: Int, exercise: ExerciseKind? = nil) async -> [SessionSummary] {
        await read([]) { db in
            let union = Self.entityTypes.zip(ExerciseKind.allCases).map { type, kind in
                "SELECT session_id, answered_at, is_correct, '\(kind.name)' AS exercise FROM \(type.table)"
            }.joined(separator: " UNION ALL ")
            let sessions = try db.query("""
            SELECT session_id AS sessionId, exercise, MIN(answered_at) AS startedAtMillis, COUNT(*) AS attempts, SUM(is_correct) AS correct
            FROM (\(union))
            WHERE ?2 IS NULL OR exercise = ?2
            GROUP BY session_id ORDER BY startedAtMillis DESC LIMIT ?1
            """, [SQLValue(limit), SQLValue(exercise?.name)]).compactMap { row -> SessionSummary? in
                guard let kind = ExerciseKind.named(row.string("exercise")) else { return nil }
                return SessionSummary(
                    sessionId: row.string("sessionId"), exercise: kind, startedAtMillis: row.int64("startedAtMillis"),
                    attempts: row.int("attempts"), correct: row.int("correct")
                )
            }
            // Exercises scored item by item need their answers to count the items.
            var itemized: [String: SessionSummary] = [:]
            for (kind, table, column, itemsOf) in Self.itemizedExercises {
                let ids = sessions.filter { $0.exercise == kind }.map(\.sessionId)
                guard !ids.isEmpty else { continue }
                let placeholders = ids.indices.map { "?\($0 + 1)" }.joined(separator: ", ")
                let modeColumn = kind == .rhythm ? "NULL" : "mode"
                let answers = try db.query("""
                SELECT session_id AS sessionId, answered_at AS answeredAtMillis, \(modeColumn) AS mode, \(column) AS asked, answer AS chosen,
                       is_correct AS isCorrect
                FROM \(table) WHERE session_id IN (\(placeholders))
                """, ids.map { SQLValue($0) }).map(SessionAnswerRow.init)
                for session in Self.sessions(answers, kind, itemsOf) { itemized[session.sessionId] = session }
            }
            return sessions.map { session in
                guard let counted = itemized[session.sessionId] else { return session }
                var copy = session
                copy.items = counted.items
                copy.itemsCorrect = counted.itemsCorrect
                return copy
            }
        }
    }

    /*
     * The latest [limit] sessions of one exercise that include answers matching the given
     * filter, newest first, counting only those answers and scoring them as the accuracy
     * charts do (see SessionSummary).
     */

    func intervalSessions(style: IntervalStyle?, limit: Int) async -> [SessionSummary] {
        await sessionTotals(table: "interval_attempts", filter: "?1 IS NULL OR style = ?1", value: SQLValue(style?.name), limit: limit, exercise: .intervals)
    }

    func chordSessions(target: ChordTarget, limit: Int) async -> [SessionSummary] {
        await sessionTotals(table: "chord_attempts", filter: "target = ?1", value: SQLValue(target.name), limit: limit, exercise: .chords)
    }

    func scaleDegreeSessions(mode: Mode?, limit: Int) async -> [SessionSummary] {
        await sessionTotals(table: "scale_degree_attempts", filter: "?1 IS NULL OR mode = ?1", value: SQLValue(mode?.name), limit: limit, exercise: .scaleDegrees)
    }

    func progressionSessions(mode: Mode, limit: Int) async -> [SessionSummary] {
        await sessionAnswers(table: "progression_attempts", column: "progression", mode: mode, limit: limit, exercise: .progressions, itemsOf: Self.progressionItems)
    }

    func dictationSessions(mode: Mode, limit: Int) async -> [SessionSummary] {
        await sessionAnswers(table: "dictation_attempts", column: "melody", mode: mode, limit: limit, exercise: .dictation, itemsOf: Self.dictationItems)
    }

    func rhythmSessions(limit: Int) async -> [SessionSummary] {
        await sessionAnswers(table: "rhythm_attempts", column: "rhythm", mode: nil, limit: limit, exercise: .rhythm, itemsOf: Self.rhythmItems)
    }

    private func sessionTotals(table: String, filter: String, value: SQLValue, limit: Int, exercise: ExerciseKind) async -> [SessionSummary] {
        await read([]) { db in
            try db.query("""
            SELECT session_id AS sessionId, MIN(answered_at) AS startedAtMillis, COUNT(*) AS attempts, SUM(is_correct) AS correct
            FROM \(table) WHERE \(filter) GROUP BY session_id ORDER BY startedAtMillis DESC LIMIT ?2
            """, [value, SQLValue(limit)]).map { row in
                SessionSummary(
                    sessionId: row.string("sessionId"), exercise: exercise, startedAtMillis: row.int64("startedAtMillis"),
                    attempts: row.int("attempts"), correct: row.int("correct")
                )
            }
        }
    }

    /// Every answer from the latest [limit] sessions (in [mode], when given), scored item by item.
    private func sessionAnswers(
        table: String, column: String, mode: Mode?, limit: Int, exercise: ExerciseKind,
        itemsOf: @escaping (SessionAnswerRow) -> [Bool]
    ) async -> [SessionSummary] {
        await read([]) { db in
            let modeColumn = mode == nil ? "NULL" : "mode"
            let filter = mode == nil ? "1" : "mode = ?2"
            let rows = try db.query("""
            SELECT session_id AS sessionId, answered_at AS answeredAtMillis, \(modeColumn) AS mode, \(column) AS asked, answer AS chosen,
                   is_correct AS isCorrect
            FROM \(table)
            WHERE \(filter) AND session_id IN (
                SELECT session_id FROM \(table) WHERE \(filter) GROUP BY session_id ORDER BY MIN(answered_at) DESC LIMIT ?1
            )
            """, [SQLValue(limit), SQLValue(mode?.name)]).map(SessionAnswerRow.init)
            return Self.sessions(rows, exercise, itemsOf)
        }
    }

    private static let itemizedExercises: [(ExerciseKind, String, String, (SessionAnswerRow) -> [Bool])] = [
        (.progressions, "progression_attempts", "progression", progressionItems),
        (.dictation, "dictation_attempts", "melody", dictationItems),
        (.rhythm, "rhythm_attempts", "rhythm", rhythmItems),
    ]

    /**
     * Groups answers into sessions, newest first, each scored item by item with [itemsOf]. A
     * session whose answers no longer parse is left out rather than shown as having no items.
     */
    private static func sessions(_ rows: [SessionAnswerRow], _ exercise: ExerciseKind, _ itemsOf: (SessionAnswerRow) -> [Bool]) -> [SessionSummary] {
        Dictionary(grouping: rows, by: \.sessionId)
            .compactMap { id, answers -> SessionSummary? in
                let items = answers.flatMap(itemsOf)
                guard !items.isEmpty else { return nil }
                return SessionSummary(
                    sessionId: id, exercise: exercise, startedAtMillis: answers.map(\.answeredAtMillis).min()!,
                    attempts: answers.count, correct: answers.filter(\.isCorrect).count,
                    items: items.count, itemsCorrect: items.filter { $0 }.count
                )
            }
            .sorted { $0.startedAtMillis > $1.startedAtMillis }
    }

    private static func progressionItems(_ row: SessionAnswerRow) -> [Bool] {
        guard let mode = row.mode.flatMap(Mode.named) else { return [] }
        return progressionChordResults(mode, asked: row.asked.decodedSteps(), chosen: row.chosen.decodedSteps()).map(\.isCorrect)
    }

    private static func dictationItems(_ row: SessionAnswerRow) -> [Bool] {
        dictationNoteResults(row.asked.decodedDegrees(), row.chosen.decodedDegrees()).map(\.isCorrect)
    }

    private static func rhythmItems(_ row: SessionAnswerRow) -> [Bool] {
        storedRhythmCellResults(rhythm: row.asked, answer: row.chosen).map(\.isCorrect)
    }

    // MARK: - Recent results, for weighting practice

    /// The latest [limit] interval answers, newest first.
    func recentIntervalResults(limit: Int) async -> [ItemResult<Interval>] {
        await recentResults("SELECT interval AS item, is_correct AS isCorrect FROM interval_attempts ORDER BY answered_at DESC, id DESC LIMIT ?1", limit)
    }

    /// The latest [limit] chord answers; each result is a quality or an inversion, as asked.
    func recentChordResults(limit: Int) async -> [ItemResult<ChordAnswer>] {
        await read([]) { db in
            try db.query("SELECT target, quality, inversion, is_correct AS isCorrect FROM chord_attempts ORDER BY answered_at DESC, id DESC LIMIT ?1", [SQLValue(limit)])
                .compactMap { row in
                    let answer: ChordAnswer? = switch ChordTarget.named(row.string("target")) {
                    case .quality: ChordQuality.named(row.string("quality")).map(ChordAnswer.quality)
                    case .inversion: ChordInversion.named(row.string("inversion")).map(ChordAnswer.inversion)
                    case nil: nil
                    }
                    return answer.map { ItemResult(item: $0, isCorrect: row.bool("isCorrect")) }
                }
        }
    }

    func recentScaleDegreeResults(limit: Int) async -> [ItemResult<ScaleDegree>] {
        await recentResults("SELECT degree AS item, is_correct AS isCorrect FROM scale_degree_attempts ORDER BY answered_at DESC, id DESC LIMIT ?1", limit)
    }

    /// One result per chord after the opening tonic, from the latest [limit] progression answers.
    func recentProgressionResults(limit: Int) async -> [ItemResult<DiatonicChord>] {
        await read([]) { db in
            try db.query("SELECT mode, progression, answer FROM progression_attempts ORDER BY answered_at DESC, id DESC LIMIT ?1", [SQLValue(limit)])
                .flatMap { row -> [ItemResult<DiatonicChord>] in
                    guard let mode = Mode.named(row.string("mode")) else { return [] }
                    return progressionChordResults(mode, asked: row.string("progression").decodedSteps(), chosen: row.string("answer").decodedSteps())
                }
        }
    }

    /// One result per note after the opening tonic, from the latest [limit] dictations.
    func recentDictationResults(limit: Int) async -> [ItemResult<ScaleDegree>] {
        await read([]) { db in
            try db.query("SELECT melody, answer FROM dictation_attempts ORDER BY answered_at DESC, id DESC LIMIT ?1", [SQLValue(limit)])
                .flatMap { dictationNoteResults($0.string("melody").decodedDegrees(), $0.string("answer").decodedDegrees()) }
        }
    }

    /// One result per cell, from the latest [limit] rhythm answers.
    func recentRhythmResults(limit: Int) async -> [ItemResult<RhythmCell>] {
        await read([]) { db in
            try db.query("SELECT rhythm, answer FROM rhythm_attempts ORDER BY answered_at DESC, id DESC LIMIT ?1", [SQLValue(limit)])
                .flatMap { storedRhythmCellResults(rhythm: $0.string("rhythm"), answer: $0.string("answer")) }
        }
    }

    private func recentResults<T: StoredEnum>(_ sql: String, _ limit: Int) async -> [ItemResult<T>] {
        await read([]) { db in
            try db.query(sql, [SQLValue(limit)]).compactMap { row in
                T.named(row.string("item")).map { ItemResult(item: $0, isCorrect: row.bool("isCorrect")) }
            }
        }
    }

    // MARK: - Backups

    /// Every stored answer, oldest first, read in one transaction.
    func allAnswers() async throws -> BackupHistory {
        try await withCheckedThrowingContinuation { continuation in
            queue.async {
                continuation.resume(with: Result {
                    try self.database.transaction {
                        BackupHistory(
                            intervals: try self.all(IntervalAttemptEntity.self),
                            chords: try self.all(ChordAttemptEntity.self),
                            scaleDegrees: try self.all(ScaleDegreeAttemptEntity.self),
                            progressions: try self.all(ProgressionAttemptEntity.self),
                            dictations: try self.all(DictationAttemptEntity.self),
                            rhythms: try self.all(RhythmAttemptEntity.self)
                        )
                    }
                })
            }
        }
    }

    /// Adds the valid answers of [history] that are not stored yet, all or nothing.
    func merge(_ history: BackupHistory) async throws -> MergeCounts {
        let counts: MergeCounts = try await withCheckedThrowingContinuation { continuation in
            queue.async {
                continuation.resume(with: Result {
                    try self.database.transaction {
                        var counts = MergeCounts()
                        try self.merge(history.intervals, into: &counts)
                        try self.merge(history.chords, into: &counts)
                        try self.merge(history.scaleDegrees, into: &counts)
                        try self.merge(history.progressions, into: &counts)
                        try self.merge(history.dictations, into: &counts)
                        try self.merge(history.rhythms, into: &counts)
                        return counts
                    }
                })
            }
        }
        Self.announceChange()
        return counts
    }

    private func all<E: AttemptEntity>(_ type: E.Type) throws -> [E] {
        try database.query("SELECT * FROM `\(E.table)` ORDER BY answered_at, id").map(E.init(row:))
    }

    /// Inserts the rows of [incoming] that are valid and not yet stored.
    private func merge<E: AttemptEntity>(_ incoming: [E], into counts: inout MergeCounts) throws {
        var seen = Set(try all(E.self).map(\.identity))
        for row in incoming {
            if !row.isValid {
                counts.unreadable += 1
            } else if !seen.insert(row.identity).inserted {
                counts.alreadyPresent += 1
            } else {
                try database.run(E.insertSQL, row.values)
                counts.added += 1
            }
        }
    }

    // MARK: - Row helpers

    private static func totals(_ rows: [SQLRow]) -> [String: (attempts: Int, correct: Int)] {
        Dictionary(rows.map { ($0.string("item"), (attempts: $0.int("attempts"), correct: $0.int("correct"))) }, uniquingKeysWith: { first, _ in first })
    }

    /// Confusions for rows whose asked name still matches a value; an unknown answer becomes nil.
    private static func confusions<T: StoredEnum>(_ rows: [SQLRow], _: T.Type) -> [Confusion<T>] {
        var counts: [Confusion<T>] = []
        for row in rows {
            guard let asked = T.named(row.string("asked")) else { continue }
            let answered = T.named(row.string("answered"))
            if let index = counts.firstIndex(where: { $0.asked == asked && $0.answered == answered }) {
                counts[index] = Confusion(asked: asked, answered: answered, count: counts[index].count + row.int("count"))
            } else {
                counts.append(Confusion(asked: asked, answered: answered, count: row.int("count")))
            }
        }
        return counts
    }
}

/// What merging a backup's history changed.
struct MergeCounts {
    /// Answers added to history.
    var added = 0
    /// Answers skipped because history already had them, e.g. when importing the same file twice.
    var alreadyPresent = 0
    /// Answers skipped because the app could not read them.
    var unreadable = 0
}

private extension Array {
    func zip<Other>(_ other: [Other]) -> [(Element, Other)] { Array<(Element, Other)>(Swift.zip(self, other)) }
}
