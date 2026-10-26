# Module 1: Kotlin through the music model

[Course index](README.md) · Previous: [Module 0](00-orientation.md) · Next: [Module 2: Pure logic, state machines and unit tests](02-pure-logic-and-tests.md)

## Why it matters

Kotlin is concise, but much of its power is in a handful of features: data classes, enums
with properties, null safety, `require`, and the collections library. The `music/` package
uses all of them, and it's also the most self-contained part of the app. Learn the language
here, plus just enough music theory to understand everything that follows.

Do [Kotlin Koans](https://play.kotlinlang.org/koans) alongside this module (the
"Introduction", "Classes" and "Collections" sections).

## Concepts: music theory you need

- **Pitch** is how high or low a note sounds. Computers number pitches with **MIDI note
  numbers**: middle C is 60, each step of 1 is a **semitone** (one piano key, white or black),
  and 12 semitones make an **octave**. C4 = 60, C5 = 72, A4 = 69.
- An **interval** is the distance between two pitches, in semitones. Each size has a name:
  3 = minor third, 4 = major third, 7 = perfect fifth, 12 = octave.
  - **Melodic**: the two notes are played one after the other (ascending or descending).
  - **Harmonic**: the two notes are played together.
- A **chord** is three or more pitches sounding together. Its **quality** (major, minor,
  diminished…) is its pattern of intervals above the **root**. A major triad is root + 4 + 7
  semitones (C–E–G).
- An **inversion** puts a different chord tone in the bass. C–E–G in first inversion is
  E–G–C.
- A **key** has a **tonic** (home note) and a **mode** (major or minor). **Scale degrees** name
  notes relative to the tonic: in C major, E is degree 3. That's why the app can ask "which
  degree was that?" in any key.
- A **cadence** is a short chord sequence that establishes a key. The app plays I–IV–V–I
  before scale-degree questions so your ear knows where "home" is.

## Concepts: Kotlin you'll meet

| Feature | Where in this module |
|---------|----------------------|
| `value class` (`@JvmInline`) | `Pitch` |
| `init { require(...) }` invariants | `Pitch`, `ListeningPrompt`, `Rhythm` |
| Computed properties (`val x get() = ...`) | `Pitch.frequencyHz`, `Pitch.name` |
| `companion object`, `const val` | `Pitch.MIDDLE_C`, `Interval.fromSemitones` |
| `enum class` with constructor properties | `Interval`, `ChordQuality`, `ScaleDegree`, `RhythmCell` |
| `entries`, `associateBy` | `Interval` companion |
| Nullable types `T?`, `?:`, `?.let` | `Interval.fromSemitones` returns `Interval?` |
| `data class` and `copy` | `ListeningPrompt`, `Key`, `Melody`, `Rhythm` |
| Nested classes and enums | `ListeningPrompt.BeatTiming`, `ListeningPrompt.Articulation` |
| Default arguments, named arguments | `ListeningPrompt(...)` |
| Collection operations | `drop`, `take`, `map`, `joinToString`, `runningFold`, `zip` |
| Function references (`key::pitchOf`) | `Melody.pitchesIn` |
| `private` top-level functions | `note()` and `rest()` in `Rhythm.kt` |

## Read the code

### `Pitch`: a value class with invariants

[music/Pitch.kt](../../app/src/main/java/com/troanhmolo/eartrainer/music/Pitch.kt)

