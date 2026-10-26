# Module 16: Capstone projects

[Course index](README.md) · Previous: [Module 15](15-testing-and-release.md)

## How to approach a capstone

Each capstone is a feature a real ear-training app could ship. They have no step-by-step
instructions on purpose. You decide the design, using the patterns you've studied. Work like
this:

1. **Write a one-page design first**: what the user sees, which layers change, the new
   types, the database changes, the tests you'll write. Revise it as you learn.
2. **Build bottom-up**: `music/` → `exercise/` → `data/` → `ui/`, with tests at each layer
   before moving up. The compiler will point at every `when` over `ExerciseKind` you need to
   extend. Let it.
3. **Commit small** on a feature branch. Each commit should build and pass tests.
4. **Review your own diff** before calling it done. Compare it with how the existing code does
   the same thing. Is your naming, commenting and error handling as consistent?

A reminder from the root README: the iOS app mirrors the Android logic, settings keys and
backup format. For learning, ignore iOS and keep your work on branches. If you ever want to
merge a capstone, the iOS side would need the same change.

---

## Capstone A: a new exercise, cadence identification (Android track)

**The feature.** A **cadence** is the chord progression that ends a musical phrase. Ear
training traditionally drills four:

| Cadence | Chords (major key) | Sounds like |
|---------|--------------------|-------------|
| Authentic | V → I | Final, resolved |
| Plagal | IV → I | "Amen" |
| Half | ? → V (ends on V) | A question, unfinished |
| Deceptive | V → vi | Expected home, landed somewhere else |

The listener hears a short progression ending in one of these and names the cadence.

**What you'll touch.** That's every layer, which is the point:

- `music/`: a `Cadence` enum describing each cadence as scale steps (study
  [Progression.kt](../../app/src/main/java/com/troanhmolo/eartrainer/music/Progression.kt) and
  [CommonProgressions.kt](../../app/src/main/java/com/troanhmolo/eartrainer/music/CommonProgressions.kt)
  for how chords in a key are voiced).
- `exercise/`: `CadenceQuestion : ExerciseQuestion<Cadence>` (its `prompt` is `FLOWING`
  chords, perhaps with a lead-in phrase), `CadenceSettings` with validation and toggles, a
  generator with weak-spot weighting, `CadenceAttempt`, and a new `ExerciseKind` entry.
- `data/`: `CadenceAttemptEntity`, a DAO, **database version 7** with `AutoMigration(6, 7)` and
  a migration test, the new methods on `PracticeHistory`, `RoomPracticeHistory` and
  `FakePracticeHistory`, a `WeakSpots` flow, `CadenceSettingsStore`, and the backup format (a
  new `BackupHistory` list **with a default**, plus validation in `HistoryValidation`).
- `ui/`: a practice route reusing `PracticeRoute`/`PracticeViewModel` (it's multiple choice, so
  most of the UI is free), a settings screen, routes in `EarTrainerNavHost`, a Home card and
  icon, and Progress/Mistakes support.

**Definition of done**

- [ ] `./gradlew test` and `connectedAndroidTest` pass, with new tests at every layer.
- [ ] Upgrading from a version-6 database keeps all existing history (migration test).
- [ ] A backup exported *before* your change imports *after* it.
- [ ] Settings changes start a new session. Weak cadences come up more often.
- [ ] The screen works with TalkBack, in dark mode, at 1.5× font size, and after process death.
- [ ] Previews exist for the new screens.

**Stretch.** Also play cadences in minor keys (watch the raised leading tone; see
`Key.cadence()`), and add a comparison drill for the most-confused pair.

---

## Capstone B: "Sing it back", a microphone pitch detector (audio track)

**The feature.** The app plays a note and names an interval ("sing a perfect fifth above").
The user sings, the app listens, shows a tuner needle, and scores the attempt as correct
within ±50 cents. This flips everything you've done: audio **in** instead of out, and
analysis instead of synthesis.

**New theory**

