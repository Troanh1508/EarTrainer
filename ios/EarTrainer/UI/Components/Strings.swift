import Foundation

/// English plural choice: [one] for exactly 1, otherwise [other].
func plural(_ count: Int, _ one: String, _ other: String) -> String { count == 1 ? one : other }

/// The user-facing wording, ported from the Android app's string resources.
enum Strings {
    static let appName = "Ear Trainer"

    // MARK: Home

    static let greetingMorning = "Good morning"
    static let greetingAfternoon = "Good afternoon"
    static let greetingEvening = "Good evening"
    static let openBackup = "Backup and restore"
    static let goalTitle = "Today's goal"
    static func goalOf(_ goal: Int) -> String { "of \(goal)" }
    static func goalRing(_ done: Int, _ goal: Int) -> String { "\(done) of \(goal) \(plural(goal, "session", "sessions")) finished today" }
    static func goalRemaining(_ count: Int) -> String { "Finish \(count) more \(plural(count, "session", "sessions")) to reach today's goal." }
    static func goalKeepStreak(_ days: Int) -> String { "Answer a question today to keep your \(days)-day streak going." }
    static let goalChange = "Change daily goal"
    static let goalDialogTitle = "Daily goal"
    static func goalDialogDescription(_ length: Int) -> String {
        "How many sessions a day count as reaching your goal. A session is finished once all \(length) of its questions are answered."
    }
    static func goalOption(_ sessions: Int) -> String { "\(sessions) \(plural(sessions, "session", "sessions")) a day" }
    static let goalDone = "Goal reached for today. Nice work."
    static func streak(_ days: Int) -> String { "\(days)-day streak" }
    static let suggestTitle = "Suggested for you"
    static func suggestText(_ names: String, count: Int, exercise: String) -> String {
        "\(names) \(plural(count, "needs", "need")) the most work in \(exercise)."
    }
    static func joinTwo(_ a: String, _ b: String) -> String { "\(a) and \(b)" }
    static func joinThree(_ a: String, _ b: String, _ c: String) -> String { "\(a), \(b) and \(c)" }
    static func suggestButton(_ exercise: String) -> String { "Practice \(exercise)" }
    static let exercises = "Exercises"
    static let seeProgress = "See progress"
    static let new = "New"
    static let offlineNote = "Works fully offline. More exercises are on the way."
    static let soundCredit = "Sounds: Upright Piano KW and Spanish Classical Guitar from FreePats (CC0)."

    static func cardDetail(_ exercise: ExerciseKind) -> String {
        switch exercise {
        case .intervals: "Two notes, name the distance"
        case .chords: "Quality or inversion"
        case .scaleDegrees: "A note in its key"
        case .progressions: "Chords in a key"
        case .dictation: "Write down a melody"
        case .rhythm: "Pick the notation"
        }
    }

    static func exerciseName(_ exercise: ExerciseKind) -> String {
        switch exercise {
        case .intervals: "Intervals"
        case .chords: "Chords"
        case .scaleDegrees: "Scale degrees"
        case .progressions: "Progressions"
        case .dictation: "Dictation"
        case .rhythm: "Rhythm"
        }
    }

    static func exerciseTitle(_ exercise: ExerciseKind) -> String {
        switch exercise {
        case .intervals: "Interval Training"
        case .chords: "Chord Training"
        case .scaleDegrees: "Scale Degrees"
        case .progressions: "Chord Progressions"
        case .dictation: "Melodic Dictation"
        case .rhythm: "Rhythm"
        }
    }

    // MARK: Practice

    static let openSettings = "Practice settings"
    static let intervalsInstruction = "Listen to the two notes and identify the interval."
    static let scoreEmpty = "No answers yet"
    static let play = "Play"
    static let replay = "Replay"
    static let stop = "Stop"
    static let listenFirst = "Play the notes to unlock the answers."
    static let playbackFailed = "Audio could not be played. Another app may be using audio, or the output device is unavailable. Try again."
    static let feedbackCorrect = "Correct"
    static let feedbackIncorrect = "Not quite"
    static func feedbackYourAnswer(_ answer: String) -> String { "You answered \(answer)." }
    static func semitones(_ count: Int) -> String { "\(count) \(plural(count, "semitone", "semitones"))" }
    static func sessionQuestion(_ number: Int, _ total: Int) -> String { "Question \(number) of \(total)" }
    static func sessionRight(_ right: Int, _ total: Int) -> String { "\(right) of \(total) right" }
    static let seeResults = "See results"
    static let playing = "Playing…"
    static let hearAgain = "Tap to hear it again"
    static func responseTime(_ millis: Int64) -> String { String(format: "%.1f s to answer", Double(millis) / 1000) }
    static let next = "Next question"
    static let choiceCorrect = "correct answer"
    static let choiceIncorrect = "your answer, incorrect"

