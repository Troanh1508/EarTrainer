# Module 5: Sampled instruments

[Course index](README.md) · Previous: [Module 4](04-scheduling-sound.md) · Next: [Module 6: Coroutines and Flow](06-coroutines-and-flow.md)

## Why it matters

Synthesized tones are clean but artificial. Real instruments are hard to synthesize
convincingly, so most music software **plays back recordings** instead. This module covers
how one recording can play many pitches, what that costs in sound quality, and how
interpolation, resampling and normalization keep it sounding good.

## Concepts

### Multisampling

A **sampler** stores recordings of real notes and plays them back. Recording every note of a
piano at every loudness is expensive, so instruments are **multisampled**: notes are recorded
every few semitones, and each recording is marked with its **root key**, the MIDI pitch it was
recorded at. The piano here has 26 recordings about a minor third (3 semitones) apart. The
guitar has 20.

### Pitch shifting by changing playback speed

Play a recording faster and it sounds higher. To raise a sample by `n` semitones, read it
`2^(n/12)` times faster: advance `step = 2^(n/12)` input samples per output sample. Side
effects:

- **Duration changes.** Up an octave, a note decays twice as fast.
- **Timbre changes.** The instrument body's resonances (**formants**) move with the pitch, so
  big shifts sound like a different, smaller or larger instrument (the "chipmunk" effect).
  That's why recordings are spaced closely: the guitar's map keeps every note within 1.5
  semitones of a root.

### Sample-rate conversion

The recordings are 44.1 kHz but the phone might output 48 kHz. Playing 44.1 kHz data at
48 kHz without converting would raise the pitch by `48/44.1`, about 1.5 semitones. The fix
is the same trick: multiply the step by `sourceRate / outputRate`. The app folds both
corrections into **one step**:

```
step = 2^((targetMidi − rootMidi) / 12) · sampleRate_recording / sampleRate_output
```

### Interpolation: reading between samples

With a fractional step, read positions land between stored samples (position 103.37). You
must estimate the value there:

| Method | Points used | Character |
|--------|-------------|-----------|
| Nearest neighbor | 1 | Cheap; adds audible distortion and noise |
| Linear | 2 | Smoother, but dulls high frequencies and still adds some error |
| Cubic (e.g. Catmull-Rom) | 4 | Keeps much more high-frequency detail; a good default |
| Windowed sinc | 8–64+ | The "correct" band-limited answer; used by pro resamplers |

**A subtle point about aliasing.** Reading *faster* (shifting up) can move content in the
recording above the output's Nyquist limit, where it aliases (Module 3). Professional
resamplers low-pass filter first. This app shifts by at most about 1.5 semitones (guitar) or
3 semitones (piano) over natural recordings with little energy near Nyquist, so the error is
inaudible. The trade-off is reasonable here. It wouldn't be for shifts of an octave or more.

### Normalization

Recordings come in at different levels. Dividing each by its **peak** absolute value levels
them, so every note plays at the same loudness. That's crude compared to loudness-based
normalization (LUFS), but simple and predictable.

## Read the code

### The instrument catalog

[audio/Instrument.kt](../../app/src/main/java/com/troanhmolo/eartrainer/audio/Instrument.kt):
each `Instrument` names a folder in assets and maps file names to root MIDI notes. File names
use `s` for sharp (`Fs4` = F♯4). Both sets come from FreePats, under CC0. The licenses are in
[shared/assets/instruments/](../../shared/assets/instruments).

### Samples, banks and the renderer

[audio/SampledInstrument.kt](../../app/src/main/java/com/troanhmolo/eartrainer/audio/SampledInstrument.kt)

