import XCTest
@testable import EarTrainer

final class AudioTests: XCTestCase {
    // 1 kHz makes one frame equal one millisecond, so expectations read directly as timings.
    private let sampleRate = 1_000
    private let timing = PlaybackTiming()

    func testSequentialNotesAreSeparatedByTheGap() {
        let schedule = NoteSchedule.from(.sequential([Pitch(60), Pitch(64)]), sampleRate: sampleRate, timing: timing)
        XCTAssertEqual(schedule.notes, [
            ScheduledNote(pitch: Pitch(60), startFrame: 30, frameCount: 900),
            ScheduledNote(pitch: Pitch(64), startFrame: 1_080, frameCount: 900),
        ])
        XCTAssertEqual(schedule.contentEndFrame, 1_980)
        XCTAssertEqual(schedule.totalFrames, 2_080)
        XCTAssertEqual(schedule.maxPolyphony, 1)
    }

    func testArpeggioStaggersOnsetsAndHoldsEveryNoteToTheSameEnd() {
        let schedule = NoteSchedule.from(.arpeggiated([Pitch(60), Pitch(64), Pitch(67), Pitch(71)]), sampleRate: sampleRate, timing: timing)
        XCTAssertEqual(schedule.notes.map(\.startFrame), [30, 330, 630, 930])
        XCTAssertEqual(schedule.notes.map(\.endFrame), Array(repeating: 930 + 1_600, count: 4))
        XCTAssertEqual(schedule.maxPolyphony, 4)
    }

    func testContextPlaysBackToBackThenPausesBeforeTheQuestion() {
        var withContext = timing
        withContext.contextPauseSeconds = 0.5
        let prompt = ListeningPrompt(moments: [[Pitch(64)]], context: [[Pitch(48), Pitch(60), Pitch(64)], [Pitch(53), Pitch(60), Pitch(65)]])
        let schedule = NoteSchedule.from(prompt, sampleRate: sampleRate, timing: withContext)
        XCTAssertEqual(schedule.notes.prefix(6).map(\.startFrame), [30, 30, 30, 730, 730, 730])
        XCTAssertEqual(schedule.notes.last, ScheduledNote(pitch: Pitch(64), startFrame: 1_930, frameCount: 900))
    }

    func testBeatTimedNotesFollowTheTempo() {
        let prompt = RhythmQuestion(rhythm: Rhythm([.half, .half]), tempo: .fast, choices: [Rhythm([.half, .half])]).prompt
        let schedule = NoteSchedule.from(prompt, sampleRate: sampleRate, timing: timing)
        // 120 BPM is 500 ms a beat: four clicks, then notes on beats 5 and 7.
        XCTAssertEqual(schedule.notes.map(\.startFrame), [30, 530, 1_030, 1_530, 2_030, 3_030])
    }

    func testSynthesizedChordsNeverClip() {
        let schedule = NoteSchedule.from(.simultaneous([Pitch(48), Pitch(52), Pitch(55), Pitch(60)]), sampleRate: 8_000)
        let pcm = ToneSynthesizer().render(schedule)
        XCTAssertEqual(pcm.count, schedule.totalFrames)
        XCTAssertLessThanOrEqual(pcm.map(abs).max()!, 0.8001)
        XCTAssertEqual(pcm.first!, 0, accuracy: 1e-6)
    }

    func testBundledRecordingsDecodeAndRenderEveryPrompt() throws {
        let bank = try SampleLoader.load(.piano, bundle: Bundle(for: AppContainer.self))
        XCTAssertEqual(bank.rootPitches.count, Instrument.piano.recordings.count)
        let renderer = SampledInstrumentRenderer(bank: bank)
        let key = Key(tonic: Pitch(60), mode: .major)
        let schedule = NoteSchedule.from(ScaleDegreeQuestion(key: key, degree: .third, context: .cadence, choices: Mode.major.diatonicDegrees).prompt, sampleRate: 48_000)
        let pcm = renderer.render(schedule)
        XCTAssertEqual(pcm.count, schedule.totalFrames)
        XCTAssertGreaterThan(pcm.map(abs).max()!, 0.1)
        XCTAssertLessThanOrEqual(pcm.map(abs).max()!, 1)
    }
}