    static let chordsInstructionQuality = "Listen to the chord and identify its quality."
    static let chordsInstructionInversion = "Listen to the chord and identify its inversion."
    static func chordsSummary(_ root: String, _ quality: String, _ inversion: String) -> String { "\(root) \(quality) · \(inversion)" }
    static func bassNote(_ inversion: ChordInversion) -> String {
        switch inversion {
        case .rootPosition: "Root in the bass"
        case .first: "3rd in the bass"
        case .second: "5th in the bass"
        case .third: "7th in the bass"
        }
    }

    static let degreesInstruction = "Listen to the key being set, then identify the scale degree of the final note."
    static func degreesSummary(_ label: String, _ solfege: String, _ key: String) -> String { "\(label) (\(solfege)) in \(key)" }

    static let progressionsInstruction = "Listen to the progression and choose the chords you heard. It always starts on the tonic."
    static func progressionsSummary(_ label: String, _ key: String, _ symbols: String) -> String { "\(label) in \(key) (\(symbols))" }

    static let dictationInstruction = "Listen to the key being set, then write down the melody. It starts on the tonic, which is filled in for you. Tap any note to change it."
    static func dictationSummary(_ label: String, _ solfege: String, _ key: String) -> String { "\(label) (\(solfege)) in \(key)" }
    static let undo = "Undo"
    static let clearAll = "Clear all"
    static let check = "Check"
    static let slotGiven = "given"
    static let slotEmpty = "empty"
    static let slotSelected = "selected for editing"
    static func slotWrong(_ written: String, _ expected: String) -> String { "\(written), incorrect, should be \(expected)" }

    static let rhythmInstruction = "After a one-bar count-in, listen to the rhythm and choose the notation that matches it."
    static func rhythmDetail(_ bpm: Int) -> String { "\(bpm) BPM" }
    static let rhythmSummary = "The rhythm you heard is marked with a tick. Replay to hear it again."

    // MARK: Summary

    static let summaryComplete = "Session complete"
    static func score(_ right: Int, _ total: Int) -> String { "\(right)/\(total)" }
    static func summaryPercent(_ percent: Int, _ caption: String) -> String { "\(percent)% \(caption)" }
    static func aboveAverage(_ points: Int) -> String { "\(points) \(plural(points, "point", "points")) above your recent average" }
    static func belowAverage(_ points: Int) -> String { "\(points) \(plural(points, "point", "points")) below your recent average" }
    static let levelWithAverage = "Level with your recent average"
    static func duration(_ seconds: Int64) -> String { String(format: "%d:%02d", seconds / 60, seconds % 60) }
    static let durationLabel = "minutes"
    static func seconds(_ millis: Int64) -> String { String(format: "%.1f s", Double(millis) / 1000) }
    static let perAnswer = "per answer"
    static func replays(_ count: Int) -> String { plural(count, "replay", "replays") }
    static let thisSession = "This session"
    static let right = "Right"
    static let missed = "Missed"
    static func tallyDescription(_ label: String, _ right: Int, _ total: Int) -> String { "\(label): \(right) of \(total) right" }
    static let youMissed = "You missed"
    static func mistake(_ label: String, _ chosen: String) -> String { "\(label) → \(chosen)" }
    static func mistakeTimes(_ text: String, _ count: Int) -> String { "\(text) ×\(count)" }
    static let noMistakes = "Nothing missed this session."
    static func retryMissed(_ count: Int) -> String { count == 1 ? "Practice the one I missed" : "Practice the \(count) I missed" }
    static let newSession = "New session"
    static let done = "Done"

    // MARK: Settings

