# Module 2: Pure logic, state machines and unit tests

[Course index](README.md) · Previous: [Module 1](01-kotlin-music-model.md) · Next: [Module 3: Digital audio](03-digital-audio-synthesis.md)

## Why it matters

The most common source of bugs in apps is **state**: a button tapped twice, an answer arriving
after the question changed, a score counted twice. This codebase guards against those bugs by
modeling each exercise as an **immutable state machine** in plain Kotlin, outside the UI. That
makes the logic easy to reason about and fast to test. Learn this pattern here, before
coroutines and Compose make things concurrent.

## Concepts

### Immutable state and transitions

Instead of an object whose fields change, keep a value (`ExerciseState`) and *functions that
return a new value*. A transition that isn't allowed returns the same instance unchanged.
Benefits:

- **No half-updated state.** A transition produces a complete new state or nothing.
- **Easy equality.** "Did anything change?" is `next === current`.
- **Easy tests.** Call a function, compare the result. No mocks, no setup.

### Sealed interfaces

A `sealed interface` has a closed set of implementations known at compile time. `when` over a
sealed type is **exhaustive**: forget a case and it won't compile. `ExercisePhase` is either
`Unheard`, `AwaitingAnswer`, or `Answered(...)`. Each phase can carry its own data (only
`Answered` has a selected answer). That's better than a set of booleans like `isHeard`,
`isAnswered`, `selectedAnswer?`, which allows contradictory combinations.

### Generics with constraints

`ExerciseState<A, Q : ExerciseQuestion<A>>` reads as "a state for questions of type `Q`
whose answers have type `A`". One engine then serves intervals (`A = Interval`), chords,
scale degrees, dictation (`A = Melody`), and so on.

### Determinism: inject what varies

Tests must give the same result every run. So anything nondeterministic is **passed in**
instead of grabbed globally:

- randomness → `random: Random` parameter (tests pass `Random(42)`)
- time → `atMillis: Long` parameters, or a `clock: () -> Long`
- IDs → `newSessionId: () -> String`

### A little probability: Laplace smoothing

Weak-spot practice asks more often about items you get wrong. Raw accuracy is unstable: one
miss out of one attempt reads as 0% accuracy. Adding one imaginary right and one imaginary
wrong answer, `(correct + 1) / (attempts + 2)`, gives sensible estimates even with little data.
This is **Laplace's rule of succession**, a standard trick for small counts.

## Read the code

### The state machine

[exercise/ExerciseSession.kt](../../app/src/main/java/com/troanhmolo/eartrainer/exercise/ExerciseSession.kt)

