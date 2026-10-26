# Module 15: Testing strategy and release builds

[Course index](README.md) · Previous: [Module 14](14-custom-drawing.md) · Next: [Module 16: Capstone projects](16-capstones.md)

## Why it matters

You've been running tests since Module 0. Now step back: what *kind* of test belongs where,
what this codebase covers well, and what it doesn't test at all. Then take the app from a
debug build to something you could ship: shrinking, signing, versioning and continuous
integration.

Read [Fundamentals of testing Android apps](https://developer.android.com/training/testing/fundamentals)
and [Test your Compose layout](https://developer.android.com/develop/ui/compose/testing).

## Concepts

### The testing pyramid, as it applies here

| Kind | Runs on | Speed | This repo |
|------|---------|-------|-----------|
| **Unit tests** (pure Kotlin) | JVM | ms | 47 files in `app/src/test`: music theory, generators, state machine, scheduling, synthesis, sampling, weighting, settings decoding, backup codec, ViewModels with fakes |
| **Integration tests** (real Android pieces) | Device | seconds | `app/src/androidTest`: Room migrations and queries, real FLAC decoding, real `AudioTrack` playback, license assets |
| **UI tests** (Compose) | Device | seconds | **None yet** (exercise 2) |
| **Manual / exploratory** | You | minutes | Listening, TalkBack, rotation, process death |

The shape follows from the architecture: because the logic is pure Kotlin, most of it is
covered by fast tests. The Android-specific parts are deliberately thin, and get a few
targeted device tests each.

### Test doubles

- **Fake**: a working, simplified implementation (`FakePracticeHistory`,
  `InMemoryPreferences`, `FakeAudioPlayer`). Used throughout this repo.
- **Stub**: returns canned answers.
- **Mock**: records calls so you can verify them (Mockito, MockK). This repo uses none, and
  its tests check *outcomes* rather than *calls*. Tests that check outcomes are less brittle
  when you refactor.

### What to test in audio code

You can't unit-test "it sounds good", but you can test the properties that make it sound
good: no clipping, no discontinuities, correct frequency, true silence in gaps, timing at
exact frames. `ToneSynthesizerTest` and `SampledInstrumentTest` are good models. Some things
(device quirks, real latency) only show up on real hardware, which is why
`AudioTrackPlayerTest` exists.

### Release builds

- **R8** shrinks (removes unused code), optimizes and obfuscates. That makes the APK smaller
  and faster, but code reached only by reflection can be wrongly removed, which is what
  **keep rules** prevent.
- **Signing**: every APK is signed. Debug builds use an auto-generated debug key. Releases need
  your own key, kept safe forever (Play App Signing can hold it for you).
- **Versioning**: `versionCode` (an integer that must increase with every upload) and
  `versionName` (shown to users).
- **App Bundles** (`.aab`): what Google Play wants. Play generates optimized APKs per device.

## Read the code

- Test organization: browse [app/src/test](../../app/src/test/java/com/troanhmolo/eartrainer)
  and note that the folders mirror `main`. The `testing/` folder holds shared fakes.
- [HomeStateTest](../../app/src/test/java/com/troanhmolo/eartrainer/ui/home/HomeStateTest.kt)
  and [HomeViewModelTest](../../app/src/test/java/com/troanhmolo/eartrainer/ui/home/HomeViewModelTest.kt):
  the same feature tested at two levels. The pure state builder gets the detailed cases, and
  the ViewModel test checks the wiring.
- [RoomPracticeHistoryTest](../../app/src/androidTest/java/com/troanhmolo/eartrainer/data/history/RoomPracticeHistoryTest.kt):
  SQL tested against a real (in-memory) SQLite on a device. SQL is code too, and it needs tests.
- [AudioTrackPlayerTest](../../app/src/androidTest/java/com/troanhmolo/eartrainer/audio/AudioTrackPlayerTest.kt):
  one test, for one specific bug class (stopping reported as failure).
- [app/build.gradle.kts, `buildTypes`](../../app/build.gradle.kts#L25): the release build
  enables R8 optimization (AGP 9's `optimization { }` block). `packageScope` limits the extra
  optimizations to the libraries listed.
- [app/src/main/keepRules/rules.keep](../../app/src/main/keepRules/rules.keep): empty apart
  from the template. kotlinx.serialization and Room ship their own rules. Exercise 4 checks
  whether that's enough.

## Exercises

Branch: `git switch -c learning/module-15`.

1. **Coverage map.** Run unit tests with coverage (*Run → Run 'Tests in …' with Coverage*). Which
   packages are near 100%, which near 0%? Is the low coverage in `ui/` a problem? Argue both
   sides.
2. **Your first Compose UI test.** Add `androidTestImplementation(platform(libs.androidx.compose.bom))`,
   `androidTestImplementation("androidx.compose.ui:ui-test-junit4")` and
   `debugImplementation("androidx.compose.ui:ui-test-manifest")` (move them into the version
   catalog properly). Then write `PracticeScreenTest` with `createComposeRule()`:
   - render `PracticeScreen` with a fake `AwaitingAnswer` state,
   - `onNodeWithText("Major third").performClick()` and assert the `onAnswerSelected` lambda
     received `Interval.MAJOR_THIRD`,
   - render an `Answered` state and assert the feedback panel and "Next" button exist.
   Use `onRoot().printToLog("TREE")` to see the semantics tree TalkBack uses.
3. **An end-to-end test.** With `createAndroidComposeRule<MainActivity>()`, open the interval
   exercise from Home, tap play, wait for idle, answer, and check that the session strip shows
   one result. What makes this flaky? (Audio timing, real database state.) How would you
   control those?
4. **Release build.** Run `./gradlew :app:assembleRelease` (configure a signing config with a
   keystore you create via *Build → Generate Signed App Bundle/APK*; never commit the
   keystore or its passwords). Install it and test **backup export/import** and **every
   exercise**. Serialization and reflection are where R8 problems hide. Compare APK sizes
   with the debug build in the APK Analyzer.
5. **CI.** Add a GitHub Actions workflow (`.github/workflows/android.yml`) that runs
   `./gradlew test lint` on every push using `actions/setup-java`. Make it fail by breaking a
   test on a branch, then fix it.
6. **Lint.** Run `./gradlew lint` and open the HTML report. Fix or justify the top three
   warnings.
7. **Stretch: screenshot tests.** Read about Compose Preview Screenshot Testing (the
   `com.android.compose.screenshot` plugin). Turn the practice screen previews into reference
   images and see a test catch an intentional color change.

## Checkpoint

1. Why are most of this app's tests fast JVM tests?
2. What's the difference between a fake and a mock, and which does this codebase prefer?
3. Name two audio properties you can assert in a unit test.
4. What does R8 do, and why can it break serialization?
5. What happens if you lose your app's signing key?

<details>
<summary>Answers</summary>

1. The architecture keeps the logic (music, exercises, rendering, state building) in pure
   Kotlin with dependencies injected, so it runs on a plain JVM without Android.
2. A fake is a working lightweight implementation, while a mock records and verifies
   interactions. This codebase uses fakes and asserts outcomes.
3. Any two of: peak level within headroom (no clipping), sample-to-sample steps below a bound
   (no clicks), measured frequency (zero crossings or autocorrelation), exact silence in gaps,
   note onsets at exact frames.
4. It shrinks, optimizes and obfuscates code. Code reached only reflectively (serializers
   looked up by name, for example) can look unused and be removed or renamed unless keep
   rules protect it.
5. Without Play App Signing, you can never publish an update to that app again. Users would
   have to install a new app. With Play App Signing, Google holds the app key and you can
   reset your upload key.

</details>
