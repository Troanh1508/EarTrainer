# Module 11: Navigation

[Course index](README.md) · Previous: [Module 10](10-viewmodels-and-architecture.md) · Next: [Module 12: Persistence](12-persistence.md)

## Why it matters

Any app with more than one screen needs navigation. You have to decide what's on the back
stack, how screens pass data to each other, what the Back button does, and how long each
screen's ViewModel lives. Navigation Compose handles all of that, and its ViewModel scoping is
what makes the audio lifecycle in this app work.

Read [Navigation with Compose](https://developer.android.com/develop/ui/compose/navigation).

## Concepts

- **`NavController`**: owns the **back stack** of destinations. `rememberNavController()`
  creates one that survives recomposition and configuration changes.
- **`NavHost`**: maps routes to composables. Only the top destination is shown.
- **Route**: a destination's address. This app uses **string routes** (`"interval-training"`),
  with arguments as path segments (`"compare/{exercise}/{scope}/{first}/{second}"`).
  Navigation 2.8+ also supports **type-safe routes**, `@Serializable` classes or objects that
  the compiler checks. That's the modern default and exercise 3 below.
- **`navigate(route)`** pushes. **`navigateUp()`** pops to the previous destination (what the
  toolbar back arrow does). The system Back gesture pops too.
- **Each back stack entry is a `ViewModelStoreOwner`.** A `viewModel()` call inside
  `composable(route) { ... }` is scoped to *that entry*. It survives while the entry is on the
  stack (including while other screens are on top of it) and is cleared when the entry is
  popped. Two `viewModel()` calls for the same type in the same entry return the same instance
  unless you give them different `key`s.
- **Arguments should be small and stable**: IDs and enum names, not whole objects. The
  destination looks the rest up.

## Read the code

[ui/navigation/EarTrainerNavHost.kt](../../app/src/main/java/com/troanhmolo/eartrainer/ui/navigation/EarTrainerNavHost.kt)

- [`Routes`, lines 40–58](../../app/src/main/java/com/troanhmolo/eartrainer/ui/navigation/EarTrainerNavHost.kt#L40):
  a private object of constants, so a typo can't create a route that silently doesn't exist.
- [Lines 64–84](../../app/src/main/java/com/troanhmolo/eartrainer/ui/navigation/EarTrainerNavHost.kt#L64):
  Home gets **callbacks** (`onOpenExercise`, `onOpenProgress`…), never the `NavController`
  itself. Screens don't know how navigation works. That keeps them previewable and testable,
  and the whole navigation graph readable in one file.
- `onOpenExercise` maps an `ExerciseKind` to a route with an exhaustive `when`. Add a seventh
  exercise and this won't compile until you add its route.
- [Lines 85–94](../../app/src/main/java/com/troanhmolo/eartrainer/ui/navigation/EarTrainerNavHost.kt#L85):
  training and settings are separate destinations. Settings is pushed **on top of** training,
  so the training ViewModel stays alive underneath, still collecting the settings flow. When
  you change a setting and go back, it has already started a fresh session (Module 10).
- [Lines 158–171](../../app/src/main/java/com/troanhmolo/eartrainer/ui/navigation/EarTrainerNavHost.kt#L158):
  a destination with arguments. Values come from `entry.arguments`, and
  [`ComparisonRequest.fromRoute`](../../app/src/main/java/com/troanhmolo/eartrainer/ui/compare/ComparisonRequest.kt#L26)
  validates them. Any unknown name gives `null`, and the screen backs out with
  `LaunchedEffect(Unit) { navController.navigateUp() }`. **Never trust route arguments.**
  Deep links and process restoration can hand you stale or malformed values.
- [ComparisonRequest.route](../../app/src/main/java/com/troanhmolo/eartrainer/ui/compare/ComparisonRequest.kt#L16):
  the request knows how to turn itself into a route, so building and parsing live side by
  side.

### ViewModel scoping in practice

- [IntervalTrainingRoute, lines 69–74](../../app/src/main/java/com/troanhmolo/eartrainer/ui/intervals/IntervalPractice.kt#L69):
  `viewModel(key = "interval-practice", factory = ...)`. The comment says it: "leaving the
  screen clears the ViewModel, which releases its audio player". Back-stack scoping is what
  calls `onCleared()` at the right moment.
- [ComparisonDestination, line 142](../../app/src/main/java/com/troanhmolo/eartrainer/ui/compare/ComparisonDestination.kt#L142):
  `viewModel(key = request.route, ...)`. Each comparison (minor vs major third, fourth vs fifth)
  gets its own ViewModel. Notice that comparison drills reuse `PracticeViewModel` with a
  `Comparison` as the "settings" type.

## Exercises

Branch: `git switch -c learning/module-11` (continue from your Module 10 branch if you did
the reference-pitch exercise).

1. **Ship the reference-pitch screen.** Write a stateless `ReferencePitchScreen` for the
   ViewModel from Module 10 (pitch chips for C4…B4, a play button, a back arrow). Add a
   `REFERENCE_PITCH` route and a way to reach it from Home. Check: playing, then pressing Back,
   stops the sound. Which line of code makes that happen?
2. **Watch the back stack.** Log `navController.currentBackStack.value.map { it.destination.route }`
   on every change (use a `LaunchedEffect` collecting `currentBackStackEntryFlow`). Open an
   exercise, then its settings, then go back twice. Then open Progress → Mistakes → a comparison.
3. **Type-safe routes.** Migrate `PRACTICE_LOG` and the comparison destination to type-safe
   routes: `@Serializable data object PracticeLog` and `@Serializable data class Compare(val
   exercise: String, val scope: String?, val first: String, val second: String)`, then use
   `composable<Compare> { entry -> entry.toRoute<Compare>() }`. The serialization plugin is
   already applied. What validation does `fromRoute` still need to do?
4. **Single top.** Tap the Progress button twice quickly. How many Progress entries end up on
   the stack? Fix it with `navigate(route) { launchSingleTop = true }`.
5. **Stretch: deep link.** Add a deep link (`navDeepLink { uriPattern = "eartrainer://compare/{exercise}/{scope}/{first}/{second}" }`)
   and an intent filter in the manifest. Open it with
   `adb shell am start -a android.intent.action.VIEW -d "eartrainer://compare/INTERVALS/ALL/MINOR_SIXTH/MAJOR_SIXTH"`.
   Then try a malformed URI and confirm the validation in `fromRoute` protects you.

## Checkpoint

1. Why does Home receive callbacks instead of the `NavController`?
2. When is a ViewModel created with `viewModel()` inside a `composable(route)` cleared?
3. Why does `ComparisonDestination` pass `key = request.route`?
4. Why must route arguments be validated?
5. What advantage do type-safe routes have over string routes?

<details>
<summary>Answers</summary>

1. To keep screens independent of navigation: they can be previewed and tested with no-op
   lambdas, and the whole graph is visible in one file.
2. When its back stack entry is popped (Back, `navigateUp`, or popped by a navigation
   action). Not when another screen is pushed on top.
3. So different comparisons get different ViewModel instances. Without a distinct key, the
   same entry could reuse a ViewModel set up for another pair.
4. Routes can come from deep links, stale saved state or bugs, so any string can arrive. A
   bad value must not crash the app or build a nonsensical screen.
5. The compiler checks argument names and types, and you get a typed object instead of
   parsing strings by hand.

</details>
