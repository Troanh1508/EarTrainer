import Foundation

/// Musical timing used to turn a `ListeningPrompt` into sample-accurate note positions.
struct PlaybackTiming: Hashable {
    /// Length of a single-note moment, including its release.
    var noteSeconds = 0.9
    /// Length of a moment with several simultaneous pitches; longer so the harmony can settle.
    var chordSeconds = 1.6
    /// Silence between consecutive moments.
    var gapSeconds = 0.15
    /// Time between onsets in an arpeggio; short enough that the notes are heard as one chord.
    var arpeggioStepSeconds = 0.3
    /// Length of each chord in a flowing progression, played back to back.
    var progressionChordSeconds = 1.1
    /// Length of each note of a melody, played back to back; the release keeps notes distinct.
    var melodyNoteSeconds = 0.65
    /**
     * Share of its written length a beat-timed note sounds for. Slightly detached notes keep
     * repeated notes on the same pitch audibly separate, which rhythms depend on.
     */
    var beatTimedNoteFraction = 0.85
    /// Length of each context chord (e.g. in a key-setting cadence), played back to back.
    var contextChordSeconds = 0.7
    /// Silence between the context and the question, so the question stands apart.
    var contextPauseSeconds = 0.7
    /**
     * Silence before the first note. Some outputs swallow the first few milliseconds after
     * playback starts, which would otherwise clip the attack.
     */
    var leadInSeconds = 0.03
    /// Silence after the last note so the release is never cut off by the output stopping.
    var tailSeconds = 0.1
}

struct ScheduledNote: Hashable {
    let pitch: Pitch
    let startFrame: Int
    let frameCount: Int

    var endFrame: Int { startFrame + frameCount }
}

/**
 * Every note of a prompt placed on a frame timeline. Timing lives entirely in these frame
 * offsets; the renderer writes notes into one buffer, so the audio hardware's sample clock (not
 * task scheduling or UI timers) decides when each note sounds.
 */
struct NoteSchedule: Hashable {
    let sampleRate: Int
    let notes: [ScheduledNote]
    /// Frame at which the last note has fully decayed.
    let contentEndFrame: Int
    /// Buffer length including trailing silence.
    let totalFrames: Int
    /// Largest number of notes that sound at the same time, used for gain staging.
    let maxPolyphony: Int

    var durationSeconds: Double { Double(totalFrames) / Double(sampleRate) }

    static func from(_ prompt: ListeningPrompt, sampleRate: Int, timing: PlaybackTiming = PlaybackTiming()) -> NoteSchedule {
        precondition(sampleRate > 0, "Sample rate must be positive")
        func frames(_ seconds: Double) -> Int { Int((seconds * Double(sampleRate)).rounded()) }

        var notes: [ScheduledNote] = []
        var leadIn = frames(timing.leadInSeconds)
        if !prompt.context.isEmpty {
            let chordLength = frames(timing.contextChordSeconds)
            for (index, pitches) in prompt.context.enumerated() {
                for pitch in pitches { notes.append(ScheduledNote(pitch: pitch, startFrame: leadIn + index * chordLength, frameCount: chordLength)) }
            }
            leadIn += prompt.context.count * chordLength + frames(timing.contextPauseSeconds)
        }

        let contentEnd: Int
        if let beatTiming = prompt.beatTiming {
            let framesPerBeat = Double(sampleRate) * 60 / Double(beatTiming.bpm)
            for (index, pitches) in prompt.moments.enumerated() {
                let start = leadIn + Int((beatTiming.startBeats[index] * framesPerBeat).rounded())
                let length = max(1, Int((beatTiming.lengthBeats[index] * framesPerBeat * timing.beatTimedNoteFraction).rounded()))
                for pitch in pitches { notes.append(ScheduledNote(pitch: pitch, startFrame: start, frameCount: length)) }
            }
            contentEnd = notes.map(\.endFrame).max()!
        } else {
            switch prompt.articulation {
            case .separate:
                var cursor = leadIn
                for (index, pitches) in prompt.moments.enumerated() {
                    if index > 0 { cursor += frames(timing.gapSeconds) }
                    let length = frames(pitches.count > 1 ? timing.chordSeconds : timing.noteSeconds)
                    for pitch in pitches { notes.append(ScheduledNote(pitch: pitch, startFrame: cursor, frameCount: length)) }
                    cursor += length
                }
                contentEnd = cursor
            case .flowing, .melodic:
                let length = frames(prompt.articulation == .flowing ? timing.progressionChordSeconds : timing.melodyNoteSeconds)
                for (index, pitches) in prompt.moments.enumerated() {
                    for pitch in pitches { notes.append(ScheduledNote(pitch: pitch, startFrame: leadIn + index * length, frameCount: length)) }
                }
                contentEnd = leadIn + prompt.moments.count * length
            case .arpeggiated:
                // Every note holds until the shared end, like a sustain pedal, so the last
                // onset is followed by the whole chord for a full chord length.
                let step = frames(timing.arpeggioStepSeconds)
                let end = leadIn + step * (prompt.moments.count - 1) + frames(timing.chordSeconds)
                for (index, pitches) in prompt.moments.enumerated() {
                    let start = leadIn + step * index
                    for pitch in pitches { notes.append(ScheduledNote(pitch: pitch, startFrame: start, frameCount: end - start)) }
                }
                contentEnd = end
            }
        }
        return NoteSchedule(
            sampleRate: sampleRate,
            notes: notes,
            contentEndFrame: contentEnd,
            totalFrames: contentEnd + frames(timing.tailSeconds),
            maxPolyphony: maxOverlap(notes)
        )
    }

    /// Most notes sounding at any one frame: a sweep over note starts and ends.
    private static func maxOverlap(_ notes: [ScheduledNote]) -> Int {
        // At equal frames, ends (-1) sort before starts (+1): a note ending exactly where
        // another begins does not overlap it.
        let events = notes.flatMap { [($0.startFrame, 1), ($0.endFrame, -1)] }.sorted { ($0.0, $0.1) < ($1.0, $1.1) }
        var current = 0
        var most = 0
        for (_, change) in events {
            current += change
            most = max(most, current)
        }
        return max(most, 1)
    }
}
