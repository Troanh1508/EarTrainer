import Foundation

struct CalendarDay: Hashable {
    let day: Int64
    let answers: Int
    let isToday: Bool
    let isFuture: Bool

    /// 0 without practice, then 1 to 4 by how much.
    var level: Int {
        switch answers {
        case ...0: 0
        case ...10: 1
        case ...25: 2
        case ...50: 3
        default: 4
        }
    }
}

struct PracticeLogUiState {
    static let weeks = 5

    /// Consecutive days with practice up to today, or up to yesterday while today has none yet.
    let currentStreak: Int
    let longestStreak: Int
    /// `weeks` whole weeks, Monday first, ending with the current week.
    let calendar: [CalendarDay]
    /// The current week, Monday to Sunday.
    let week: [CalendarDay]
    /// Most recent first.
    let sessions: [SessionSummary]

    var weekAnswers: Int { week.reduce(0) { $0 + $1.answers } }
    var practicedDays: Int { calendar.filter { $0.answers > 0 }.count }
    var hasHistory: Bool { !sessions.isEmpty }

    static func from(days: [DayCount], sessions: [SessionSummary], today: Int64) -> PracticeLogUiState {
        let answersOn = Dictionary(days.map { ($0.day, $0.answers) }, uniquingKeysWith: +)
        let practiced = Set(answersOn.filter { $0.value > 0 }.keys)
        // A streak is not broken until a whole day passes without practice.
        var current = 0
        var day = practiced.contains(today) ? today : today - 1
        while practiced.contains(day) {
            current += 1
            day -= 1
        }
        var longest = 0
        var run = 0
        var previous: Int64?
        for practicedDay in practiced.sorted() {
            run = previous == practicedDay - 1 ? run + 1 : 1
            longest = max(longest, run)
            previous = practicedDay
        }
        let weekStart = today - Int64(weekdayOf(today))
        let calendarStart = weekStart - 7 * Int64(weeks - 1)
        let calendar = (calendarStart..<(weekStart + 7)).map { calendarDay in
            CalendarDay(day: calendarDay, answers: answersOn[calendarDay] ?? 0, isToday: calendarDay == today, isFuture: calendarDay > today)
        }
        return PracticeLogUiState(
            currentStreak: current, longestStreak: longest, calendar: calendar, week: Array(calendar.suffix(7)), sessions: sessions
        )
    }
}

/// Overall accuracy of one exercise, counted as on the Progress screen.
struct ExerciseOverview: Hashable {
    let exercise: ExerciseKind
    let attempts: Int
    let correct: Int

    var accuracy: Double? { attempts == 0 ? nil : Double(correct) / Double(attempts) }
    var needsWork: Bool { accuracy.map { $0 < AccuracyItem.weakThreshold } ?? false }
}

/// What to practice next: the weakest items of the exercise practiced most recently.
struct Suggestion: Hashable {
    let exercise: ExerciseKind
    let items: [String]
}

struct HomeUiState {
    /// Fewer answers than this say too little about an item to call it weak.
    static let minAnswersForSuggestion = 3
    static let suggestedItems = 3

    let answersToday: Int
    /// Sessions finished today, all of their questions answered.
    let sessionsToday: Int
    /// Finished sessions a day that count as reaching today's goal.
    let dailyGoal: Int
    let currentStreak: Int
    /// Every exercise, in menu order.
    let exercises: [ExerciseOverview]
    let suggestion: Suggestion?

    var goalProgress: Double { min(1, Double(sessionsToday) / Double(dailyGoal)) }
    var sessionsToGoal: Int { max(0, dailyGoal - sessionsToday) }

    static func from(
        days: [DayCount], finishedSessions: [DaySessions], today: Int64, dailyGoal: Int,
        lastExercise: ExerciseKind?, accuracy: [ExerciseKind: [AccuracyItem]]
    ) -> HomeUiState {
        let suggestion = lastExercise.flatMap { exercise -> Suggestion? in
            var seen = Set<String>()
            let weakest = (accuracy[exercise] ?? [])
                .filter { $0.attempts >= minAnswersForSuggestion && $0.needsWork }
                .enumerated()
                .sorted { a, b in
                    if a.element.accuracy! != b.element.accuracy! { return a.element.accuracy! < b.element.accuracy! }
                    if a.element.attempts != b.element.attempts { return a.element.attempts > b.element.attempts }
                    return a.offset < b.offset
                }
                .map(\.element.label)
                // The same label can come from two modes, e.g. V in major and in minor.
                .filter { seen.insert($0).inserted }
                .prefix(suggestedItems)
            return weakest.isEmpty ? nil : Suggestion(exercise: exercise, items: Array(weakest))
        }
        return HomeUiState(
            answersToday: days.first { $0.day == today }?.answers ?? 0,
            sessionsToday: finishedSessions.first { $0.day == today }?.sessions ?? 0,
            dailyGoal: dailyGoal,
            currentStreak: PracticeLogUiState.from(days: days, sessions: [], today: today).currentStreak,
            exercises: ExerciseKind.allCases.map { exercise in
                let items = accuracy[exercise] ?? []
                return ExerciseOverview(exercise: exercise, attempts: items.reduce(0) { $0 + $1.attempts }, correct: items.reduce(0) { $0 + $1.correct })
            },
            suggestion: suggestion
        )
    }
}