    static let settingsTitle = "Practice Settings"
    static let newSessionNote = "Changes are saved automatically and start a new practice session."
    static let intervalsTitle = "Intervals"
    static func intervalsDescription(_ minimum: Int) -> String { "Questions use only the intervals you select. Keep at least \(minimum)." }
    static let selectAll = "Select all"
    static let stylesTitle = "How notes are played"
    static let stylesDescription = "Questions randomly use any of the selected styles. Keep at least one."
    static let choicesTitle = "Answer choices"
    static let choicesDescription = "How many intervals to choose from in each question."
    static let choicesAll = "All"
    static let restoreDefaults = "Restore defaults"
    static let instrumentTitle = "Instrument"
    static let instrumentDescription = "The sound used in every exercise. Changing it does not start a new session."
    static let focusTitle = "Focus on weak spots"
    static let focusDescription = "Ask more often about what you have recently got wrong. Everything you selected still comes up."
    static let chordTargetTitle = "What to identify"
    static let chordTargetDescription = "Name the type of chord, or which chord tone is in the bass."
    static let chordQualitiesTitle = "Chord types"
    static func chordQualitiesDescription(_ minimum: Int) -> String { "Questions use only the chord types you select. Keep at least \(minimum)." }
    static let inversionsTitle = "Include inversions"
    static let inversionsDescription = "Also play chords with the 3rd, 5th or 7th in the bass."
    static let inversionsAlways = "Always on when identifying inversions."
    static let modesTitle = "Modes"
    static let modesDescription = "Questions randomly use any of the selected modes. Keep at least one."
    static let degreesTitle = "Notes to identify"
    static let degreesDescription = "Only the seven notes of the key, or every note including chromatic ones."
    static let keyTitle = "Key"
    static let keyDescription = "Practice in one familiar key, or a new key each question."
    static let contextTitle = "How the key is set"
    static let contextDescription = "Played before each note so you can hear where home is."
    static let sourceTitle = "Progressions"
    static let sourceDescription = "Common progressions are the ones heard in songs and classical pieces. Random uses any order of the chosen chords."
    static let vocabularyTitle = "Chords"
    static let vocabularyDescription = "Which chords of the key progressions can use. With common progressions, only those built from these chords are asked."
    static let progressionLengthTitle = "Length"
    static let progressionLengthDescription = "How many chords each progression has, including the opening tonic."
    static let dictationLengthTitle = "Length"
    static let dictationLengthDescription = "How many notes each melody has, including the opening tonic."
    static let rhythmVocabularyTitle = "Rhythms"
    static let rhythmVocabularyDescription = "Which note values and rests rhythms can use. Each level adds to the one before."
    static let barsTitle = "Length"
    static let barsDescription = "How long each rhythm is, in 4/4 bars."
    static func bars(_ count: Int) -> String { "\(count) \(plural(count, "bar", "bars"))" }
    static let tempoTitle = "Tempo"
    static let tempoDescription = "How fast the beat is, in beats per minute."
    static func tempoOption(_ name: String, _ bpm: Int) -> String { "\(name) · \(bpm) BPM" }

    // MARK: Progress

    static let progressTitle = "Your Progress"
    static let progressEmpty = "Answer a few questions and your progress will appear here."
    static let filterAll = "All styles"
    static let filterAllModes = "All modes"
    static let filterAllRhythms = "All rhythms"
    static let noAnswersForFilter = "No answers here yet"
    static let notPracticed = "Not practiced yet"
    static let scoringTitle = "How scoring works"
    static func itemScore(_ correct: Int, _ attempts: Int) -> String { "\(correct) of \(attempts)" }
    static func changeUp(_ points: Int) -> String { "Up \(points) \(plural(points, "point", "points")) recently" }
    static func changeDown(_ points: Int) -> String { "Down \(points) \(plural(points, "point", "points")) recently" }
    static let changeSteady = "About the same recently"
    static let trendTitle = "Accuracy over time"
    static func trendWindow(_ count: Int) -> String { count == 1 ? "Last session" : "Last \(count) sessions" }
    static func trendWindowFiltered(_ filter: String, _ count: Int) -> String { count == 1 ? "\(filter) · last session" : "\(filter) · last \(count) sessions" }
    static let trendNeedsMore = "Practice a couple of sessions of this exercise to see how your accuracy changes."
    static let trendLatest = "Latest"
    static func trendDescription(_ count: Int, _ values: String) -> String {
        count == 1 ? "Accuracy in your last session: \(values)" : "Accuracy in your last \(count) sessions, oldest first: \(values)"
    }
    static let weakestFirst = "Weakest first"
    static func legendNeedsWork(_ percent: Int) -> String { "Below \(percent)%: needs work" }
    static let legendOnTrack = "On track"
    static let recentSessions = "Recent sessions"
    static let showMistakes = "Show mistakes"

    // MARK: Mistakes and comparisons

    static let mistakesTitle = "Mistakes"
    static func mistakesScope(_ exercise: String, _ filter: String) -> String { "\(exercise) · \(filter)" }
    static func whenLabel(_ exercise: ExerciseKind) -> String {
        switch exercise {
        case .intervals: "When the interval was"
        case .chords: "When the right answer was"
        case .scaleDegrees, .dictation: "When the note was"
        case .progressions: "When the chord was"
        case .rhythm: "When the rhythm figure was"
        }
    }
    static let chooseItem = "Choose another"
    static func itemOption(_ label: String, _ percent: String) -> String { "\(label) · \(percent)" }
    static func timesHeard(_ count: Int) -> String { plural(count, "time heard", "times heard") }
    static let youAnswered = "You answered"
    static func rightAnswer(_ label: String) -> String { "\(label) (right)" }
    static let somethingElse = "Something else"
    static let otherAnswers = "Other answers"
    static func count(_ count: Int) -> String { "\(count)×" }
    static func pieDescription(_ label: String, _ shares: String) -> String { "When it was \(label), you answered: \(shares)" }
    static func tipLead(_ label: String) -> String { "Often mistaken for \(label)." }
    static func tipApart(_ semitones: Int) -> String {
        semitones == 1 ? "The two are a semitone apart; hearing them back to back helps." : "The two are \(semitones) semitones apart; hearing them back to back helps."
    }
    static let tipGeneral = "Hearing the two back to back helps."
    static let tipSomethingElse = "Most often answered with something that does not line up with it."
    static let mistakesNone = "No mistakes yet: every answer was right."
    static let pairsTitle = "Most confused pairs"
    static let pairsNone = "No mix-ups yet."
    static func pair(_ first: String, _ second: String) -> String { "\(first) and \(second)" }
    static func pairCount(_ count: Int) -> String { "\(count) \(plural(count, "mix-up", "mix-ups"))" }
    static func comparePair(_ first: String, _ second: String) -> String { "Compare \(first) and \(second)" }
    static let compare = "Compare"