- [Line 7–20](../../app/src/main/java/com/troanhmolo/eartrainer/exercise/ExerciseSession.kt#L7):
  `ExercisePhase<out A>`. Note `out` (covariance) and `ExercisePhase<Nothing>` for the phases
  without data. `Nothing` is a subtype of every type, so `Unheard` is a valid
  `ExercisePhase<Interval>` and a valid `ExercisePhase<ChordAnswer>`.
- [Line 45–49](../../app/src/main/java/com/troanhmolo/eartrainer/exercise/ExerciseSession.kt#L45):
  `promptPlayed` moves `Unheard → AwaitingAnswer`, counts plays, and remembers the first play
  time (`firstPlayedAtMillis ?: atMillis` keeps the first value).
- [Line 51–58](../../app/src/main/java/com/troanhmolo/eartrainer/exercise/ExerciseSession.kt#L51):
  `submitAnswer` guards first (`return this`), then builds the next state. Duplicate taps and
  answers before hearing the prompt are handled here, once, for every exercise.
- [Line 62–83](../../app/src/main/java/com/troanhmolo/eartrainer/exercise/ExerciseSession.kt#L62):
  `ExerciseEngine` gets its questions from a function `() -> Q`. It doesn't know about
  intervals or randomness.

Draw the state diagram on paper: three phases, the transitions between them, and which
transitions are no-ops.

### The question contract

[exercise/ExerciseQuestion.kt](../../app/src/main/java/com/troanhmolo/eartrainer/exercise/ExerciseQuestion.kt)

- Interfaces in Kotlin can have **default method implementations**: `isCorrect` and `accepts`.
  Dictation overrides `accepts` because its answer is built up rather than picked from a list.
- The KDoc rule: "correctness is always judged against `correctAnswer`, never against a label
  or position shown in the UI". Why would comparing button positions be a bug waiting to
  happen?

### A generator

[exercise/IntervalQuestionGenerator.kt](../../app/src/main/java/com/troanhmolo/eartrainer/exercise/IntervalQuestionGenerator.kt)

- [Line 7–27](../../app/src/main/java/com/troanhmolo/eartrainer/exercise/IntervalQuestionGenerator.kt#L7):
  settings validate themselves. The pitch range must fit the widest enabled interval.
- [Line 30–41](../../app/src/main/java/com/troanhmolo/eartrainer/exercise/IntervalQuestionGenerator.kt#L30):
  toggle functions refuse to go below the minimum. The UI can call them blindly.
- [Line 60–70](../../app/src/main/java/com/troanhmolo/eartrainer/exercise/IntervalQuestionGenerator.kt#L60):
  pick an interval (weighted or uniform), then a lower note that keeps the upper note in range.
- [Line 77–84](../../app/src/main/java/com/troanhmolo/eartrainer/exercise/IntervalQuestionGenerator.kt#L77):
  the choices are a contiguous run of interval sizes containing the answer, with a random
  start. Work out on paper the possible runs for `MAJOR_THIRD` with `choiceCount = 4` and all
  12 intervals enabled.

### Weighting

[exercise/WeakSpotWeighting.kt](../../app/src/main/java/com/troanhmolo/eartrainer/exercise/WeakSpotWeighting.kt)

- [`weightOf`](../../app/src/main/java/com/troanhmolo/eartrainer/exercise/WeakSpotWeighting.kt#L25):
  `MIN_WEIGHT + (1 − smoothed accuracy)`. Check the comment's numbers yourself: 20/20 → ≈0.25,
  0/5 → ≈1.06.
- [`weightedRandom`](../../app/src/main/java/com/troanhmolo/eartrainer/exercise/WeakSpotWeighting.kt#L37):
  roulette-wheel selection. Draw a number in `[0, total)` and walk the list subtracting
  weights. The last line handles floating-point leftovers. Why can `remaining` still be ≥ 0
  after the loop?

### The tests

- [ExerciseSessionTest.kt](../../app/src/test/java/com/troanhmolo/eartrainer/exercise/ExerciseSessionTest.kt):
  test names are sentences (`answersAreIgnoredBeforeThePromptIsHeard`). Read the names first.
  Together they read as a specification.
- [IntervalQuestionGeneratorTest.kt](../../app/src/test/java/com/troanhmolo/eartrainer/exercise/IntervalQuestionGeneratorTest.kt#L13):
  `Random(seed)` makes "random" questions repeatable. Tests generate hundreds of questions
  and assert **properties** that must hold for all of them (in range, no duplicates, the
  answer is among the choices). This is a lightweight form of **property-based testing**.
- [WeakSpotWeightingTest.kt](../../app/src/test/java/com/troanhmolo/eartrainer/exercise/WeakSpotWeightingTest.kt):
  statistical tests. Generate many picks and check the proportions are roughly right.

## Exercises

Branch: `git switch -c learning/module-02`.

1. **Break a guard.** In `submitAnswer`, delete the `phase != ExercisePhase.AwaitingAnswer`
   check. Run `./gradlew test`. Which tests fail, and what bug would a user see? Restore it.
2. **TDD a feature: no immediate repeats.** Listeners find it odd when the same interval comes
   twice in a row. Using test-driven development:
   1. In `IntervalQuestionGeneratorTest`, write `sameIntervalIsNeverAskedTwiceInARow` (generate
      500 questions with a seed, assert no two neighbors share an interval). Run it and watch it
      fail.
   2. Make it pass in `IntervalQuestionGenerator`, keeping weighted selection working. Hint:
      remember the previous interval and pick from the others. What should happen when only
      one interval would remain? (Settings guarantee at least two. Check where.)
   3. Run the whole suite. Did `everyEnabledIntervalIsEventuallyAsked` still pass?
3. **Exhaustive `when`.** Add a fourth phase `Skipped` to `ExercisePhase`. Follow the compiler
   errors. How many places did you have to touch? Revert when done. The point is to see the
   compiler find every place for you.
4. **Smoothing experiment.** In a scratch file, print `weightOf(c, n)` for `n` in 0..10 and
   all `c`. Then try `(c + 2.0) / (n + 4.0)`. How does a stronger prior change how quickly
   weights react?
5. **Stretch: a session statistic.** Add `longestStreak` to `SessionScore` (the longest run of
   consecutive correct answers in the session). Keep `SessionScore` immutable. Write tests
   first.

## Checkpoint

1. Why does `submitAnswer` return `this` for invalid input instead of throwing?
2. What does `out` in `ExercisePhase<out A>` allow?
3. Why does `ExerciseState` take `atMillis` as a parameter instead of calling
   `System.currentTimeMillis()`?
4. What property would make a test of a random generator flaky, and how do these tests avoid
   it?

<details>
<summary>Answers</summary>

1. Invalid input here is normal user behavior (double taps, tapping before listening), not a
   programming error. Returning the same state makes it a no-op, and callers detect it with
   `next === current`.
2. Covariance: an `ExercisePhase<Nothing>` (like `Unheard`) can be used wherever an
   `ExercisePhase<Interval>` is expected, because `A` only appears in "out" positions
   (returned, never accepted).
3. To keep the class pure and deterministic. Tests pass exact times and can assert exact
   response durations.
4. Assertions that depend on the specific random sequence, or that assume a rare event never
   happens. These tests seed `Random(42)` so the run is repeatable, and assert properties
   (with generous tolerances for proportions) rather than exact outputs.

</details>
