# Module 4: Scheduling sound in time

[Course index](README.md) · Previous: [Module 3](03-digital-audio-synthesis.md) · Next: [Module 5: Sampled instruments](05-sampled-instruments.md)

## Why it matters

Getting notes to sound at exactly the right moment is the hard part of audio programming. A
rhythm exercise with sloppy timing teaches the wrong rhythm. This module explains why the app
doesn't use timers to play notes, and how it places every note on a sample-accurate timeline
instead.

## Concepts

### Two clocks

An app has (at least) two notions of time:

1. **The CPU's clock**, as seen by your code: `delay(500)`, `Handler.postDelayed`, frame
   callbacks. Your code runs when the OS scheduler lets it. That can be late because of other
   threads, garbage collection, the main thread busy drawing, or the CPU in a power-saving
   state. Delays of several milliseconds, sometimes tens, are normal and unpredictable. This
   variation is called **jitter**.
2. **The audio clock**: the hardware consumes samples at exactly `fs` per second. At 48 kHz,
   one frame lasts about 20.8 µs.

Ears are sensitive to timing. In a rhythm, deviations of a few tens of milliseconds are
plainly audible, and trained musicians notice much less. So if note onsets depend on when your
code wakes up, rhythms will wobble.

### The solution: put time in the data

Decide the position of every note **in frames**, write all notes into **one buffer**, and hand
the buffer to the hardware. Note 2 starts at frame 43,200 because that's where its samples
are, not because a timer fired. Timing becomes a property of the data, and the audio clock
plays it out perfectly.

The trade-off is that the whole prompt must be computed before it starts (fine for prompts a
few seconds long, not for an endless stream). Module 7 covers the alternative, streaming.

### Musical time to sample time

```
framesPerBeat = fs · 60 / bpm
```

At 48 kHz and 90 BPM, a beat is 32,000 frames. An eighth note is half a beat: 16,000 frames.

### Polyphony

**Polyphony** is the number of notes sounding at the same time. Module 3 showed it's needed
for gain staging. Computing the *maximum* overlap among N intervals of time is a classic
algorithm problem: the **sweep line**. Turn each note into a +1 event at its start and a −1
event at its end, sort the events by time, and walk them keeping a running count.

## Read the code

### `PlaybackTiming`: musical decisions as numbers

