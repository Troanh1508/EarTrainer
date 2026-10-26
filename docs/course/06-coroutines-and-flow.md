# Module 6: Coroutines and Flow

[Course index](README.md) · Previous: [Module 5](05-sampled-instruments.md) · Next: [Module 7: Android audio APIs](07-android-audio-apis.md)

## Why it matters

Android apps are full of things that take time: decoding audio, reading a database, waiting
for a prompt to finish playing. The main thread must never block, or the UI freezes and
Android eventually shows "App isn't responding". Kotlin **coroutines** let you write that
asynchronous code as straight-line code. **Flow** models values that change over time. Both
are everywhere in modern Android, and they're the main source of subtle bugs: leaks,
races, swallowed cancellations. This codebase uses them carefully, so it's a good place to
learn them.

Read the official [coroutines guide](https://kotlinlang.org/docs/coroutines-guide.html)
sections "Basics", "Cancellation and timeouts", "Composing suspending functions" and
"Coroutine context and dispatchers" before or alongside this module.

## Concepts

### Suspending functions

A `suspend fun` can pause without blocking its thread, and resume later, possibly on another
thread. `AudioPlayer.play(prompt)` suspends **until the prompt has finished sounding**. The
caller simply writes `audioPlayer.play(prompt)` and the next line runs after the sound ends.

### Structured concurrency

Every coroutine runs in a **scope**, and the scope owns it:

- `viewModelScope`: cancelled when the ViewModel is cleared (the screen is gone for good).
- An application scope (`CoroutineScope(SupervisorJob() + Dispatchers.Default)`): lives as
  long as the process.
- `coroutineScope { }`: a child scope that waits for all its children and fails if one fails.

Rule of thumb: **work should be cancelled when the thing that needs its result goes away.**
UI work goes in `viewModelScope`. Work that must finish anyway (saving an answer) goes in the
application scope.

`SupervisorJob` means one failing child doesn't cancel its siblings. Without it, one failed
instrument decode would cancel every other background job in the app.

### Dispatchers

- `Dispatchers.Main`: the UI thread. Touch UI state here.
- `Dispatchers.Default`: CPU work (rendering PCM, crunching statistics). Sized to the CPU
  count.
- `Dispatchers.IO`: blocking I/O (files, streams).
- `withContext(dispatcher) { }` switches for a block and comes back.
- `limitedParallelism(n)`: a view of a dispatcher that runs at most `n` tasks at once.

### Cancellation is cooperative, and it's an exception

Cancelling a job makes its suspension points throw `CancellationException`. Two rules
follow:

1. **Never swallow `CancellationException`.** A `catch (e: Exception)` catches it too. If you
   don't rethrow it, the coroutine keeps running as if nothing happened. That's why this
   codebase writes `catch (e: CancellationException) { throw e }` before every broad `catch`.
2. **Clean up in `finally`.** It runs on success, failure *and* cancellation.

`ensureActive()` checks for cancellation explicitly. `cancelAndJoin()` cancels a job and
waits until its `finally` blocks have finished.

### Bridging callbacks: `CompletableDeferred`

Old Android APIs report completion through callbacks. A `CompletableDeferred<Unit>` turns a
callback into something you can `await()`: the callback calls `complete(Unit)`, and the
coroutine waiting on `await()` resumes.

### Flow, StateFlow and operators

- A `Flow<T>` is a **cold** stream: nothing happens until someone `collect`s it, and each
  collector runs it separately.
- A `StateFlow<T>` is a **hot** holder of one current value. New collectors get the latest
  value immediately. It's the standard way to expose UI state.
- `MutableStateFlow` + `update { }` changes state atomically (safe if several coroutines
  update it at once).
- `stateIn(scope, started, initial)` turns a cold Flow into a StateFlow.
  `SharingStarted.WhileSubscribed(5_000)` keeps the upstream running 5 s after the last
  collector leaves, so a screen rotation (which briefly unsubscribes) doesn't restart
  database queries.
- `map`, `combine` (merge several flows into one), `distinctUntilChanged` (skip repeats),
  `catch` (handle upstream errors), `flatMapLatest` (switch to a new inner flow whenever the
  outer one emits, cancelling the previous one).

## Read the code

Read these in order. Each builds on the previous.

### 1. Parallel decoding with `async`

[AssetSampleLoader.load, lines 22–31](../../app/src/main/java/com/troanhmolo/eartrainer/audio/AssetSampleLoader.kt#L22):
`coroutineScope` + `async` per recording + `awaitAll()`. `limitedParallelism(4)` runs four
decodes at once. The comment explains the number: decoding is mostly codec start-up rather than
CPU work, so a few in parallel cut the total time several-fold. If one decode throws, the
scope cancels the others and rethrows. That's structured concurrency in action.

### 2. Caching a background result: `Deferred`

[AppContainer, lines 83–112](../../app/src/main/java/com/troanhmolo/eartrainer/AppContainer.kt#L83):

- `ConcurrentHashMap<Instrument, Deferred<PcmRenderer>>` + `computeIfAbsent`: each instrument
  is decoded **at most once**, even if two screens ask at the same moment.
- `applicationScope.async { ... }` starts decoding right away. Anyone who needs the result
  later calls `await()`. If it's ready, no wait.
- The `catch` falls back to `ToneSynthesizer()` when samples fail to load: the app keeps
  working with the built-in synthesizer.
- [`preloadInstrument`](../../app/src/main/java/com/troanhmolo/eartrainer/AppContainer.kt#L104)
  collects the settings flow forever (`map` → `distinctUntilChanged`), so picking a new
  instrument starts its decode right away.
- [`createAudioPlayer`](../../app/src/main/java/com/troanhmolo/eartrainer/AppContainer.kt#L111)
  passes a **suspending lambda** as the renderer: each prompt asks for the current instrument
  and awaits it.

### 3. A callback API as a suspend function

[AudioTrackPlayer.play, lines 73–111](../../app/src/main/java/com/troanhmolo/eartrainer/audio/AudioTrackPlayer.kt#L73).
This is the most instructive coroutine code in the repo. Read every comment.

- Line 78: render on `Dispatchers.Default` (CPU-heavy).
- Lines 74–79: **a subtle bug avoided.** The track is stored in `created` *inside*
  `withContext` instead of being returned from it. If the caller is cancelled just as
  `withContext` finishes, its result is thrown away. A returned track would leak, but one
  stored in `created` is released by the `finally`.
- Lines 84–93: the AudioTrack marker callback completes a `CompletableDeferred`.
- Line 101: `withTimeoutOrNull` is a safety net for devices that never deliver the callback.
- Lines 102–107: `CancellationException` is rethrown **before** `IllegalStateException` is
  caught. The comment explains why: `CancellationException` *is* an `IllegalStateException`
  subclass. Swap the order and stopping a prompt would be reported as an audio failure.
- Line 108: `finally` releases the track in every case.

### 4. A ViewModel driving it all

[PracticeViewModel.kt](../../app/src/main/java/com/troanhmolo/eartrainer/ui/practice/PracticeViewModel.kt)

- [Lines 64–70](../../app/src/main/java/com/troanhmolo/eartrainer/ui/practice/PracticeViewModel.kt#L64):
  `settings.distinctUntilChanged().collect(::startSession)`. Every settings change starts a
  new session.
- [`onPlayClick`, lines 73–104](../../app/src/main/java/com/troanhmolo/eartrainer/ui/practice/PracticeViewModel.kt#L73):
  - `previous?.cancelAndJoin()`: stop the old prompt **and wait for its cleanup** before
    starting the new one, so two tracks never fight over the speaker.
  - `ensureActive()` after the `try`: a playback that was stopped reports nothing.
  - Lines 96–102: **stale-result guard.** By the time playback finishes, the user may have
    moved to another question or session. The result is applied only if `session` and
    `questionNumber` still match. Any async result needs this check.
- [`stopPlayback`, lines 208–212](../../app/src/main/java/com/troanhmolo/eartrainer/ui/practice/PracticeViewModel.kt#L208):
  cancels but keeps the reference, so the next play can join it.
- [`onCleared`](../../app/src/main/java/com/troanhmolo/eartrainer/ui/practice/PracticeViewModel.kt#L219):
  releases the audio player when the screen is gone for good.

### 5. Writes that outlive the screen

[RoomPracticeHistory.write, lines 371–382](../../app/src/main/java/com/troanhmolo/eartrainer/data/history/PracticeHistory.kt#L371):
writes launch in the **application** scope, so leaving the screen right after answering
doesn't cancel the save. Failures are logged rather than allowed to crash practice.

### 6. Flows end to end

- [IntervalSettingsStore.settings](../../app/src/main/java/com/troanhmolo/eartrainer/data/settings/IntervalSettingsStore.kt#L23):
  `catch` (a corrupt file falls back to defaults) → `map(::decode)` → `distinctUntilChanged`.
- [WeakSpots](../../app/src/main/java/com/troanhmolo/eartrainer/data/history/WeakSpots.kt#L32):
  `stateIn(scope, SharingStarted.Eagerly, emptyMap())`. Eagerly, because weights must be ready
  before the first question. `asWeightLookup()` reads `.value` synchronously when a generator
  picks.
- [HomeViewModel.uiState, lines 41–57](../../app/src/main/java/com/troanhmolo/eartrainer/ui/home/HomeViewModel.kt#L41):
  `combine` five flows into one UI state, with `WhileSubscribed(5_000)`.
- [ProgressViewModel, lines 156–173](../../app/src/main/java/com/troanhmolo/eartrainer/ui/progress/ProgressViewModel.kt#L156):
  `filter.flatMapLatest { ... }`. When the user picks a different filter, the old database
  queries are cancelled and new ones start.

### 7. Testing coroutines

[PracticeViewModelTest.kt](../../app/src/test/java/com/troanhmolo/eartrainer/ui/practice/PracticeViewModelTest.kt)

- [Line 98](../../app/src/test/java/com/troanhmolo/eartrainer/ui/practice/PracticeViewModelTest.kt#L98):
  `Dispatchers.setMain(StandardTestDispatcher())`. `viewModelScope` uses `Main`, which doesn't
  exist on the JVM, so tests swap in a controllable dispatcher.
- `runTest { ... advanceUntilIdle() ... }`: run all queued coroutines to completion with
  virtual time, so `delay(600)` takes no real time.
- [`FakeAudioPlayer`, line 47 onward](../../app/src/test/java/com/troanhmolo/eartrainer/ui/practice/PracticeViewModelTest.kt#L47):
  playback that lasts until the test calls `finish()`, built on `CompletableDeferred`. That
  lets the test observe the "playing" state, then stop it mid-prompt. A fake is often easier
  to understand than a mocking library.
- Look at the `failWhenCanceled` flag: the ViewModel is tested against a *badly behaved*
  player, to check that `ensureActive()` really protects it.

## Exercises

Branch: `git switch -c learning/module-06`.

1. **Swallow a cancellation.** In `PracticeViewModel.onPlayClick`, delete
   `catch (e: CancellationException) { throw e }`. Run `PracticeViewModelTest`. Which test
   fails, and what would the user see? Restore it.
2. **Reorder catches.** In `AudioTrackPlayer.play`, move the `IllegalStateException` catch above
   the `CancellationException` one. Does it compile? Run the instrumented
   `AudioTrackPlayerTest` on a device. Explain the result.
3. **Remove the join.** Change `previous?.cancelAndJoin()` to `previous?.cancel()`. Then tap
   Replay rapidly on a real device. Can you hear overlaps or glitches? Explain what can
   happen between `cancel()` returning and the old track's `finally` running.
4. **Observe `WhileSubscribed`.** Add a log line inside `HomeViewModel`'s `combine` block. Open
   Home, rotate the device, then navigate away and wait 10 s, then come back. When does the
   upstream restart? Change the timeout to `0` and repeat.
5. **Write a test: auto-advance.** Add a ViewModel option: after a correct answer, wait 1.5 s
   and move to the next question automatically, unless the user does something first. Use
   `delay` in `viewModelScope`. Write the test first with `runTest` and `advanceTimeBy(1_500)`.
   Make sure tapping Next during the wait doesn't skip two questions. (Hint: what job do you
   need to cancel, and where?)
6. **Stretch: a cold-flow surprise.** In a scratch test, create
   `val f = flow { println("start"); emit(1) }` and collect it twice. Then do the same with
   `f.stateIn(this)`. Explain the output and connect it to why the app turns repository flows
   into StateFlows in ViewModels.

## Checkpoint

1. What's wrong with `try { play() } catch (e: Exception) { showError() }` in a coroutine?
2. When should work use `viewModelScope`, and when an application scope?
3. What does `cancelAndJoin()` give you that `cancel()` doesn't?
4. Why does `AudioTrackPlayer` assign the track to a variable inside `withContext` instead of
   returning it?
5. What does `flatMapLatest` do with the previous inner flow?
6. Why do ViewModel tests call `Dispatchers.setMain`?

<details>
<summary>Answers</summary>

1. It catches `CancellationException` too. A stopped playback would show an error, and the
   coroutine would carry on instead of ending.
2. `viewModelScope` for work whose result only matters while the screen exists (playback,
   loading UI state). An application scope for work that must complete regardless (saving an
   answer, preloading samples).
3. It waits until the cancelled job has actually finished, including its `finally` blocks, so
   resources such as an AudioTrack are released before you start the next one.
4. If the caller is cancelled while `withContext` returns, the result is discarded and a
   returned track would never be released. Assigning it first means the `finally` block can
   always find and release it.
5. It cancels it when the outer flow emits a new value, then starts collecting the new
   inner flow.
6. `viewModelScope` dispatches on `Dispatchers.Main`, which needs an Android Looper. Tests on
   the JVM replace it with a test dispatcher they can control and fast-forward.

</details>
