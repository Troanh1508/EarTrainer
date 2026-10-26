# Module 9: Compose state, effects and lifecycle

[Course index](README.md) · Previous: [Module 8](08-compose-basics.md) · Next: [Module 10: ViewModels and app architecture](10-viewmodels-and-architecture.md)

## Why it matters

Module 8 kept state out of composables. Real screens still need some local state (a dialog
that's open, a scroll position, a half-typed melody) and some side effects (scroll when
answered, show a snackbar, stop audio when backgrounded). Getting these wrong produces the
classic Compose bugs: state that resets on rotation, effects that run on every
recomposition, work that keeps going after the screen is gone. This module is about those
rules.

Read [State and Jetpack Compose](https://developer.android.com/develop/ui/compose/state)
and [Side-effects in Compose](https://developer.android.com/develop/ui/compose/side-effects).

## Concepts

### Recomposition

When state that a composable **read** changes, Compose re-runs that composable (and only what
depends on it). State is anything in a `State<T>` / `MutableState<T>` (`mutableStateOf`,
`collectAsState…`). Plain `var`s don't trigger anything.

Compose skips a composable whose inputs haven't changed. With the Kotlin 2 Compose compiler,
**strong skipping** is on by default, which makes skipping work for most parameter types.
Still, prefer immutable data classes for UI state (as this app does): equality checks are
cheap and correct.

### `remember` vs `rememberSaveable`

| | Survives recomposition | Survives rotation / config change | Survives process death |
|---|---|---|---|
| `remember { }` | ✅ | ❌ | ❌ |
| `rememberSaveable { }` | ✅ | ✅ | ✅ |
| ViewModel | ✅ | ✅ | ❌ (needs `SavedStateHandle`) |

`rememberSaveable` writes to the Activity's saved-state `Bundle`. Primitives and Strings work
out of the box. Custom types need a `Saver`. Pass **keys** to `remember…(key1, key2)` to
reset the state when the keys change.

**Process death** is easy to forget. When your app is in the background, Android may kill its
process to free memory. When the user returns, the Activity is recreated and saved state is
restored, but all in-memory objects (including ViewModels) are new. Turn on *Developer options
→ Don't keep activities* to simulate it.

### Collecting flows in the UI

`viewModel.uiState.collectAsStateWithLifecycle()` converts a `StateFlow` into Compose `State`,
and **stops collecting when the app isn't visible** (below `STARTED`). Plain `collectAsState()`
keeps collecting in the background and wastes work.

### Effects

| API | Use it for |
|-----|-----------|
| `LaunchedEffect(key) { }` | Run a suspending block when the composable enters, and restart it whenever `key` changes. Cancelled when it leaves. |
| `rememberCoroutineScope()` | Launch coroutines from event handlers (`onClick`). |
| `DisposableEffect(key) { onDispose { } }` | Register/unregister listeners. |
| `LifecycleEventEffect(event) { }` | React to lifecycle events such as `ON_STOP`. |
| `SideEffect { }` | Publish Compose state to non-Compose code after every successful recomposition. |

The common mistake is `LaunchedEffect(Unit)` when the effect really depends on some value. It
then runs once and never again when the value changes.

### One-off events as state

"Show a snackbar once" is awkward in a declarative UI. The recommended pattern: put the message
in UI state, show it in a `LaunchedEffect(message)`, then tell the ViewModel it's been shown so
it clears the message. It survives rotation and is never lost.

### Lists

`LazyColumn { items(list, key = { it.id }) { ... } }` composes only the visible rows. Give items
a **stable key**, so Compose can keep each row's state and animate correctly when the list
changes.

## Read the code

### Collecting state and reacting to the lifecycle

[PracticeHost, lines 90–115](../../app/src/main/java/com/troanhmolo/eartrainer/ui/practice/PracticeScreen.kt#L90):

- `val state by viewModel.uiState.collectAsStateWithLifecycle()`: `by` is Kotlin property
  delegation, so `state` reads like a plain value.
- `LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onScreenHidden() }`: stop audio
  when the app goes to the background.
- `val loaded = state ?: run { Surface(...) {}; return }`: an early return inside a
  composable. The KDoc explains the choice of a blank surface over a spinner: loading is almost
  instant, and a spinner would flash.

### An effect keyed on a value

[PracticeScaffold, lines 157–168](../../app/src/main/java/com/troanhmolo/eartrainer/ui/practice/PracticeScreen.kt#L157):

```kotlin
val answered = state.answer != null
LaunchedEffect(answered) {
    if (answered) { withFrameNanos {}; scroll.animateScrollTo(scroll.maxValue) }
    else scroll.animateScrollTo(0)
}
```

The effect restarts only when `answered` flips. `withFrameNanos {}` waits one frame, so the
feedback panel has been laid out and `scroll.maxValue` is up to date before scrolling.
[ComparisonScreen](../../app/src/main/java/com/troanhmolo/eartrainer/ui/compare/ComparisonScreen.kt#L146)
waits **two** frames and keys on the question number too. Read its comment for why.

### Remembered objects and effects together

[AnswerChoice, lines 135–140](../../app/src/main/java/com/troanhmolo/eartrainer/ui/practice/PracticeComponents.kt#L135):
`remember { BringIntoViewRequester() }` creates the requester once per choice, and
`LaunchedEffect(appearance)` scrolls the correct answer into view when it's revealed.

### Animation

[PromptPlayer, lines 99–105](../../app/src/main/java/com/troanhmolo/eartrainer/ui/practice/PracticePrompt.kt#L99):
`rememberInfiniteTransition().animateFloat(1f → 1.08f, reverse)` gives a pulsing value, used
only while playing. Animations in Compose are just state that changes every frame.

### `rememberSaveable` with a custom Saver

- [DictationPractice, line 153](../../app/src/main/java/com/troanhmolo/eartrainer/ui/dictation/DictationPractice.kt#L153):
  the melody being typed is **local UI state** (the ViewModel only learns about it on submit).
  `rememberSaveable(state.exercise.questionNumber, question, stateSaver = ...)`: the keys reset
  the draft on a new question.
- [DictationEntry.Saver, lines 84–91](../../app/src/main/java/com/troanhmolo/eartrainer/ui/dictation/DictationDraft.kt#L84):
  `listSaver` stores the undo history and the current draft as a list of encoded strings. The
  draft and its undo stack survive rotation **and** process death.
- Simpler cases: [HomeScreen line 182](../../app/src/main/java/com/troanhmolo/eartrainer/ui/home/HomeScreen.kt#L182)
  (`choosingGoal` for a dialog) and
  [ProgressScreen line 328](../../app/src/main/java/com/troanhmolo/eartrainer/ui/progress/ProgressScreen.kt#L328).
  Compare with [ProgressScreen line 434](../../app/src/main/java/com/troanhmolo/eartrainer/ui/progress/ProgressScreen.kt#L434),
  which uses plain `remember` for a dropdown's `expanded` flag. Is losing that on rotation
  acceptable?

### One-off messages

[BackupScreen, lines 79–85](../../app/src/main/java/com/troanhmolo/eartrainer/ui/backup/BackupScreen.kt#L79):
`state.message?.let { LaunchedEffect(message) { showSnackbar(...); onMessageShown() } }`.
That's the "events as state" pattern from above.

### Effects in navigation

[EarTrainerNavHost, lines 166–168](../../app/src/main/java/com/troanhmolo/eartrainer/ui/navigation/EarTrainerNavHost.kt#L166):
an invalid comparison route calls `navigateUp()` **inside** `LaunchedEffect(Unit)`, never
directly in the composable body. Navigating during composition is a side effect.

### Lazy lists

[ProgressScreen, lines 171–190](../../app/src/main/java/com/troanhmolo/eartrainer/ui/progress/ProgressScreen.kt#L171):
`items(state.recentSessions, key = { it.sessionId })`.

## Exercises

Branch: `git switch -c learning/module-09`.

1. **Rotation vs process death.** In dictation, type three notes, then rotate. They survive.
   Now enable *Don't keep activities*, type three notes, press Home, and come back. Still
   there? Change `rememberSaveable` to `remember` and repeat both tests. Then explain what
   happens to the *ViewModel's* state (the current question) in the process-death case.
2. **Wrong key.** Change `LaunchedEffect(answered)` in `PracticeScaffold` to
   `LaunchedEffect(Unit)`. What breaks, and when? Change it to `LaunchedEffect(state)`. What
   breaks now? (Hint: how often does `state` change while a prompt plays?)
3. **Count recompositions.** Use the Layout Inspector's recomposition counts (*Tools → Layout
   Inspector*, enable "Show recomposition counts") during a session. Which composables
   recompose most while a prompt plays, and why? (Look at the infinite transition.)
4. **Move the pulse.** The `pulse` animation runs even when not playing (it's only *applied*
   when playing). Restructure `PromptPlayer` so the infinite transition only exists while
   `state.isPlaying` is true. Check the recomposition counts again.
5. **Keyboard shortcuts.** Add hardware-keyboard support to the practice screen: Space plays
   or replays, digits 1–9 pick the n-th choice, Enter goes to the next question. Use
   `Modifier.onKeyEvent` and focus handling. Where should the key → event mapping live so it
   stays testable?
6. **Stretch: auto-replay countdown.** After a wrong answer, show "Replaying in 3… 2… 1…" and
   replay the prompt, unless the user taps Next. Do the countdown in the ViewModel (state +
   `delay`), not in a `LaunchedEffect`. Argue why. Write the ViewModel test with virtual time.

## Checkpoint

1. A dialog's open/closed flag disappears when you rotate the phone. What's the fix?
2. What does `collectAsStateWithLifecycle` do that `collectAsState` doesn't?
3. When does a `LaunchedEffect(key)` block start, restart and stop?
4. Why is navigating directly in a composable body a bug?
5. Why does `LazyColumn`'s `items` take a `key`?
6. What's the difference between what `rememberSaveable` and a ViewModel survive?

<details>
<summary>Answers</summary>

1. Hold it with `rememberSaveable { mutableStateOf(false) }` instead of `remember`.
2. It stops collecting when the lifecycle drops below `STARTED` (app in background) and
   resumes when it's visible again, so no work is wasted off screen.
3. It starts when the composable enters the composition, is cancelled and restarted whenever
   `key` changes, and is cancelled when the composable leaves.
4. The body may run many times, and during composition. You'd trigger navigation repeatedly,
   or mid-frame. Side effects must go in effects or event callbacks.
5. So Compose can match items across changes (insertions, reorders) and keep per-item state
   and animations attached to the right item rather than to a position.
6. Both survive configuration changes. Only `rememberSaveable` (and `SavedStateHandle`)
   survive process death, but it can only hold small, `Bundle`-able data. A ViewModel holds
   anything, but only in memory.

</details>