- **Pitch detection.** The simplest robust method for a single voice is **autocorrelation**:
  compare the signal with itself shifted by a lag τ. The lag where it matches best is the
  period, and `f = fs / τ`. You've already seen a version: the `estimateFrequency` /
  `correlation` functions in
  [SampledInstrumentsTest.kt](../../app/src/androidTest/java/com/troanhmolo/eartrainer/audio/SampledInstrumentsTest.kt#L69).
  For voices, the **YIN** algorithm improves on it (difference function, cumulative mean
  normalization, absolute threshold, parabolic interpolation). Read the original paper:
  A. de Cheveigné and H. Kawahara, "YIN, a fundamental frequency estimator for speech and
  music" (JASA, 2002).
- **Octave errors.** Autocorrelation also matches at 2τ, 3τ…, so detectors sometimes report
  half or double the frequency. YIN's normalization exists largely to fix this.
- **Windows and hops.** Analyze ~40 ms windows (long enough for two or more periods of your
  lowest note), hopping every ~10 ms. Explain the trade-off between latency and the lowest
  detectable pitch.
- **Frequency → note.** `midi = 69 + 12·log2(f / 440)`, and the cents error is
  `100 · (midi − round(midi))`. That's the inverse of `Pitch.frequencyOf`.
- **Voicing.** Silence and noise also produce a "best lag". Use the signal level (RMS in dB)
  and YIN's confidence to decide whether anyone is singing.
- **Smoothing.** Raw estimates jitter. A median filter over the last few frames gives a stable
  needle.

**New Android**

- The `RECORD_AUDIO` permission: declare it in the manifest, request it at runtime with
  `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`, explain why
  before asking, and handle "denied" and "don't ask again".
- `AudioRecord` (16-bit mono, 48 kHz or the device rate; size buffers with
  `getMinBufferSize`) read in a loop on a background coroutine, exposing results as a
  `StateFlow<PitchReading?>`. Stop recording when the screen stops (`ON_STOP`) and release
  it in `onCleared`.
- Don't record while your own prompt is playing (or the detector hears the speaker). Sequence
  play → listen in the ViewModel.

**Suggested path**

1. Pure Kotlin `PitchDetector` (YIN) with JVM tests on synthetic signals: sines, your
   synthesizer's harmonic tones, a sawtooth, noise, silence, and an octave-error trap (a
   strong second harmonic).
2. A test that feeds a WAV file you recorded of your own voice (use the Module 3 WAV
   knowledge in reverse).
3. A `MicrophonePitchSource` (Android) with an on-device test.
4. A tuner screen with a Canvas needle (Module 14) showing note name and cents.
5. The exercise: question generation, scoring tolerance, history, and weak spots.

**Definition of done**: detects your voice within ±10 cents on held notes, no octave errors
on your normal range in the tests, permission flows handled, no recording in the background,
and audio resources released on every path.

---

## Shorter capstones

### C: Practice reminders (background work)

A daily notification at a time the user picks, if they haven't hit their goal today. You'll
learn **WorkManager** (periodic work, constraints), **notifications** (channels, the
`POST_NOTIFICATIONS` runtime permission on Android 13+), a `PendingIntent` that opens the app
on the right screen, and reading Room from a worker. Test the "already reached goal → no
notification" logic as a pure function.

### D: A home screen widget

Show today's goal ring and streak on the home screen with **Jetpack Glance** (Compose-style
widgets). You'll learn how widgets update (they're not live Compose), how to read your data
from outside an Activity, and why `AppContainer` access needs care there.

### E: Shared code with Kotlin Multiplatform (architecture)

The iOS app re-implements `music/` and `exercise/` in Swift. Both are pure Kotlin here. Design
(and, if you're ambitious, prototype) a Kotlin Multiplatform `shared` module that both apps
use. Which packages move unchanged? What blocks the rest (`java.util.UUID`,
`System.currentTimeMillis`, `kotlin.random` is fine)? What would the Swift side look like
calling into it? Write the design as a document comparing the duplication cost today with
KMP's build and tooling cost.

---

## After the course

- Read the whole codebase again, top to bottom, including the iOS port in `ios/` if you're
  curious how the same architecture looks in UIKit.
- Read a large open-source app's architecture, for example
  [Now in Android](https://github.com/android/nowinandroid) (modularization, Hilt, offline-first
  sync) and compare its choices with this app's.
- For deeper audio: *The Audio Programming Book* (Boulanger & Lazzarini), Will Pirkle's
  *Designing Audio Effect Plugins in C++*, and Julius O. Smith's online books at
  [ccrma.stanford.edu/~jos](https://ccrma.stanford.edu/~jos/). Then try Oboe in C++ for
  real-time synthesis on Android.
