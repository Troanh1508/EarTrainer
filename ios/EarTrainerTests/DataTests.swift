import XCTest
@testable import EarTrainer

final class DataTests: XCTestCase {
    private var history: PracticeHistory!

    override func setUpWithError() throws {
        history = try PracticeHistory(database: SQLiteDatabase.inMemory())
    }

    private func answered<Q: ExerciseQuestion>(_ question: Q, _ answer: Q.Answer, at millis: Int64) -> ExerciseState<Q> {
        ExerciseEngine { question }.start().promptPlayed(atMillis: millis - 1_000).submitAnswer(answer, atMillis: millis)!
    }

    private func recordIntervals(session: String, start: Int64, results: [Bool]) {
        for (index, right) in results.enumerated() {
            let question = IntervalQuestion(lowerPitch: Pitch(60), interval: .majorThird, style: .harmonic, choices: [.minorThird, .majorThird])
            history.record(IntervalAttempt(answered(question, right ? .majorThird : .minorThird, at: start + Int64(index) * 1_000), sessionId: session)!)
        }
    }

    func testAccuracyAndConfusionsCountEveryAnswer() async {
        recordIntervals(session: "a", start: 1_700_000_000_000, results: [true, false, false])
        let accuracy = await history.intervalAccuracy(style: nil)
        XCTAssertEqual(accuracy.count, Interval.allCases.count)
        let third = accuracy.first { $0.item == .majorThird }!
        XCTAssertEqual(third.attempts, 3)
        XCTAssertEqual(third.correct, 1)
        let confusions = await history.intervalConfusions(style: .harmonic)
        XCTAssertEqual(Set(confusions), [Confusion(asked: .majorThird, answered: .majorThird, count: 1), Confusion(asked: .majorThird, answered: .minorThird, count: 2)])
        let melodic = await history.intervalAccuracy(style: .melodicAscending)
        XCTAssertEqual(melodic.reduce(0) { $0 + $1.attempts }, 0)
    }

    func testFinishedSessionsCountOnlyFullSessions() async {
        recordIntervals(session: "full", start: 1_700_000_000_000, results: Array(repeating: true, count: sessionLength))
        recordIntervals(session: "short", start: 1_700_000_100_000, results: [true, true])
        let finished = await history.finishedSessionsPerDay()
        XCTAssertEqual(finished.reduce(0) { $0 + $1.sessions }, 1)
        let days = await history.answersPerDay()
        XCTAssertEqual(days.reduce(0) { $0 + $1.answers }, sessionLength + 2)
        XCTAssertEqual(days.last?.day, localEpochDay(1_700_000_100_000))
    }

    func testProgressionSessionsAreScoredChordByChord() async {
        let key = Key(tonic: Pitch(60), mode: .major)
        let asked = CommonProgressions.parse(.major, "I vi IV V")
        let chosen = CommonProgressions.parse(.major, "I ii IV V")
        let question = ProgressionQuestion(key: key, progression: asked, choices: [asked, chosen])
        history.record(ProgressionAttempt(answered(question, chosen, at: 1_700_000_000_000), sessionId: "p")!)
        let sessions = await history.recentSessions(limit: 5)
        XCTAssertEqual(sessions.map(\.exercise), [.progressions])
        // Three chords after the given tonic, two of them right; the progression as a whole was wrong.
        XCTAssertEqual(sessions[0].items, 3)
        XCTAssertEqual(sessions[0].itemsCorrect, 2)
        XCTAssertEqual(sessions[0].correct, 0)
        let accuracy = await history.progressionAccuracy(mode: .major)
        XCTAssertEqual(accuracy.first { $0.item.step == 6 }?.correct, 0)
        XCTAssertEqual(accuracy.first { $0.item.step == 5 }?.correct, 1)
    }

    func testBackupRoundTripsAndSkipsDuplicates() async throws {
        recordIntervals(session: "a", start: 1_700_000_000_000, results: [true, false])
        let exported = try await history.allAnswers()
        let storage = SettingsStorage(defaults: UserDefaults(suiteName: UUID().uuidString)!)
        let file = BackupFile(exportedAtMillis: 1, settings: SettingsBackup.export(storage.preferences), history: exported)
        let decoded = try BackupCodec.decode(try BackupCodec.encode(file))
        XCTAssertEqual(decoded.history.intervals, exported.intervals)
        XCTAssertEqual(decoded.settings["rhythm.bars"], SettingValue(.int(1)))

        let again = try await history.merge(decoded.history)
        XCTAssertEqual(again.added, 0)
        XCTAssertEqual(again.alreadyPresent, 2)

        let fresh = try PracticeHistory(database: SQLiteDatabase.inMemory())
        let counts = try await fresh.merge(decoded.history)
        XCTAssertEqual(counts.added, 2)
    }

