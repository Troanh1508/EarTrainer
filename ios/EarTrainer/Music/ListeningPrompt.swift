import Foundation

/**
 * What the listener hears, described musically rather than as audio: an ordered sequence of
 * moments, each sounding one or more pitches together.
 *
 * - Ascending melodic interval: `[[C4], [E4]]`
 * - Harmonic interval or block chord: `[[C4, E4, G4]]`
 * - Arpeggiated chord: `[[C4], [E4], [G4]]` with `.arpeggiated`
 * - Scale degree: a cadence as `context`, then `[[E4]]`
 * - Chord progression: one multi-pitch moment per chord with `.flowing`
 * - Melody: one single-pitch moment per note with `.melodic`, usually after a `context`
 * - Rhythm: one moment per note with exact `beatTiming` at a tempo, which overrides `articulation`
 *
 * Exact timing (durations, gaps) is decided by the audio layer.
 */
struct ListeningPrompt: Hashable {

    /**
     * Moment i starts `startBeats[i]` beats in and lasts `lengthBeats[i]` beats, at `bpm` beats
     * per minute. Rests are simply gaps between moments.
     */
    struct BeatTiming: Hashable {
        let bpm: Int
        let startBeats: [Double]
        let lengthBeats: [Double]

        init(bpm: Int, startBeats: [Double], lengthBeats: [Double]) {
            precondition(bpm > 0, "Tempo must be positive")
            precondition(startBeats.count == lengthBeats.count, "Every moment needs a start and a length")
            precondition(startBeats.allSatisfy { $0 >= 0 } && lengthBeats.allSatisfy { $0 > 0 }, "Starts must not be negative and lengths must be positive")
            self.bpm = bpm
            self.startBeats = startBeats
            self.lengthBeats = lengthBeats
        }
    }

    enum Articulation: Hashable {
        /// Each moment ends before the next one starts.
        case separate
        /// Moments start in quick succession and keep ringing until the last one has sounded.
        case arpeggiated
        /// Moments follow each other without gaps at a moderate pace, as in a chord progression.
        case flowing
        /// Single notes of a tune, back to back at a walking pace.
        case melodic
    }

    let moments: [[Pitch]]
    let articulation: Articulation
    /**
     * Moments that set the scene before the question itself, such as a cadence establishing a
     * key. Played briskly, then a pause separates them from `moments`.
     */
    let context: [[Pitch]]
    /// Exact placement in beats at a tempo; when set, `articulation` is ignored.
    let beatTiming: BeatTiming?

    init(moments: [[Pitch]], articulation: Articulation = .separate, context: [[Pitch]] = [], beatTiming: BeatTiming? = nil) {
        precondition(!moments.isEmpty, "A prompt needs at least one moment")
        precondition(moments.allSatisfy { !$0.isEmpty } && context.allSatisfy { !$0.isEmpty }, "Every moment must sound at least one pitch")
        precondition(beatTiming == nil || beatTiming!.startBeats.count == moments.count, "Beat timing must cover every moment")
        self.moments = moments
        self.articulation = articulation
        self.context = context
        self.beatTiming = beatTiming
    }

    /// Pitches played one after another.
    static func sequential(_ pitches: [Pitch]) -> ListeningPrompt { ListeningPrompt(moments: pitches.map { [$0] }) }

    /// Pitches played together as a single moment.
    static func simultaneous(_ pitches: [Pitch]) -> ListeningPrompt { ListeningPrompt(moments: [pitches]) }

    /// Pitches played from first to last, each held so the chord sounds whole at the end.
    static func arpeggiated(_ pitches: [Pitch]) -> ListeningPrompt {
        ListeningPrompt(moments: pitches.map { [$0] }, articulation: .arpeggiated)
    }
}