- [Line 10–11](../../app/src/main/java/com/troanhmolo/eartrainer/music/Pitch.kt#L10):
  `@JvmInline value class Pitch(val midi: Int)`. At runtime this is just an `Int`, so there's
  no object allocation, but the compiler won't let you pass a bare `Int` where a `Pitch` is
  expected. This is called a **zero-cost wrapper type**. Why it helps: a function taking
  `(Pitch, Int)` can't have its arguments swapped by mistake.
- [Line 13–15](../../app/src/main/java/com/troanhmolo/eartrainer/music/Pitch.kt#L13): `init`
  runs on every construction. `require` throws `IllegalArgumentException` if the condition is
  false. **Invalid pitches can't exist.** This idea comes back everywhere in the codebase:
  make illegal states unrepresentable.
- [Line 17–19](../../app/src/main/java/com/troanhmolo/eartrainer/music/Pitch.kt#L17):
  `frequencyHz` is a computed property: no storage, recalculated on each access.
- [Line 49–50](../../app/src/main/java/com/troanhmolo/eartrainer/music/Pitch.kt#L49): the
  equal-temperament formula, `440 · 2^((midi − 69) / 12)`. You'll need it in Module 3.
- `implements Comparable<Pitch>`: makes `pitchA < pitchB`, `maxOf`, `sorted()` work.

### `Interval`: an enum as the single source of truth

[music/Interval.kt](../../app/src/main/java/com/troanhmolo/eartrainer/music/Interval.kt)

- Each entry carries data: `MAJOR_THIRD(4, "Major third", "M3")`. In Java you'd need a lookup
  table. In Kotlin the enum *is* the table.
- [Line 28–34](../../app/src/main/java/com/troanhmolo/eartrainer/music/Interval.kt#L28): the
  companion builds a map once (`entries.associateBy { it.semitones }`) and `fromSemitones`
  returns `Interval?`. A 13-semitone gap isn't a simple interval, and the return type says so.
  The caller is forced to handle `null`.

### `ChordQuality`: collection operations doing real work

[music/Chord.kt](../../app/src/main/java/com/troanhmolo/eartrainer/music/Chord.kt#L34)

Read `offsetsAboveBass` slowly. For a major triad `[0, 4, 7]` in first inversion
(`ordinal = 1`):

```
drop(1)            -> [4, 7]
take(1).map{+12}   -> [12]
raised             -> [4, 7, 12]
map { it - 4 }     -> [0, 3, 8]      // E–G–C as offsets above E
```

Work through `rootOffsetAboveBass` the same way, on paper, for a second-inversion dominant
seventh.

### `ListeningPrompt`: the boundary between music and audio

[music/ListeningPrompt.kt](../../app/src/main/java/com/troanhmolo/eartrainer/music/ListeningPrompt.kt)

This is the most important type in the app. It describes *what* to play musically (a list of
moments, each a list of pitches, plus articulation, optional context, and optional beat
timing) and says nothing about *how* it sounds. The KDoc at the top lists how each exercise
encodes its question.

- [Line 17](../../app/src/main/java/com/troanhmolo/eartrainer/music/ListeningPrompt.kt#L17):
  `data class` gives you `equals`, `hashCode`, `toString` and `copy` for free. Module 7 shows
  that `AudioTrackPlayer` relies on `equals` to reuse a rendered buffer on replay.
- [Line 55–59](../../app/src/main/java/com/troanhmolo/eartrainer/music/ListeningPrompt.kt#L55):
  invariants again. A prompt with zero moments is a bug, so it fails fast.
- [Line 61–73](../../app/src/main/java/com/troanhmolo/eartrainer/music/ListeningPrompt.kt#L61):
  factory functions (`sequential`, `simultaneous`, `arpeggiated`) give common cases readable
  names.

### `Key`, `ScaleDegree`, `Rhythm`

- [music/ScaleDegree.kt](../../app/src/main/java/com/troanhmolo/eartrainer/music/ScaleDegree.kt):
  `semitones.mod(12)` vs `semitones % 12`: in Kotlin, `%` keeps the sign (`-1 % 12 == -1`),
  while `.mod()` doesn't (`(-1).mod(12) == 11`). Find where this matters.
- [Key.cadence()](../../app/src/main/java/com/troanhmolo/eartrainer/music/ScaleDegree.kt#L57):
  four chords as offset lists. The comments explain the voicing choices. This is music theory
  encoded as data.
- [music/Rhythm.kt](../../app/src/main/java/com/troanhmolo/eartrainer/music/Rhythm.kt):
  `startBeats` uses `runningFold` to compute where each cell starts. Run it in your head on
  `[QUARTER, HALF, QUARTER]`.

## Exercises

Create a branch: `git switch -c learning/module-01`.

1. **Scratch file.** In Android Studio, *File → New → Scratch File → Kotlin*. Paste in
   `Pitch` (just the class) and print the frequency of every C from C1 to C8. Check: each
   octave doubles the frequency.
2. **Read a test, then extend it.** Open
   [PitchTest.kt](../../app/src/test/java/com/troanhmolo/eartrainer/music/PitchTest.kt). Add a
   test that `Pitch(60).frequencyHz` is about 261.63 Hz (use `assertEquals(expected, actual,
   0.01)`), and one that constructing `Pitch(128)` throws. Hint: `assertThrows`.
3. **Add a chord quality.** Add `SUSPENDED_FOURTH(listOf(0, 5, 7), "Suspended 4th", "sus4")`
   to `ChordQuality`. Run `./gradlew test`. Then run the app and open the chord exercise
   settings. Did the new quality appear without other code changes? Find out why (*Find
   Usages* on `ChordQuality.entries`). Add a test to
   [ChordTest.kt](../../app/src/test/java/com/troanhmolo/eartrainer/music/ChordTest.kt) for
   its first inversion offsets.
4. **Nullable practice.** Write `fun Interval.inverted(): Interval?`. The inversion of an
   interval is `12 − semitones` (a major third inverts to a minor sixth). What should the
   octave return? Write tests for it.
5. **Spelling.** Use `Key.noteName` to print the notes of E♭ major
   (`Key(Pitch(63), Mode.MAJOR)`) via `Mode.MAJOR.diatonicDegrees`. Why does the class need
   `prefersFlats` at all? (Think about what a musician expects to read.)
6. **Stretch.** Write `fun ChordQuality.Companion.identify(offsets: List<Int>): Pair<ChordQuality, ChordInversion>?`,
   which recognizes a chord from its close-position offsets above the bass. Is the answer
   always unique? Try `[0, 3, 6, 9]` and your new sus4 chord in each inversion.

## Checkpoint

1. What does `@JvmInline value class` give you that a `typealias Pitch = Int` wouldn't?
2. Why does `Interval.fromSemitones` return `Interval?` instead of throwing?
3. What does the `data` modifier generate, and which of those generated functions does the
   audio layer depend on?
4. How many semitones is a perfect fifth? What MIDI note is a perfect fifth above A4?
5. Why is a diminished seventh chord `[0, 3, 6, 9]` hard to identify by inversion?

<details>
<summary>Answers</summary>

1. Type safety. A typealias is just another name for `Int`, so any `Int` is accepted. A value
   class is a distinct type with its own `init` validation and methods, with no runtime
   allocation in most cases.
2. Not every distance is a simple interval. Asking is a normal operation, not a programming
   error, so the "no answer" case belongs in the type. Callers use `?:` or `?.let` to decide
   what to do.
3. `equals`/`hashCode`, `toString`, `componentN` (destructuring) and `copy`. `AudioTrackPlayer`
   compares prompts with `==` to reuse the last rendered buffer.
4. 7 semitones. A4 is 69, so 69 + 7 = 76 (E5).
5. Every inversion has the same shape (minor thirds stacked, 3 semitones apart), so the
   offsets above the bass are identical. Only the pitch classes tell you which note is the
   root, and even that's ambiguous by ear.

</details>
