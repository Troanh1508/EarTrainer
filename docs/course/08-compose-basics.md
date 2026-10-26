# Module 8: Compose fundamentals

[Course index](README.md) · Previous: [Module 7](07-android-audio-apis.md) · Next: [Module 9: Compose state, effects and lifecycle](09-compose-state-and-effects.md)

## Why it matters

Jetpack Compose is how modern Android UIs are built. Instead of inflating XML layouts and
mutating views, you write functions that **describe** the UI for a given state, and Compose
updates the screen when the state changes. This module covers the building blocks:
composables, layout, modifiers, Material 3 components, theming, resources and previews.

Read [Thinking in Compose](https://developer.android.com/develop/ui/compose/mental-model)
first. It's short and it changes how you read everything below.

## Concepts

### Composables

A `@Composable` function emits UI. It takes data, returns `Unit`, and can be **called again at
any time** (recomposed) when its inputs change. Consequences:

- Composables should be **fast** and **free of side effects**: no network calls, no writes,
  no `audioPlayer.play()` in the body. Module 9 covers where those go.
- They should depend only on their parameters (and on state they read), so the same
  input gives the same UI.

### State hoisting: stateless composables

The pattern used throughout this app:

```kotlin
@Composable
fun PracticeScreen(state: PracticeUiState, onPlay: () -> Unit, onAnswerSelected: (A) -> Unit, ...)
```

The composable **receives state and emits events**. It doesn't own or fetch anything.
That's **state hoisting**: state lives higher up (in a ViewModel), events flow up, and state
flows down. This is called **unidirectional data flow**. Stateless composables are easy to
preview, test and reuse.

### Layout

- `Column` (vertical), `Row` (horizontal), `Box` (stacked on top of each other).
- `Arrangement.spacedBy(12.dp)` for gaps between children, `Alignment` for cross-axis placement.
- `Modifier.weight(1f)` inside a Row or Column shares the leftover space.
- `Scaffold` provides slots for a top bar, bottom bar, FAB and content, and hands you
  `innerPadding` so content doesn't hide under the bars.
- `LazyColumn` renders only visible items. Use it for long lists (Module 9).

### Modifiers, and why order matters

A `Modifier` chain is applied **in order**, outside-in.
`Modifier.padding(16.dp).background(Red)` gives a red box *inside* a 16 dp margin.
`Modifier.background(Red).padding(16.dp)` gives a red box with 16 dp of red padding inside it.
By convention every reusable composable takes `modifier: Modifier = Modifier` as its first
optional parameter and applies it to its root element.

### Material 3 and theming

`MaterialTheme` provides a `colorScheme`, `typography` and `shapes` to everything inside it.
Components (`Button`, `Surface`, `TopAppBar`, `FilterChip`) read from it, so changing the
theme restyles the whole app. Always use `MaterialTheme.colorScheme.primary` rather than a
hard-coded color, so dark mode works automatically.

### Resources

`stringResource(R.string.x)` and `pluralStringResource(R.plurals.x, count, ...)` read
strings from `res/values/strings.xml`. Keeping text out of code makes translation possible.
Plurals exist because "1 semitones" is wrong, and some languages have more than two plural
forms.

### Accessibility basics

Compose builds a **semantics tree** that TalkBack (the screen reader) reads.
`contentDescription`, `stateDescription`, `role`, `mergeDescendants` and `liveRegion` control
what's announced. Good apps never rely on color alone to convey meaning.

## Read the code

### The app shell

- [MainActivity.kt](../../app/src/main/java/com/troanhmolo/eartrainer/MainActivity.kt):
  `setContent { EarTrainerTheme { EarTrainerNavHost() } }`. The theme wraps everything.
- [ui/theme/Theme.kt](../../app/src/main/java/com/troanhmolo/eartrainer/ui/theme/Theme.kt):
  hand-picked light and dark `ColorScheme`s, `EarTrainerTheme` choosing between them with
  `isSystemInDarkTheme()` ([line 70](../../app/src/main/java/com/troanhmolo/eartrainer/ui/theme/Theme.kt#L70)),
  and **extension properties on `ColorScheme`** (`weakSpot`, `weakSpotContainer`) that add
  app-specific colors while respecting dark mode.
- [ui/theme/Icons.kt](../../app/src/main/java/com/troanhmolo/eartrainer/ui/theme/Icons.kt):
  a custom `Icons.Filled.Stop` vector, because the core icon set lacks one and the extended set
  is very large. Note `by lazy`.

### One screen for six exercises

[ui/practice/PracticeScreen.kt](../../app/src/main/java/com/troanhmolo/eartrainer/ui/practice/PracticeScreen.kt)

- [`PracticeTexts`, lines 43–61](../../app/src/main/java/com/troanhmolo/eartrainer/ui/practice/PracticeScreen.kt#L43):
  per-exercise wording passed as **composable lambdas** (`@Composable (Q) -> String`). That's
  how one generic screen shows interval-specific, chord-specific and rhythm-specific text.
- [`PracticeScreen`, lines 117–139](../../app/src/main/java/com/troanhmolo/eartrainer/ui/practice/PracticeScreen.kt#L117):
  stateless, with all events as parameters. It passes `state::appearanceOf` (a function
  reference) to the grid.
- [`PracticeScaffold`, lines 147–231](../../app/src/main/java/com/troanhmolo/eartrainer/ui/practice/PracticeScreen.kt#L147):
  - `Scaffold` with a `TopAppBar` (back and settings icons, each with a content description).
  - `bottomBar` holds the feedback panel **only when answered**: `state.answer?.let { ... }`.
    The comment says why: "Next" stays reachable without scrolling.
  - `navigationBarsPadding()`: edge-to-edge means the app draws under the system bar, so
    content must be padded out of it.
  - The content column is `widthIn(max = 560.dp)` so it doesn't stretch absurdly on tablets.
  - `answerArea: @Composable () -> Unit` is a **slot**: callers decide what goes there (a grid
    of buttons for most exercises, a keypad for dictation).

### Components

[ui/practice/PracticeComponents.kt](../../app/src/main/java/com/troanhmolo/eartrainer/ui/practice/PracticeComponents.kt)

- [`AnswerGrid`, line 82](../../app/src/main/java/com/troanhmolo/eartrainer/ui/practice/PracticeComponents.kt#L82):
  a grid made from `chunked(columns)` → `Row` → `weight(1f)`, padding short rows with
  `Spacer`s so the buttons line up. Generic over the answer type `A`.
- [`AnswerChoice`, line 113 onward](../../app/src/main/java/com/troanhmolo/eartrainer/ui/practice/PracticeComponents.kt#L113):
  - `when (appearance)` maps six UI states to colors. The *decision* about which state applies
    was made in `PracticeUiState.appearanceOf`, not here (see the KDoc on `ChoiceAppearance`).
  - `Surface(onClick = ..., enabled = ...)`: a clickable Material surface.
  - `semantics { stateDescription = ... }` makes TalkBack say "correct" or "incorrect".
  - The comment "Icons back up the color change so the result never relies on color alone"
    is an accessibility rule: about 1 in 12 men has some color-vision deficiency.
- [`AnswerFeedback`](../../app/src/main/java/com/troanhmolo/eartrainer/ui/practice/PracticeComponents.kt#L186):
  `semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }`. When feedback
  appears, TalkBack announces it without the user having to find it.

[ui/practice/PracticePrompt.kt](../../app/src/main/java/com/troanhmolo/eartrainer/ui/practice/PracticePrompt.kt)

- [`SessionStrip`](../../app/src/main/java/com/troanhmolo/eartrainer/ui/practice/PracticePrompt.kt#L51):
  20 small `Box`es colored by result. The visual strip is hidden from TalkBack
  (`clearAndSetSemantics {}`) because the text above already says the same thing.
- [`PromptPlayer`](../../app/src/main/java/com/troanhmolo/eartrainer/ui/practice/PracticePrompt.kt#L91):
  a big circular button with a pulsing halo, made of three stacked `Box`es, `clip(CircleShape)`
  and `scale(...)`. The animation is covered in Module 9.

### Settings building blocks

[ui/settings/SettingsComponents.kt](../../app/src/main/java/com/troanhmolo/eartrainer/ui/settings/SettingsComponents.kt):
`FlowRow` wraps chips onto new lines. `SettingsSwitch` makes the whole row `toggleable` (a
big touch target) and passes `onCheckedChange = null` to the `Switch` so it isn't separately
clickable.

### Previews

[IntervalPractice.kt, lines 76–115](../../app/src/main/java/com/troanhmolo/eartrainer/ui/intervals/IntervalPractice.kt#L76):
previews build a fake `PracticeUiState` and call the **stateless** `PracticeScreen` with no-op
lambdas. One preview uses `uiMode = UI_MODE_NIGHT_YES` with `EarTrainerTheme(darkTheme = true)`.
Previews are only possible because the screen is stateless.

## Exercises

Branch: `git switch -c learning/module-08`.

1. **Previews as a playground.** Open `IntervalPractice.kt` in split view and look at the
   previews. Add a third preview showing a *correct* answer (`ExercisePhase.Answered(...,
   isCorrect = true, ...)`). Add `@Preview(fontScale = 1.5f)` to see large text, and
   `@Preview(widthDp = 320)` for a small phone. Does anything break?
2. **Modifier order.** In `AnswerChoice`, move `.heightIn(min = 72.dp)` before
   `.bringIntoViewRequester(...)`, then swap `padding` and `background` somewhere visible.
   Predict the result, then check it in the preview.
3. **Responsive grid.** Make `AnswerGrid` use 3 columns when the available width is at least
   600 dp. Hint: `BoxWithConstraints`, or the window size class APIs. Check it in a
   `@Preview(widthDp = 700)`.
4. **Theme it.** Change `primary` in `LightColors` and `DarkColors`. Note how much of the app
   changes without touching any screen. Then find any hard-coded colors in `ui/` (search for
   `Color(0x`). Which ones are justified?
5. **Plurals.** Find `R.plurals.intervals_semitones` in
   [strings.xml](../../app/src/main/res/values/strings.xml). Add a new string for the feedback
   panel, "You replayed it %d times", using plurals, and show it only when `playCount > 1`.
   Where does the play count live in the state?
6. **TalkBack.** Turn on TalkBack on your phone (Settings → Accessibility) and do three
   questions with your eyes closed. What's announced when you answer? What's confusing?
   Fix one thing.
7. **Stretch: a piano keyboard.** Write a stateless `PianoKeyboard(lowest: Pitch, highest:
   Pitch, highlighted: Set<Pitch>, modifier: Modifier)` from `Row`s and `Box`es (white keys in
   a row, black keys overlaid in a `Box`). Show it in the feedback panel, highlighting the
   notes that were played. (Module 14 redoes it with `Canvas`.)

## Checkpoint

1. Why shouldn't a composable call `audioPlayer.play()` in its body?
2. What does "state hoisting" mean, and what does it make possible here?
3. Why is `PracticeTexts` full of `@Composable` lambdas rather than plain strings?
4. What's the difference between `Modifier.padding(8.dp).background(c)` and
   `Modifier.background(c).padding(8.dp)`?
5. Why does `AnswerChoice` show an icon as well as a color?

<details>
<summary>Answers</summary>

1. The body can run many times, at times you don't control (every recomposition). The sound
   would play repeatedly and unpredictably. Side effects belong in event handlers or effect
   APIs.
2. Moving state out of a composable into its caller (here, a ViewModel), so the composable
   just renders parameters and reports events. It makes screens previewable, testable and
   reusable across six exercises.
3. Text depends on the question and comes from resources via `stringResource`, which is
   itself a composable (it needs the current configuration and locale).
4. The first pads, then paints only the inner area. The second paints the whole area, then
   pads the content inside the painted area.
5. So the result is clear to people who can't tell the colors apart, and to screen readers
   (via `stateDescription`).

</details>
