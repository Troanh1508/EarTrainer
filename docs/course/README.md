# Learning Android, Compose and Audio with Ear Trainer

This course teaches native Android development (Kotlin, Jetpack Compose, coroutines, Room,
DataStore, Navigation) and the theory behind digital audio, using this repository as the
textbook. Nothing here is a toy example. Each concept is shown in code that ships in the app,
followed by exercises that change that code.

## How the course works

Every module has the same shape:

1. **Why it matters**: what problem the module solves in a real app.
2. **Concepts**: the theory, kept short. Read the linked official docs for depth.
3. **Read the code**: a guided tour of specific files and lines. Open them side by side with
   the lesson and read slowly. Most of the learning happens here.
4. **Exercises**: hands-on changes, from small to stretch. Do them on a branch.
5. **Checkpoint**: questions to answer from memory. Answers are folded underneath.

### Ground rules

- **Work on a branch.** `git switch -c learning/module-03` before each module. You can throw
  the branch away when you're done. Don't merge exercise work into `main`: the root README says
  the iOS app mirrors the Android logic, and exercises won't keep the two in step.
- **Run the tests constantly.** `./gradlew test` runs the JVM unit tests in seconds. Most
  exercises tell you which test file to extend.
- **Type, don't paste.** When a lesson shows a snippet, type it. Your fingers learn the syntax.
- **Break things on purpose.** Many exercises ask you to remove a safeguard (a fade, a
  `try/catch`, a key) and observe what goes wrong. Knowing why a line exists is the point.

## Prerequisites

- You can program in at least one language (variables, functions, classes, loops).
- Android Studio (latest stable), with an emulator or a real phone. **Use a real phone for
  the audio modules if you can.** Emulator audio works, but it adds latency and glitches you
  shouldn't mistake for bugs in your code.
- Headphones. Several audio exercises are about hearing small differences.

No music theory is assumed. Module 1 teaches the bits you need.

## Roadmap

The modules alternate between the Android track (A) and the audio track (♪) so that each one
uses what the previous ones taught.

| # | Module | Track | What you'll be able to do | Time |
|---|--------|-------|----------------------------|------|
| 0 | [Orientation and tooling](00-orientation.md) | A | Build, run, test; read Gradle files; navigate the codebase | 3 h |
| 1 | [Kotlin through the music model](01-kotlin-music-model.md) | A ♪ | Use value/data/enum classes, null safety, collections; MIDI and intervals | 5 h |
| 2 | [Pure logic, state machines and unit tests](02-pure-logic-and-tests.md) | A | Model behavior as immutable state; write deterministic tests | 5 h |
| 3 | [Digital audio: from a number to a sound](03-digital-audio-synthesis.md) | ♪ | Explain sampling, PCM, Nyquist, harmonics, envelopes, gain staging | 6 h |
| 4 | [Scheduling sound in time](04-scheduling-sound.md) | ♪ | Place notes sample-accurately; explain why timers can't do rhythm | 4 h |
| 5 | [Sampled instruments](05-sampled-instruments.md) | ♪ | Pitch-shift and resample recordings; compare interpolation methods | 5 h |
| 6 | [Coroutines and Flow](06-coroutines-and-flow.md) | A | Use structured concurrency, cancellation, StateFlow, operators | 7 h |
| 7 | [Android audio APIs](07-android-audio-apis.md) | A ♪ | Use AudioTrack, audio focus, MediaExtractor/MediaCodec | 6 h |
| 8 | [Compose fundamentals](08-compose-basics.md) | A | Build screens from composables, layouts, Material 3, themes, previews | 6 h |
| 9 | [Compose state, effects and lifecycle](09-compose-state-and-effects.md) | A | Explain recomposition; use remember/rememberSaveable and effects correctly | 6 h |
| 10 | [ViewModels and app architecture](10-viewmodels-and-architecture.md) | A | Structure an app in layers; write UI state; wire dependencies by hand | 6 h |
| 11 | [Navigation](11-navigation.md) | A | Build a NavHost, pass arguments, scope ViewModels to destinations | 4 h |
| 12 | [Persistence: DataStore and Room](12-persistence.md) | A | Store settings and history; write SQL queries and migrations | 7 h |
| 13 | [Files, serialization and backups](13-files-and-backup.md) | A | Use the Storage Access Framework and kotlinx.serialization safely | 4 h |
| 14 | [Custom drawing with Canvas](14-custom-drawing.md) | A ♪ | Draw charts, notation and a live waveform | 5 h |
| 15 | [Testing strategy and release builds](15-testing-and-release.md) | A | Choose test types; write Compose UI tests; build a release | 5 h |
| 16 | [Capstone projects](16-capstones.md) | A ♪ | Add a new exercise end to end; build a microphone pitch detector | 15–30 h |

Roughly 100 hours in total. At 5–8 hours a week that's three to five months. Don't rush
modules 3, 6 and 9: they are the foundations everything else rests on.

## The app in one picture

```
             ┌───────────────────────────── ui/ ─────────────────────────────┐
  Compose    │ HomeScreen  PracticeScreen  ProgressScreen  SettingsScreens ...│
  screens    │      ▲  events ▼                                              │
             │ ViewModels (PracticeViewModel, HomeViewModel, ...)             │
             └───────▲────────────────────▲───────────────────▲─────────────┘
                     │ StateFlow          │ Flow              │ suspend play()
             ┌───────┴──────┐   ┌─────────┴─────────┐   ┌─────┴──────────────┐
             │  exercise/   │   │      data/         │   │      audio/        │
             │ questions,   │   │ Room history,      │   │ NoteSchedule →     │
             │ generators,  │   │ DataStore settings,│   │ PcmRenderer →      │
             │ state machine│   │ backups            │   │ AudioTrack         │
             └───────▲──────┘   └────────────────────┘   └─────▲──────────────┘
                     │                                          │
             ┌───────┴──────────────────────────────────────────┴──────────────┐
             │ music/  Pitch, Interval, Chord, Key, ListeningPrompt (pure Kotlin)│
             └──────────────────────────────────────────────────────────────────┘
       AppContainer (hand-wired dependencies) created once in EarTrainerApplication
```

The lower layers know nothing about the upper ones. `music/` and `exercise/` don't import
anything from Android, which is why they can be tested on your computer in milliseconds. Keep
this picture in mind. Module 10 comes back to it.

## Reference shelf

Bookmark these. Lessons link to specific pages, but these are the homes:

- Kotlin: [language docs](https://kotlinlang.org/docs/home.html),
  [Kotlin Koans](https://play.kotlinlang.org/koans) (do these alongside Module 1)
- Android: [Android Basics with Compose](https://developer.android.com/courses/android-basics-compose/course),
  [Guide to app architecture](https://developer.android.com/topic/architecture)
- Compose: [Compose docs](https://developer.android.com/develop/ui/compose),
  [Thinking in Compose](https://developer.android.com/develop/ui/compose/mental-model)
- Audio and DSP: Steven W. Smith, *The Scientist and Engineer's Guide to Digital Signal
  Processing* (free at [dspguide.com](https://www.dspguide.com/)); Bartosz Ciechanowski's
  interactive [Sound](https://ciechanow.ski/sound/) article; Julius O. Smith's
  [online books](https://ccrma.stanford.edu/~jos/) for going deeper
- Real-world sample apps: [Now in Android](https://github.com/android/nowinandroid),
  [Compose samples](https://github.com/android/compose-samples)

When you've finished the course, read the codebase once more from top to bottom. You'll
see design decisions in it that you couldn't see on the first pass.