    static let compareTitle = "Compare"
    static func compareSubtitle(_ first: String, _ second: String) -> String { "\(first) or \(second)" }
    static let compareListenTitle = "Hear the difference"
    static func compareHint(_ exercise: ExerciseKind) -> String {
        switch exercise {
        case .intervals: "Both start from the same note, played the same way."
        case .chords: "Both are played from the same bass note."
        case .scaleDegrees, .dictation: "Both are in the same key, after the same cadence."
        case .progressions: "Both follow the same tonic chord, in the same key."
        case .rhythm: "The same two figures in opposite order, at the same tempo."
        }
    }
    static func playExample(_ label: String) -> String { "Play \(label)" }
    static let playBoth = "Play both"
    static func startsWith(_ figure: String) -> String { "\(figure) first" }
    static func compareQuestion(_ number: Int, _ total: Int) -> String { "Question \(number) of \(total)" }
    static func rightSoFar(_ count: Int) -> String { "\(count) right" }
    static func itWas(_ label: String) -> String { "It was \(label)" }
    static let seeResult = "See result"
    static func compareResult(_ correct: Int, _ total: Int) -> String { "\(correct) of \(total) right" }
    static let compareResultGood = "You can tell these two apart. Their answers now count toward your progress."
    static let compareResultKeepGoing = "Getting there. Play the two examples again, then try another round."
    static let compareAgain = "Another round"

    // MARK: Practice log

    static let logTitle = "Practice Log"
    static let currentStreak = "current streak"
    static let longestStreak = "longest streak"
    static func days(_ count: Int) -> String { "\(count) \(plural(count, "day", "days"))" }
    static func calendarTitle(_ weeks: Int) -> String { weeks == 1 ? "Last week" : "Last \(weeks) weeks" }
    static let answersPerDay = "Answers per day"
    static func calendarDescription(_ practiced: Int, _ total: Int) -> String { "Practiced on \(practiced) of the last \(total) days" }
    static let less = "Less"
    static let more = "More"
    static let thisWeek = "This week"
    static func answers(_ count: Int) -> String { "\(count) \(plural(count, "answer", "answers"))" }

    // MARK: Backup

    static let backupTitle = "Backup & Restore"
    static let backupAutoTitle = "Automatic backup"
    static let backupAutoDescription = "Your history and settings are part of this device's iCloud and computer backups. They come back when you restore a backup or set up a new iPhone or iPad from one."
    static let backupExportTitle = "Export to a file"
    static let backupExportDescription = "Save all your history and settings in one file. Keep it somewhere safe, or use it to move to another device. Backups from Ear Trainer for Android work too."
    static let backupExportButton = "Export"
    static let backupImportTitle = "Import from a file"
    static let backupImportDescription = "Add the history from an exported file to this device. Answers that are already here are not added twice. Your settings are replaced by the ones in the file."
    static let backupImportButton = "Import"
    static func backupFileName(_ date: String) -> String { "ear-trainer-backup-\(date).json" }
    static func exported(_ count: Int) -> String { "Exported \(count) \(plural(count, "answer", "answers")) and your settings." }
    static func imported(_ count: Int) -> String { "Imported \(count) new \(plural(count, "answer", "answers"))." }
    static func importedAlreadyPresent(_ count: Int) -> String { count == 1 ? "1 was already here." : "\(count) were already here." }
    static func importedUnreadable(_ count: Int) -> String {
        count == 1 ? "1 could not be read and was skipped." : "\(count) could not be read and were skipped."
    }
    static let importedSettings = "Settings restored."
    static let exportFailed = "Could not save the file. Try another location."
    static let importFailed = "Could not open that file."
    static let notABackup = "That file is not an Ear Trainer backup."
    static let newerVersion = "That backup was made by a newer version of Ear Trainer. Update the app, then try again."
    static let tooLarge = "That file is too large to be an Ear Trainer backup."
    static let ok = "OK"
}
