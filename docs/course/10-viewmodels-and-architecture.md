# Module 10: ViewModels and app architecture

[Course index](README.md) · Previous: [Module 9](09-compose-state-and-effects.md) · Next: [Module 11: Navigation](11-navigation.md)

## Why it matters

So far you've studied the parts. This module covers how they fit together: which code
lives where, who creates what, and how data flows between layers. A good architecture lets
you change one part (swap the synthesizer for samples, add an exercise) without touching the
others, and test each part on its own. This codebase is small enough to read in full and
disciplined enough to learn from.

Read the official [Guide to app architecture](https://developer.android.com/topic/architecture)
and its [UI layer](https://developer.android.com/topic/architecture/ui-layer) page.

## Concepts

### Layers and the dependency rule

```
ui/         Compose screens + ViewModels              (Android, Compose)
data/       Room, DataStore, files                    (Android)
audio/      playback (Android) + rendering (pure)
exercise/   questions, generators, state machine      (pure Kotlin)
music/      pitches, intervals, chords, prompts       (pure Kotlin)
```

**Dependencies point down.** `music/` knows nothing about exercises, `exercise/` knows
nothing about Room or Compose. The payoff: the most valuable logic (theory, question
generation, scoring) is pure Kotlin. It's tested in milliseconds and could be shared (the iOS
port re-implements exactly these layers).

### ViewModel

A `ViewModel` holds UI state and handles UI events for one screen. It:

- **survives configuration changes** (rotation, dark-mode switch, window resize),
- is **scoped** to an owner (an Activity, or a navigation destination) and is cleared when that
  owner is gone for good, at which point `onCleared()` runs and `viewModelScope` is cancelled,
- must **not** hold references to Views, Activities or composables (they're recreated on
  rotation, so the old ones would leak).

### UI state as one immutable object

Expose **one** `StateFlow<UiState>` per screen, where `UiState` is an immutable data class.
Put *derived* values (is it the last question? how should this button look?) in the state
class as computed properties. Composables then only render. The logic stays in plain Kotlin
where tests can reach it.

### Dependency injection, by hand

Classes receive their dependencies through constructors rather than creating them. Something
has to build the object graph:

- **Manual DI**: a container class with `lazy` properties, created once in `Application`.
  This app does this.
- **Frameworks**: Hilt (Google's, built on Dagger), Koin, Metro. Worth it in big multi-module
  apps. As the comment in `AppContainer` says, for an app this size "a DI framework would add
  nothing".

### Repositories and fakes

A **repository** interface (`PracticeHistory`) hides where data comes from. Production uses
`RoomPracticeHistory`, tests use `FakePracticeHistory`, and nothing else in the app can tell
the difference.

## Read the code

### The container

[AppContainer.kt](../../app/src/main/java/com/troanhmolo/eartrainer/AppContainer.kt): read it
top to bottom and draw the object graph. Notice:

- [Line 42](../../app/src/main/java/com/troanhmolo/eartrainer/AppContainer.kt#L42): **one**
  `preferencesDataStore` shared by every settings store. DataStore requires a single instance
  per file.
- `by lazy` everywhere: nothing is built until first use, so app start-up stays fast.
- `weakSpots` is *not* lazy. It must start collecting history at launch so weights are ready
  for the first question.
- [`createAudioPlayer()`](../../app/src/main/java/com/troanhmolo/eartrainer/AppContainer.kt#L110)
  is a **factory**, not a singleton: each screen owns and releases its own player.
- [`EarTrainerApplication`, lines 119–127](../../app/src/main/java/com/troanhmolo/eartrainer/AppContainer.kt#L119):
  the container's lifetime is the process.

### Getting dependencies into a ViewModel

[IntervalPractice.kt, lines 36–53](../../app/src/main/java/com/troanhmolo/eartrainer/ui/intervals/IntervalPractice.kt#L36):

```kotlin
val IntervalPracticeFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val container = (this[APPLICATION_KEY] as EarTrainerApplication).container
        IntervalPracticeViewModel(settings = container.intervalSettings.settings, ...)
    }
}
```

The framework creates ViewModels (so they can survive rotation), so you hand it a
**factory**. `this[APPLICATION_KEY]` reads the Application from the `CreationExtras` the
framework passes in. Compare the simpler
[HomeViewModel.Factory](../../app/src/main/java/com/troanhmolo/eartrainer/ui/home/HomeViewModel.kt#L64).

### One ViewModel class, six exercises

[PracticeViewModel](../../app/src/main/java/com/troanhmolo/eartrainer/ui/practice/PracticeViewModel.kt#L36)
is generic: `PracticeViewModel<A, Q : ExerciseQuestion<A>, S>`. What differs between
exercises is passed in as **functions** (the strategy pattern, done with lambdas):

| Parameter | Interval exercise supplies |
|-----------|----------------------------|
| `settings: Flow<S>` | `container.intervalSettings.settings` |
| `questionSource: (S) -> () -> Q` | a weighted `IntervalQuestionGenerator(settings)::generate` |
| `recordAttempt` | `IntervalAttempt.from(state, sessionId)` → `practiceHistory.record` |
| `itemsOf` | `wholeAnswerItems { it.displayName }` |
| `recentSessions` | the last few interval sessions |

Each exercise then adds a `typealias` (`IntervalPracticeViewModel = PracticeViewModel<Interval,
IntervalQuestion, IntervalExerciseSettings>`). Compare the six factories (`grep -rn typealias
app/src/main/java`). How much code does each exercise need?

Note `questionSource` has type `(S) -> () -> Q`: a function that takes settings and
**returns a function** that makes questions. Why not just `(S) -> Q`? (Hint: a generator
keeps internal state between questions, such as the `answerPool` it precomputes.)

### UI state with derived properties

[PracticeUiState.kt](../../app/src/main/java/com/troanhmolo/eartrainer/ui/practice/PracticeUiState.kt):
`isLastQuestion`, `canAnswer`, `canAdvance`, `responseMillis` and especially
[`appearanceOf`](../../app/src/main/java/com/troanhmolo/eartrainer/ui/practice/PracticeUiState.kt#L41).
The KDoc on `ChoiceAppearance` says it: "derived here so composables contain no exercise
logic". Similarly, `HomeUiState.from(...)` in
[HomeState.kt](../../app/src/main/java/com/troanhmolo/eartrainer/ui/home/HomeState.kt#L37)
is a **pure function** from data to screen state, tested in `HomeStateTest` without any
ViewModel.

### Settings: the store is the source of truth

[IntervalSettingsViewModel](../../app/src/main/java/com/troanhmolo/eartrainer/ui/settings/IntervalSettingsViewModel.kt):
"Edits are saved immediately; there is no separate 'save' step." The ViewModel exposes the
store's flow and writes through `store.update { it.withIntervalToggled(...) }`. The practice
ViewModel is collecting the same flow, so it starts a fresh session when settings change.
Two screens stay in sync with no direct link between them.

### Mapping between layers

- Domain record: [IntervalAttempt](../../app/src/main/java/com/troanhmolo/eartrainer/exercise/IntervalAttempt.kt)
  (typed: `Interval`, `Pitch`).
- Storage row: [IntervalAttemptEntity](../../app/src/main/java/com/troanhmolo/eartrainer/data/history/HistoryEntities.kt#L25)
  (strings and ints).
- The mapper: [`IntervalAttempt.toEntity()`](../../app/src/main/java/com/troanhmolo/eartrainer/data/history/PracticeHistory.kt#L472).

Each layer has its own model, so changing a table doesn't ripple into the exercise code.

### The repository and its fake

[PracticeHistory interface, line 68](../../app/src/main/java/com/troanhmolo/eartrainer/data/history/PracticeHistory.kt#L68)
and [FakePracticeHistory](../../app/src/test/java/com/troanhmolo/eartrainer/testing/FakePracticeHistory.kt#L44).

## Exercises

Branch: `git switch -c learning/module-10`.

1. **Draw it.** On paper, draw every object `AppContainer` creates and what each depends on.
   Mark which are created at launch, which lazily on first use, and which once per screen.
2. **Build a feature ViewModel: a reference pitch.** Musicians often want to hear a reference
   note. Create `ui/reference/ReferencePitchViewModel` with UI state `(pitch: Pitch, isPlaying:
   Boolean)` and events `onPitchSelected(Pitch)`, `onPlay()`, `onStop()`. It takes an
   `AudioPlayer` and plays a 2-second note. Write its factory using the container. Write a
   JVM test with a fake `AudioPlayer` (copy the idea from `PracticeViewModelTest`). You'll add
   the screen and navigation in Module 11.
3. **Find leaked logic.** Look through `ui/` for decisions made inside composables (an `if`
   that encodes a rule, not just layout). Move one into its UI state class and add a test.
4. **Process death audit.** With *Don't keep activities* on, start an interval session, answer
   five questions, background the app and return. What do you see, and why? Write a short
   design note: what would you save with `SavedStateHandle` to resume the session, and what
   would you leave out?
5. **Stretch: Hilt for comparison.** In a throwaway branch, migrate `HomeViewModel` and its
   dependencies to Hilt (follow the official Hilt + Compose guide). Count the files and
   annotations added. Write down when you'd choose Hilt over manual DI.

## Checkpoint

1. Why must a ViewModel never hold a reference to an Activity or a composable?
2. Why do ViewModels need factories at all?
3. Why is `createAudioPlayer()` a function while `practiceHistory` is a `lazy` property?
4. Where should "how should this answer button look?" be decided, and why there?
5. What does the generic `PracticeViewModel` gain from taking functions as parameters rather
   than subclassing per exercise?

<details>
<summary>Answers</summary>

1. ViewModels outlive configuration changes. Activities and composables are recreated, so
   the ViewModel would keep the old ones alive (a memory leak) and could touch a dead UI.
2. The framework creates and retains ViewModels across configuration changes, so you can't
   call the constructor yourself. A factory tells the framework how to build one with its
   dependencies.
3. Each screen needs its own player that it can release when it closes. History is one shared
   database for the whole app.
4. In the UI state (`PracticeUiState.appearanceOf`), as plain Kotlin. It's tested without
   Compose, and the composable stays a simple mapping from appearance to colors.
5. Composition over inheritance. Each exercise just supplies data and small functions, with
   no fragile base-class hooks, and each piece can be tested or swapped on its own.

</details>
