import XCTest
@testable import EarTrainer

final class MusicTests: XCTestCase {

    func testPitchNamesAndFrequencies() {
        XCTAssertEqual(Pitch(60).name, "C4")
        XCTAssertEqual(Pitch(66).name, "F♯4")
        XCTAssertEqual(Pitch(69).frequencyHz, 440, accuracy: 1e-9)
        XCTAssertEqual(Pitch(81).frequencyHz, 880, accuracy: 1e-9)
    }

    func testIntervalsAreOneSemitoneApartUpToAnOctave() {
        XCTAssertEqual(Interval.allCases.map(\.semitones), Array(1...12))
        XCTAssertEqual(Interval.between(Pitch(64), Pitch(60)), .majorThird)
        XCTAssertNil(Interval.between(Pitch(60), Pitch(80)))
    }

    func testInversionsRaiseTheLowestTonesByAnOctave() {
        // C major: E-G-C and G-C-E.
        XCTAssertEqual(ChordQuality.major.offsetsAboveBass(.first), [0, 3, 8])
        XCTAssertEqual(ChordQuality.major.offsetsAboveBass(.second), [0, 5, 9])
        // Cmaj7 third inversion: B-C-E-G.
        XCTAssertEqual(ChordQuality.majorSeventh.offsetsAboveBass(.third), [0, 1, 5, 8])
        // Dominant 7th second inversion: G-B♭-C-E.
        XCTAssertEqual(ChordQuality.dominantSeventh.offsetsAboveBass(.second), [0, 3, 5, 9])
    }

    func testEveryInversionKeepsItsPitchClassesInCloseAscendingPosition() {
        for quality in ChordQuality.allCases {
            let pitchClasses = Set(quality.semitonesAboveRoot.map { $0 % 12 })
            for inversion in quality.inversions {
                let offsets = quality.offsetsAboveBass(inversion)
                let root = quality.rootOffsetAboveBass(inversion)
                XCTAssertEqual(Set(offsets.map { positiveMod($0 - root, 12) }), pitchClasses, "\(quality) \(inversion)")
                XCTAssertTrue(zip(offsets, offsets.dropFirst()).allSatisfy { $0 < $1 })
            }
        }
    }

    func testKeysUseConventionalSpelling() {
        XCTAssertEqual(Key(tonic: Pitch(63), mode: .major).name, "E♭ major")
        XCTAssertEqual(Key(tonic: Pitch(61), mode: .minor).name, "C♯ minor")
        XCTAssertEqual(Key(tonic: Pitch(63), mode: .major).noteName(Pitch(68)), "A♭")
        XCTAssertEqual(DiatonicChord(.major, 4).symbol(in: Key(tonic: Pitch(63), mode: .major)), "A♭")
        XCTAssertEqual(DiatonicChord(.major, 6).symbol(in: Key(tonic: Pitch(60), mode: .major)), "Am")
    }

    func testProgressionsAreVoicedWithTheRootBelowTheTonic() {
        let key = Key(tonic: Pitch(60), mode: .major)
        let progression = CommonProgressions.parse(.major, "I vi IV V")
        XCTAssertEqual(progression.label, "I – vi – IV – V")
        XCTAssertEqual(progression.symbols(in: key), "C – Am – F – G")
        for (chord, voicing) in zip(progression.chords, progression.voiced(in: key)) {
            XCTAssertEqual(voicing[0].midi % 12, (60 + chord.rootSemitonesAboveTonic) % 12)
            XCTAssertTrue(voicing[0].midi < 60 - 4)
            XCTAssertTrue(voicing.dropFirst().allSatisfy { (57...68).contains($0.midi) })
        }
    }

    func testRhythmsMustFillWholeBarsWithAlignedFigures() {
        XCTAssertNotNil(Rhythm.validated([.quarter, .eighths, .half]))
        XCTAssertNil(Rhythm.validated([.quarter, .half, .quarter]), "A half note cannot start on beat 2")
        XCTAssertNil(Rhythm.validated([.quarter, .quarter]))
        let rhythm = Rhythm([.quarter, .eighths, .half, .sixteenths, .quarterRest, .half])
        XCTAssertEqual(rhythm.bars, 2)
        XCTAssertEqual(rhythm.cellsByBar, [[.quarter, .eighths, .half], [.sixteenths, .quarterRest, .half]])
        XCTAssertEqual(rhythm.description, "quarter, two eighths, half, four sixteenths, quarter rest, half")
    }
}
