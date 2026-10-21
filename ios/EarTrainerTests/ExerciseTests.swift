import XCTest
@testable import EarTrainer

final class ExerciseTests: XCTestCase {

    func testIntervalChoicesAreAContiguousRunContainingTheAnswer() {
        let generator = IntervalQuestionGenerator(settings: IntervalExerciseSettings(choiceCount: 4), random: RandomSource(seed: 1))
        for _ in 0..<200 {
            let question = generator.generate()
            XCTAssertEqual(question.choices.count, 4)
            XCTAssertTrue(question.choices.contains(question.interval))
            XCTAssertEqual(question.choices.map(\.semitones), Array(question.choices[0].semitones...question.choices[0].semitones + 3))
            XCTAssertTrue((48...84).contains(question.lowerPitch.midi) && (48...84).contains(question.upperPitch.midi))
        }
    }

    func testChordQuestionsStayInRangeAndOfferEveryEnabledQuality() {
        let settings = ChordExerciseSettings(qualities: [.major, .minor, .dominantSeventh], styles: [.block, .arpeggiated], includeInversions: true)
        let generator = ChordQuestionGenerator(settings: settings, random: RandomSource(seed: 2))
        for _ in 0..<200 {
            let question = generator.generate()
            XCTAssertEqual(question.choices, [.quality(.major), .quality(.minor), .quality(.dominantSeventh)])
            XCTAssertTrue(question.pitches.allSatisfy { (48...84).contains($0.midi) })
        }
    }

    func testProgressionsStartOnTheTonicAndOfferCloseAlternatives() {
        let generator = ProgressionQuestionGenerator(settings: ProgressionSettings(), random: RandomSource(seed: 3))
        for _ in 0..<100 {
            let question = generator.generate()
            XCTAssertEqual(question.choices.count, ProgressionSettings.choiceCount)
            XCTAssertTrue(question.choices.allSatisfy { $0.chords.first?.step == 1 && $0.chords.count == 4 })
            XCTAssertTrue(question.choices.contains(question.progression))
        }
        XCTAssertEqual(ProgressionQuestionGenerator.allProgressions(.major, .primary, 3).count, 2 * 2)
    }

    func testDictationMelodiesStartOnTheTonicAndNeverRepeatANote() {
        let generator = DictationQuestionGenerator(settings: DictationSettings(modes: [.major, .minor], length: 6), random: RandomSource(seed: 4))
        for _ in 0..<200 {
            let degrees = generator.generate().melody.degrees
            XCTAssertEqual(degrees.count, 6)
            XCTAssertEqual(degrees.first, .tonic)
            XCTAssertTrue(zip(degrees, degrees.dropFirst()).allSatisfy { $0 != $1 })
        }
    }

    func testDictationAcceptsOnlyCompleteEntriesInTheKey() {
        let question = DictationQuestion(key: Key(tonic: Pitch(60), mode: .major), melody: Melody([.tonic, .second, .third]), context: .cadence)
        XCTAssertTrue(question.accepts(Melody([.tonic, .third, .second])))
        XCTAssertFalse(question.accepts(Melody([.tonic, .second])))
        XCTAssertFalse(question.accepts(Melody([.tonic, .flatThird, .second])))
    }

    func testRhythmQuestionsHaveDistinctPleasantChoices() {
        let generator = RhythmQuestionGenerator(settings: RhythmSettings(vocabulary: .sixteenths, bars: 2), random: RandomSource(seed: 5))
        for _ in 0..<100 {
            let question = generator.generate()
            XCTAssertEqual(question.choices.count, RhythmSettings.choiceCount)
            XCTAssertEqual(Set(question.choices).count, question.choices.count)
            XCTAssertFalse(question.rhythm.cells[0].events[0].isRest)
            // Four count-in clicks, then one moment per sounded note.
            let notes = question.rhythm.timeline.filter { !$0.event.isRest }.count
            XCTAssertEqual(question.prompt.moments.count, 4 + notes)
        }
    }

    func testRhythmScoringPairsFiguresByBeat() {
        let played = Rhythm([.quarter, .eighths, .half])
        let chosen = Rhythm([.quarter, .quarter, .quarter, .quarter])
        XCTAssertEqual(rhythmCellResults(played, chosen).map(\.isCorrect), [true, false, false])
        XCTAssertEqual(rhythmCellPairs(played, chosen).map { $0.chosen }, [.quarter, .quarter, .quarter])
    }

    func testAlternatingComparisonBarsFillTheBar() {
        let rhythm = RhythmCellComparison.alternating(.half, .quarter)
        XCTAssertEqual(rhythm.cells, [.half, .quarter, .quarter])
        XCTAssertEqual(RhythmCellComparison.alternating(.quarter, .half).cells, [.quarter, .quarter, .half])
    }

    func testSessionStateIgnoresAnswersBeforeListeningAndDuplicates() {
        let question = IntervalQuestion(lowerPitch: Pitch(60), interval: .majorThird, style: .harmonic, choices: [.minorThird, .majorThird])
        let state = ExerciseEngine { question }.start()
        XCTAssertNil(state.submitAnswer(.majorThird, atMillis: 10), "Answers wait until the prompt has played")
        let heard = state.promptPlayed(atMillis: 100)
        let answered = heard.submitAnswer(.minorThird, atMillis: 1_600)!
        XCTAssertEqual(answered.score, SessionScore(answered: 1, correct: 0))
        XCTAssertEqual(answered.responseMillis, 1_500)
        XCTAssertNil(answered.submitAnswer(.majorThird, atMillis: 1_700))
    }

    func testWeakSpotWeightsFavorMistakes() {
        XCTAssertEqual(WeakSpotWeighting.weightOf(correct: 20, attempts: 20), 0.245, accuracy: 0.01)
        XCTAssertEqual(WeakSpotWeighting.weightOf(correct: 0, attempts: 5), 1.057, accuracy: 0.01)
        let weights = WeakSpotWeighting.weights([ItemResult(item: "a", isCorrect: false), ItemResult(item: "b", isCorrect: true)])
        XCTAssertGreaterThan(weights["a"]!, weights["b"]!)
    }

    func testSummaryListsWeakestItemsAndMostFrequentMistakesFirst() {
        let question = { (interval: Interval) in IntervalQuestion(lowerPitch: Pitch(60), interval: interval, style: .harmonic, choices: [.minorThird, .majorThird]) }
        let answered = [
            AnsweredQuestion(question: question(.majorThird), answer: .minorThird, isCorrect: false, playCount: 2, firstPlayedAtMillis: 0, answeredAtMillis: 2_000),
            AnsweredQuestion(question: question(.majorThird), answer: .minorThird, isCorrect: false, playCount: 1, firstPlayedAtMillis: 3_000, answeredAtMillis: 4_000),
            AnsweredQuestion(question: question(.minorThird), answer: .minorThird, isCorrect: true, playCount: 1, firstPlayedAtMillis: 5_000, answeredAtMillis: 8_000),
        ]
        let summary = PracticeSummary.from(answered, itemsOf: wholeAnswerItems { $0.displayName })
        XCTAssertEqual(summary.percent, 33)
        XCTAssertEqual(summary.durationMillis, 8_000)
        XCTAssertEqual(summary.averageResponseMillis, 2_000)
        XCTAssertEqual(summary.replays, 1)
        XCTAssertEqual(summary.tallies.map(\.label), ["Major third", "Minor third"])
        XCTAssertEqual(summary.mistakes, [SessionMistake(label: "Major third", chosenLabel: "Minor third", count: 2)])
    }
}