    /// A backup as the Android app writes it: the same fields, so files move between the apps.
    func testReadsAndroidBackups() throws {
        let json = """
        {
          "app": "com.example.eartrainer", "formatVersion": 1, "exportedAtMillis": 1760000000000,
          "settings": { "rhythm.bars": { "int": 2 }, "sound.instrument": { "string": "GUITAR" }, "future.thing": { "float": 1.5 } },
          "history": {
            "intervals": [{ "sessionId": "s", "answeredAtMillis": 1760000000000, "interval": "TRITONE", "style": "HARMONIC",
                            "lowerMidi": 60, "answer": "PERFECT_FOURTH", "isCorrect": false, "playCount": 2, "responseMillis": 1800 }],
            "rhythms": [{ "sessionId": "r", "answeredAtMillis": 1760000001000, "tempo": "MEDIUM", "rhythm": "HALF,QUARTER,NOT_A_CELL",
                          "answer": "HALF,HALF", "isCorrect": false, "playCount": 1, "responseMillis": 900 }]
          }
        }
        """
        let file = try BackupCodec.decode(Data(json.utf8))
        XCTAssertTrue(file.history.intervals[0].isValid)
        XCTAssertFalse(file.history.rhythms[0].isValid)
        let imported = SettingsBackup.importing(file.settings)
        XCTAssertEqual(RhythmSettingsCodec.decode(imported).bars, 2)
        XCTAssertEqual(SoundSettingsCodec.decode(imported).instrument, .guitar)
        XCTAssertNil(imported["future.thing"])
    }

    func testRejectsFilesThatAreNotBackups() {
        XCTAssertThrowsError(try BackupCodec.decode(Data("{\"hello\": 1}".utf8))) { XCTAssertEqual($0 as? BackupError, .notABackup) }
        XCTAssertThrowsError(try BackupCodec.decode(Data("{\"app\":\"com.example.eartrainer\",\"formatVersion\":9,\"exportedAtMillis\":1}".utf8))) {
            XCTAssertEqual($0 as? BackupError, .newerVersion)
        }
    }

    func testSettingsFallBackToDefaultsWhenInvalid() {
        var preferences: Preferences = ["interval_training.intervals": .stringSet(["OCTAVE"]), "interval_training.choice_count": .int(5)]
        XCTAssertEqual(IntervalSettingsCodec.decode(preferences), IntervalExerciseSettings())
        preferences["interval_training.intervals"] = .stringSet(["OCTAVE", "TRITONE", "UNKNOWN"])
        XCTAssertEqual(IntervalSettingsCodec.decode(preferences).intervals, [.tritone, .octave])
        XCTAssertEqual(IntervalSettingsCodec.decode(preferences).choiceCount, 6)
    }

    func testSettingsPersistAcrossLaunches() {
        let defaults = UserDefaults(suiteName: UUID().uuidString)!
        let store = SettingsStorage(defaults: defaults).store(DictationSettingsCodec.decode, DictationSettingsCodec.encode)
        store.update { $0.withModeToggled(.minor) }
        let reopened = SettingsStorage(defaults: defaults).store(DictationSettingsCodec.decode, DictationSettingsCodec.encode)
        XCTAssertEqual(reopened.current.modes, [.major, .minor])
    }

    func testStreaksAndCalendar() {
        let today: Int64 = 20_000
        let days = [19_995, 19_996, 19_998, 19_999].map { DayCount(day: $0, answers: 5) }
        let state = PracticeLogUiState.from(days: days, sessions: [], today: today)
        // Yesterday counts until today is over.
        XCTAssertEqual(state.currentStreak, 2)
        XCTAssertEqual(state.longestStreak, 2)
        XCTAssertEqual(state.calendar.count, 7 * PracticeLogUiState.weeks)
        XCTAssertEqual(weekdayOf(state.calendar[0].day), 0)
    }
}