- [`InstrumentSample`, line 9](../../app/src/main/java/com/troanhmolo/eartrainer/audio/SampledInstrument.kt#L9):
  PCM plus root and rate. [`peak`](../../app/src/main/java/com/troanhmolo/eartrainer/audio/SampledInstrument.kt#L16)
  is computed once at construction.
- [`valueAt`, lines 23–34](../../app/src/main/java/com/troanhmolo/eartrainer/audio/SampledInstrument.kt#L23):
  4-point Catmull-Rom. Given neighbors `y0, y1, y2, y3` and a fraction `t` between `y1` and
  `y2`, it evaluates a cubic that passes through `y1` and `y2` with slopes estimated from the
  outer points. The last line is **Horner's method** for evaluating a polynomial with few
  multiplications. `lastReadablePosition` leaves room for the two samples ahead.
- [`SampleBank.sampleFor`, line 52](../../app/src/main/java/com/troanhmolo/eartrainer/audio/SampledInstrument.kt#L52):
  nearest root, with ties going to the lower recording.
- [`SampledInstrumentRenderer.render`, lines 73–100](../../app/src/main/java/com/troanhmolo/eartrainer/audio/SampledInstrument.kt#L73):
  - Line 78: the same gain staging as the synthesizer.
  - Line 83: the combined pitch-shift and rate-conversion `step`.
  - Lines 86–87: a note can't play longer than its recording lasts at this step. `length` is
    the shortest of the scheduled length, the playable length, and the buffer space left.
  - Lines 89–96: a 2 ms fade-in (the recording already starts at the hammer strike) and a
    250 ms raised-cosine release that acts like a piano damper.

### The tests

[SampledInstrumentTest.kt](../../app/src/test/java/com/troanhmolo/eartrainer/audio/SampledInstrumentTest.kt):
note [`sineSample`, line 21](../../app/src/test/java/com/troanhmolo/eartrainer/audio/SampledInstrumentTest.kt#L21).
The tests don't need real recordings. They build **synthetic** samples whose frequency is
known exactly, then measure the output frequency. Testing audio against signals with known
properties is a technique you'll reuse.

The on-device counterpart,
[SampledInstrumentsTest.kt](../../app/src/androidTest/java/com/troanhmolo/eartrainer/audio/SampledInstrumentsTest.kt),
decodes the real recordings and checks each one is at its mapped pitch using
**autocorrelation** (line 69 onward). Keep that function in mind for the pitch-detection
capstone.

## Exercises

Branch: `git switch -c learning/module-05`. Keep using `WavLab` from Module 3.

1. **Step by hand.** A note at MIDI 62 is played from the C4 (MIDI 60) recording, which is
   44.1 kHz, on a 48 kHz output. Compute `step`. Is it more or less than 1? What would
   happen to the pitch if the code forgot the `sampleRate / outputRate` factor?
2. **Memory budget.** Each recording is decoded to at most 3 s of mono 16-bit at 44.1 kHz.
   How big is the decoded piano bank? Compare with the size of the FLAC files on disk
   (`shared/assets/instruments/`). Why are they so different?
3. **Hear the chipmunk.** In `WavLab`, build a synthetic "recording" (a sawtooth with an
   exponential decay, rooted at MIDI 60) and a `SampleBank` containing only that one sample.
   Render a scale from C3 to C6 with `SampledInstrumentRenderer`. Listen to how the decay rate
   and brightness change with distance from the root. Which notes end early, and why
   (`playable`)?
4. **Compare interpolators.** Add a parameter choosing nearest, linear or Catmull-Rom in
   `valueAt`. Render a bright synthetic sample shifted up 5 semitones with each. Compare them
   in Audacity's *Plot Spectrum*: look for energy that isn't at a harmonic of the note.
5. **Sparse sampling on a real device.** In a throwaway branch, cut the piano map in
   `Instrument.kt` down to one recording per octave (C1, C2, … C7). Run the app and play
   intervals around F♯4. Describe what you hear. What did you learn about why recordings are
   spaced as they are?
6. **Ties.** `sampleFor` prefers the lower recording on a tie. Using the aliasing discussion
   above, explain why shifting a sample *up* is riskier than shifting *down*. Is "prefer
   lower" the right tie-break?
7. **Stretch: anti-aliased upward shifts.** When `step > 1`, low-pass filter the sample before
   reading (a simple start: average `step`-wide neighborhoods, or apply a one-pole low-pass;
   better: a windowed-sinc resampler). Measure the improvement with exercise 4's method.

## Checkpoint

1. Why is one recording per octave not enough for a good-sounding piano?
2. What two corrections does `step` combine?
3. Why does the renderer divide by `sample.peak`?
4. Why must `valueAt` stop two frames before the end of the recording?
5. What's the risk of shifting a sample up by a large interval, and how do pro resamplers
   handle it?

<details>
<summary>Answers</summary>

1. Shifting by up to 6 semitones changes duration and moves the instrument's formants
   audibly, so notes far from a root sound unnatural.
2. The pitch shift (`2^(Δ/12)`) and the sample-rate conversion
   (`recordingRate / outputRate`).
3. To level recordings made at different loudnesses, so every note plays at the same level
   and the gain staging can guarantee no clipping.
4. Catmull-Rom reads `index + 1` and `index + 2`. Reading past the end would throw.
5. High-frequency content in the recording can move above the output's Nyquist limit and
   alias. Professional resamplers band-limit (low-pass filter) as part of resampling, for
   example with windowed-sinc filters.

</details>