[audio/NoteSchedule.kt, lines 8–44](../../app/src/main/java/com/troanhmolo/eartrainer/audio/NoteSchedule.kt#L8)

Every field has a comment saying *why* it has that value: chords last longer so the harmony
can settle, arpeggio steps are short enough to still be heard as one chord, beat-timed notes
sound for 85% of their length so repeated notes stay distinct. Two fields are workarounds for
real hardware:

- `leadInSeconds`: "Some devices swallow the first few milliseconds after a track starts."
- `tailSeconds`: so the release isn't cut off when output stops.

Production audio code is full of this kind of knowledge, learned from real devices. Note
how it's written down next to the number.

### `NoteSchedule.from`: building the timeline

[NoteSchedule.kt, lines 68–132](../../app/src/main/java/com/troanhmolo/eartrainer/audio/NoteSchedule.kt#L68)

Walk through it in order:

1. `frames(seconds)` converts seconds to rounded frame counts once, here, so every later
   calculation is in integers.
2. **Context** (lines 74–80): cadence chords back to back, then a pause. `leadIn` moves forward
   so the question starts after them.
3. **Beat timing** (lines 81–89): if the prompt has exact beats, each note's start is
   `leadIn + startBeat · framesPerBeat`. Note that `framesPerBeat` stays a `Double`, and
   each start is rounded on its own, so rounding errors don't accumulate across a bar.
4. **Articulations** (lines 90–124):
   - `SEPARATE`: a cursor walks forward. Chords get `chordSeconds`, single notes
     `noteSeconds`, with `gapSeconds` between moments.
   - `FLOWING` / `MELODIC`: back to back at a fixed length.
   - `ARPEGGIATED`: staggered onsets, but **every note ends at the same frame**, like a
     sustain pedal, so the chord rings whole at the end.
5. Lines 125–131: `totalFrames` adds the tail, and `maxPolyphony` comes from the sweep.

### The sweep line

[`maxOverlap`, lines 135–142](../../app/src/main/java/com/troanhmolo/eartrainer/audio/NoteSchedule.kt#L135)

`compareBy({ it.first }, { it.second })` sorts by frame, then by change: `-1` before `+1`.
That way a note ending exactly where another begins doesn't count as overlapping. Without
that tie-break, a melody played legato would report a polyphony of 2 and play at half volume.

### Where beat timing comes from

[exercise/RhythmQuestion.kt](../../app/src/main/java/com/troanhmolo/eartrainer/exercise/RhythmQuestion.kt#L40):
the rhythm prompt is a bar of count-in clicks (a high C7, a quarter beat long, so they read as
a metronome), then the rhythm's notes on C5, all placed with `BeatTiming`. Rests are simply
gaps, as the `BeatTiming` KDoc says.

### The tests

[NoteScheduleTest.kt](../../app/src/test/java/com/troanhmolo/eartrainer/audio/NoteScheduleTest.kt):
read `polyphonyCountsOnlyNotesThatActuallyOverlap` and `timingScalesWithSampleRate`. The
second is a good example of testing an **invariant across configurations** rather than one
magic number.

## Exercises

Branch: `git switch -c learning/module-04`.

1. **Schedule by hand.** For an ascending melodic interval C4→E4 at 48 kHz with default
   timing, write down each note's `startFrame` and `frameCount`, plus `contentEndFrame`,
   `totalFrames` and `maxPolyphony`. Then check your answer in a test.
2. **Beat arithmetic.** At 120 BPM and 48 kHz, where does the "and" of beat 3 fall (beat 2.5
   counting from 0), after a one-bar count-in? Include the lead-in.
3. **See jitter for yourself.** Write an instrumented test (in `app/src/androidTest/`) that runs
   `repeat(40) { delay(250); record(System.nanoTime()) }` inside `runBlocking` and logs the
   difference of each interval from 250 ms. Run it on a phone while you scroll something else.
   What's the worst error? Now imagine a metronome built that way.
4. **Add an articulation: `STRUMMED`.** A guitar strum plays chord notes from low to high
   about 15–25 ms apart, all ending together. Add `STRUMMED` to
   `ListeningPrompt.Articulation`, implement it in `NoteSchedule.from` (it's close to
   `ARPEGGIATED`), and write a test. Listen to it with the `WavLab` tool from Module 3. Then
   find a place in the chord exercise where it could be offered as a style.
5. **Break the tie-break.** Remove `{ it.second }` from the comparator in `maxOverlap`. Which
   tests fail? Render a melody and compare its loudness to before.
6. **Stretch: swing.** In swing, off-beat eighth notes are delayed (in "triplet swing", from
   beat 0.5 to 0.667). Add an optional `swing: Double` to `BeatTiming` (0 = straight, 1 = full
   triplet swing) and apply it to notes that start on an off-beat eighth. Which layer should
   own this: `music/`, `exercise/` or `audio/`? Argue for your choice.

## Checkpoint

1. Why can't `delay()` between notes produce reliable rhythm?
2. In this design, what decides *when* note 3 sounds?
3. How many frames is one beat at 70 BPM and 44.1 kHz?
4. What does the end-before-start tie-break in `maxOverlap` prevent?
5. What's the cost of rendering a whole prompt into one buffer up front?

<details>
<summary>Answers</summary>

1. Your code's wake-up time is subject to scheduler jitter (other threads, GC, a busy main
   thread, CPU power states), easily several milliseconds and sometimes much more. Rhythmic
   timing errors of that size are audible.
2. Its position in the PCM buffer. The hardware plays samples at a fixed rate, so frame N
   always sounds at N / fs seconds after the start.
3. 44,100 · 60 / 70 = 37,800 frames.
4. Counting back-to-back notes as overlapping. That would inflate `maxPolyphony` and make
   melodies quieter than they should be.
5. Latency before the first sound (render time) and memory for the whole buffer. It doesn't
   suit long or interactive audio, which needs streaming.

</details>
