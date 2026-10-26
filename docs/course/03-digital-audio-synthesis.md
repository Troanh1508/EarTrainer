# Module 3: Digital audio, from a number to a sound

[Course index](README.md) · Previous: [Module 2](02-pure-logic-and-tests.md) · Next: [Module 4: Scheduling sound in time](04-scheduling-sound.md)

## Why it matters

Every sound this app plays is computed by Kotlin code into an array of numbers before the
phone hears about it. Once you understand what those numbers mean, everything else in audio
programming becomes manageable: synthesis, mixing, sampling, latency, codecs. This is the core
theory module of the audio track. Take your time with it.

Before you start, spend 30 minutes with Bartosz Ciechanowski's interactive
[Sound](https://ciechanow.ski/sound/) article. It builds the intuition this module puts into
numbers.

## Concepts

### Sound is pressure over time

A speaker cone moves back and forth and pushes air. Your ear detects the pressure changes. A
**waveform** is a graph of that pressure (or the cone's position) over time. A **sine wave** is
the simplest waveform: one pure frequency.

- **Frequency** (Hz): cycles per second. Humans hear roughly 20 Hz – 20 kHz. Perceived as
  pitch.
- **Amplitude**: how far the wave swings. Perceived (roughly) as loudness.
- **Phase**: where in its cycle a wave is at a given moment.

### Sampling: turning a wave into numbers

A computer can't store a continuous curve, so it **samples** it: it measures the value at
regular instants.

- **Sample rate** `fs`: samples per second. 44,100 Hz (CD) and 48,000 Hz (most phones) are
  typical.
- **Frame**: one sample per channel at one instant. This app is **mono** (one channel), so a
  frame *is* a sample. In stereo, a frame holds two samples.
- **The Nyquist limit**: a sample rate `fs` can only represent frequencies below `fs / 2`. At
  48 kHz that's 24 kHz, comfortably above human hearing. That's why those rates were chosen.
- **Aliasing**: try to make a frequency above Nyquist and it *folds back* to a wrong, lower
  frequency. At 48 kHz, a 30 kHz tone comes out as 48 − 30 = 18 kHz. Aliased overtones aren't
  in tune with the note and sound harsh and metallic.

### Quantization: PCM and bit depth

Each sample is stored as an integer. **PCM 16-bit** (pulse-code modulation) means a signed
16-bit integer, `-32,768 … 32,767`. Internally the app mixes in `Double`s in the range
`-1.0 … 1.0` (called **full scale**) and converts at the end.

- More bits means a lower noise floor. Each bit adds about 6 dB, so 16 bits gives roughly
  96 dB of dynamic range.
- **Clipping**: a value beyond full scale can't be stored. If it's clamped to the maximum, the
  wave's peaks are flattened, which sounds like harsh distortion. Good audio code makes sure
  the mix never gets there. This is **gain staging**.
- **Decibels**: `dB = 20 · log10(amplitude)`. Full scale is 0 dBFS. An amplitude of 0.8 is
  about −1.9 dBFS, and 0.5 is about −6 dBFS. Halving the amplitude is −6 dB.

### Pitch to frequency: equal temperament

Western tuning splits the octave (a 2:1 frequency ratio) into 12 equal steps. Each semitone
multiplies frequency by `2^(1/12) ≈ 1.05946`. Anchored at A4 = 440 Hz:

```
f(midi) = 440 · 2^((midi − 69) / 12)
```

**Cents** measure small pitch differences: `1200 · log2(f2 / f1)`. A semitone is 100 cents.

### Timbre: harmonics

Real instruments don't produce pure sines. A note at fundamental `f` also contains
**harmonics** (overtones) at `2f`, `3f`, `4f`… with various amplitudes. The *mix* of harmonics
is the **timbre**: why a piano and a clarinet playing the same pitch sound different.

- Pure sine: harmonic amplitudes `[1]`. Flute-like, dull.
- Sawtooth: all harmonics at `1/k`. Bright, buzzy.
- Square: odd harmonics only at `1/k`. Hollow, clarinet-like.

**Why harmonics matter for ear training.** When two notes sound together, their harmonics
can sit close to each other and **beat** (a slow wobble at the difference frequency). In a
perfect fifth C4–G4, C4's 3rd harmonic (784.9 Hz) and G4's 2nd harmonic (784.0 Hz) are less
than 1 Hz apart, so they sound smooth. In a minor second, C4 (261.6 Hz) against C♯4
(277.2 Hz) gives ~16 Hz of roughness. With pure sines there are fewer of these cues, so
intervals are harder to judge. The app adds a few weak harmonics for that reason.

### Envelopes and clicks

An **envelope** shapes a note's amplitude over time. The classic model is **ADSR**: Attack,
Decay, Sustain, Release.

If a note starts or stops abruptly, with the waveform jumping from some value to 0, that
**discontinuity** contains energy at all frequencies. You hear it as a **click**. The fix is to
fade in and out, even over a few milliseconds. A **raised cosine** `0.5 − 0.5·cos(πx)` is a
smooth 0→1 ramp whose slope is zero at both ends, so it doesn't create a corner of its own.

### Mixing

Mixing several notes means **adding** their samples. Two notes at full amplitude can reach
2.0, which clips. Either scale each voice down or use a limiter. This app scales each voice by
`1 / maxPolyphony`, the most notes that ever sound at once in this prompt.

## Read the code

### The renderer contract

[audio/PcmRenderer.kt](../../app/src/main/java/com/troanhmolo/eartrainer/audio/PcmRenderer.kt)

- `fun interface PcmRenderer { fun render(schedule: NoteSchedule): ShortArray }`: anything
  that turns scheduled notes into 16-bit mono samples. A `fun interface` can be created from a
  lambda.
- [`raisedCosine`](../../app/src/main/java/com/troanhmolo/eartrainer/audio/PcmRenderer.kt#L13):
  the click-free ramp.
- [`toPcm16`](../../app/src/main/java/com/troanhmolo/eartrainer/audio/PcmRenderer.kt#L16):
  `Double` in −1..1 → `Short`, with `coerceIn` as a last-resort clamp.

### The synthesizer

[audio/ToneSynthesizer.kt](../../app/src/main/java/com/troanhmolo/eartrainer/audio/ToneSynthesizer.kt)

Read it with a calculator open.

- [`ToneEnvelope`, lines 14–33](../../app/src/main/java/com/troanhmolo/eartrainer/audio/ToneSynthesizer.kt#L14):
  12 ms raised-cosine attack, an exponential decay (time constant 0.45 s) toward a 0.55
  sustain, and an 80 ms raised-cosine release. Very short notes split their frames between
  attack and release (line 22–24). Why `coerceAtLeast(1)`?
- [Line 44](../../app/src/main/java/com/troanhmolo/eartrainer/audio/ToneSynthesizer.kt#L44):
  default harmonics `[1.0, 0.25, 0.1]`: a fundamental plus a weak 2nd and 3rd harmonic.
- [Line 61](../../app/src/main/java/com/troanhmolo/eartrainer/audio/ToneSynthesizer.kt#L61):
  **gain staging in one line.** `headroom / maxPolyphony / harmonics.sum()`. With all voices
  at peak and all harmonics in phase, the sum can't exceed `headroom`. Prove it to yourself.
- [Line 66–68](../../app/src/main/java/com/troanhmolo/eartrainer/audio/ToneSynthesizer.kt#L66):
  **anti-aliasing by construction.** Partials at or above Nyquist are dropped before
  rendering.
- [Line 71–80](../../app/src/main/java/com/troanhmolo/eartrainer/audio/ToneSynthesizer.kt#L71):
  the inner loop. `radiansPerFrame = 2π·f / fs` is how far the phase advances per sample. For
  A4 at 48 kHz that's about 0.0576 rad, or ~109 samples per cycle. Note
  `phase = radiansPerFrame * i`: the phase is *computed from the index*, not accumulated.
  That's fine for a constant pitch, but see the stretch exercise.

### The tests are an audio-theory exam

[ToneSynthesizerTest.kt](../../app/src/test/java/com/troanhmolo/eartrainer/audio/ToneSynthesizerTest.kt)

Each test checks one of the concepts above:

| Test | Concept |
|------|---------|
| `envelopeStartsAndEndsAtSilence` | Envelopes; monotonic attack |
| `noteBoundariesAreClickFree` | Discontinuities; true digital silence between notes |
| `noAbruptJumpsAnywhereInTheSignal` | Maximum slope of a sine: `2π·f/fs · amplitude` per sample |
| `pureToneHasExpectedFrequency` | Measuring frequency by counting zero crossings |
| `simultaneousNotesStayWithinHeadroomWithoutClipping` | Gain staging |

Read the comment in `noAbruptJumpsAnywhereInTheSignal` and redo its arithmetic.

## Lab tool: listen to your samples on your computer

You'll want to *hear* what you render without deploying to a phone. A WAV file is a 44-byte
header followed by raw PCM. Create
`app/src/test/java/com/troanhmolo/eartrainer/audio/WavLab.kt`:

```kotlin
package com.troanhmolo.eartrainer.audio

import com.troanhmolo.eartrainer.music.ListeningPrompt
import com.troanhmolo.eartrainer.music.Pitch
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Writes mono 16-bit PCM as a .wav file (little-endian RIFF). */
fun writeWav(file: File, pcm: ShortArray, sampleRate: Int) {
    val dataBytes = pcm.size * 2
    val buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + dataBytes)
    buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
    buffer.put("fmt ".toByteArray(Charsets.US_ASCII)).putInt(16) // size of the fmt chunk
    buffer.putShort(1)               // audio format 1 = integer PCM
    buffer.putShort(1)               // channels: mono
    buffer.putInt(sampleRate)
    buffer.putInt(sampleRate * 2)    // bytes per second
    buffer.putShort(2)               // bytes per frame
    buffer.putShort(16)              // bits per sample
    buffer.put("data".toByteArray(Charsets.US_ASCII)).putInt(dataBytes)
    for (sample in pcm) buffer.putShort(sample)
    file.parentFile?.mkdirs()
    file.writeBytes(buffer.array())
}

class WavLab {
    private val rate = 48_000

    @Test
    fun majorTriad() {
        val prompt = ListeningPrompt.simultaneous(listOf(Pitch(60), Pitch(64), Pitch(67)))
        val pcm = ToneSynthesizer().render(NoteSchedule.from(prompt, rate))
        writeWav(File("build/lab/major-triad.wav"), pcm, rate)
    }
}
```

Run it (`./gradlew :app:testDebugUnitTest --tests "*.WavLab"`) and open
`app/build/lab/major-triad.wav` in any audio player. For visual inspection, open it in
[Audacity](https://www.audacityteam.org/) (free): zoom right in to see individual samples,
and use *Analyze → Plot Spectrum* to see the harmonics.

## Exercises

Branch: `git switch -c learning/module-03`. Do every exercise by **listening** and by
**looking** in Audacity.

1. **Numbers by hand.** Without running code: how many frames is a 0.9 s note at 48 kHz? How
   many bytes is 3 s of mono 16-bit audio at 48 kHz? What's the frequency of MIDI 57? Of
   MIDI 81? How many cents is the equal-tempered major third (`2^(4/12)`) away from the pure
   ratio 5/4?
2. **Timbre.** In `WavLab`, render the same melody (C4 E4 G4 C5, `ListeningPrompt.sequential`)
   with harmonics `[1.0]`, `[1.0, 0.25, 0.1]` (the default), a sawtooth
   (`List(10) { 1.0 / (it + 1) }`) and a square (odd harmonics only, `1/k`; even ones `0.0`).
   Look at each in *Plot Spectrum*. Describe what you hear.
3. **Make a click.** Render with `ToneEnvelope(attackSeconds = 0.0001, releaseSeconds = 0.0001)`.
   Listen with headphones. Then zoom into the note start in Audacity. Then run
   `ToneSynthesizerTest` with that envelope as the default. Which tests catch it?
4. **Make aliasing.** Temporarily comment out the Nyquist filter (lines 67–68). Render
   `Pitch(96)` with a 20-harmonic sawtooth at `sampleRate = 22_050` (a low rate exaggerates
   the problem). Compare with the filter restored. Can you hear tones that aren't harmonics of
   the note? Find them in the spectrum.
5. **Make clipping.** Build a 6-note chord and render it with `headroom = 1.0`, then again with
   `voiceGain` changed to ignore `maxPolyphony`. Count the samples equal to `Short.MAX_VALUE`
   or `Short.MIN_VALUE`. Listen to both. Write a test `chordsNeverClip` that would catch this.
6. **Beating.** Render harmonic intervals (`ListeningPrompt.simultaneous`) of a perfect fifth,
   a major third and a minor second, first with `[1.0]` and then with `[1.0, 0.5, 0.4, 0.3]`.
   Which intervals change character most when harmonics are added? Explain using the
   harmonic arithmetic from the Concepts section.
7. **Stretch: vibrato.** Add an optional vibrato to `ToneSynthesizer`: the frequency wobbles by
   ±20 cents at 5 Hz. You'll find that `phase = radiansPerFrame * i` no longer works. Switch to
   **phase accumulation** (`phase += 2π·f(t)/fs` each frame). Explain why computing phase from
   the index fails when frequency varies. Then think about why accumulating a `Double` over
   millions of samples could drift, and how wrapping the phase into `[0, 2π)` fixes it.

## Checkpoint

1. What's the highest frequency a 44.1 kHz signal can represent? What happens to a partial
   above it?
2. Why does the synthesizer divide by `harmonics.sum()` and by `maxPolyphony`?
3. Why does a note that stops abruptly click, even if it's quiet?
4. A 440 Hz sine sampled at 48 kHz: how many samples per cycle, roughly?
5. What's −6 dB as an amplitude factor?
6. Why can't you just play pure sines for interval training?

<details>
<summary>Answers</summary>

1. 22,050 Hz (Nyquist = fs / 2). It aliases: it reflects back below Nyquist as an inharmonic
   frequency.
2. To guarantee the worst-case sum (every harmonic of every voice at its peak at the same
   instant) stays at or below `headroom`, so the mix never clips, whatever the chord.
3. The jump to zero is a discontinuity. Its spectrum is broadband, so energy appears at high
   frequencies the note didn't contain. Loudness isn't the issue. The sharp corner is.
4. 48,000 / 440 ≈ 109 samples.
5. About 0.5 (half amplitude).
6. You can, but intervals are harder to judge. Much of what we hear as consonance and
   dissonance comes from how the harmonics of the two notes line up or beat. Pure sines remove
   most of those cues.

</details>
