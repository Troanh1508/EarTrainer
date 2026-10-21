import Foundation

/// What every answered question records: who, when, how it went and how long it took.
struct AttemptInfo<A: Hashable> {
    let sessionId: String
    let answeredAtMillis: Int64
    let answer: A
    let isCorrect: Bool
    /// Times the prompt was started before answering; 1 means no replays.
    let playCount: Int
    /// From the first time the prompt started playing until the answer.
    let responseMillis: Int64

    /// The record for [state]'s current question, or nil if it has not been answered.
    init?<Q>(_ state: ExerciseState<Q>, sessionId: String) where Q.Answer == A {
        guard let answered = state.phase.answered else { return nil }
        let firstPlayed = state.firstPlayedAtMillis ?? answered.answeredAtMillis
        self.sessionId = sessionId
        answeredAtMillis = answered.answeredAtMillis
        answer = answered.selectedAnswer
        isCorrect = answered.isCorrect
        playCount = state.playCount
        responseMillis = max(0, answered.answeredAtMillis - firstPlayed)
    }
}

/// One answered interval question, as stored in practice history.
struct IntervalAttempt {
    let info: AttemptInfo<Interval>
    let interval: Interval
    let style: IntervalStyle
    let lowerPitch: Pitch

    init?(_ state: ExerciseState<IntervalQuestion>, sessionId: String) {
        guard let info = AttemptInfo(state, sessionId: sessionId) else { return nil }
        self.info = info
        interval = state.question.interval
        style = state.question.style
        lowerPitch = state.question.lowerPitch
    }
}

/// One answered chord question, as stored in practice history.
struct ChordAttempt {
    let info: AttemptInfo<ChordAnswer>
    let quality: ChordQuality
    let inversion: ChordInversion
    let style: ChordStyle
    let target: ChordTarget
    let bass: Pitch

    init?(_ state: ExerciseState<ChordQuestion>, sessionId: String) {
        guard let info = AttemptInfo(state, sessionId: sessionId) else { return nil }
        self.info = info
        quality = state.question.quality
        inversion = state.question.inversion
        style = state.question.style
        target = state.question.target
        bass = state.question.bass
    }
}

/// One answered scale-degree question, as stored in practice history.
struct ScaleDegreeAttempt {
    let info: AttemptInfo<ScaleDegree>
    let key: Key
    let degree: ScaleDegree
    let context: KeyContext

    init?(_ state: ExerciseState<ScaleDegreeQuestion>, sessionId: String) {
        guard let info = AttemptInfo(state, sessionId: sessionId) else { return nil }
        self.info = info
        key = state.question.key
        degree = state.question.degree
        context = state.question.context
    }
}

/// One answered progression question, as stored in practice history.
struct ProgressionAttempt {
    let info: AttemptInfo<ChordProgression>
    let key: Key
    let progression: ChordProgression

    init?(_ state: ExerciseState<ProgressionQuestion>, sessionId: String) {
        guard let info = AttemptInfo(state, sessionId: sessionId) else { return nil }
        self.info = info
        key = state.question.key
        progression = state.question.progression
    }
}

/// One answered dictation question, as stored in practice history.
struct DictationAttempt {
    let info: AttemptInfo<Melody>
    let key: Key
    let melody: Melody

    init?(_ state: ExerciseState<DictationQuestion>, sessionId: String) {
        guard let info = AttemptInfo(state, sessionId: sessionId) else { return nil }
        self.info = info
        key = state.question.key
        melody = state.question.melody
    }
}

/// One answered rhythm question, as stored in practice history.
struct RhythmAttempt {
    let info: AttemptInfo<Rhythm>
    let rhythm: Rhythm
    let tempo: RhythmTempo

    init?(_ state: ExerciseState<RhythmQuestion>, sessionId: String) {
        guard let info = AttemptInfo(state, sessionId: sessionId) else { return nil }
        self.info = info
        rhythm = state.question.rhythm
        tempo = state.question.tempo
    }
}
