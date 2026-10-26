# Module 0: Orientation and tooling

[Course index](README.md) · Next: [Module 1: Kotlin through the music model](01-kotlin-music-model.md)

## Why it matters

Before learning any API, you need to be able to build the app, run it, run its tests, and
find your way around. You also need to read the build files, because every "why doesn't this
import resolve?" question ends up in Gradle.

## Concepts

### What an Android app is, physically

An app is an **APK** (or an AAB for the Play Store): a zip file containing

- compiled code (`classes.dex`: your Kotlin compiled to JVM bytecode, then to Dalvik bytecode),
- compiled resources (`res/`: strings, colors, icons, XML),
- raw **assets** (`assets/`: files you open by path; here, instrument recordings),
- the **manifest** (`AndroidManifest.xml`), which tells the OS what the app contains.

When you tap the launcher icon, the OS starts a **process** for the app, creates the
`Application` object named in the manifest, then the `Activity` that has the `MAIN` +
`LAUNCHER` intent filter. In a modern Compose app there is usually exactly one Activity, and
everything you see is drawn by Compose inside it.

### Gradle in five minutes

Gradle is the build system. The files that matter:

| File | Role |
|------|------|
| `settings.gradle.kts` | Which modules exist (`:app`) and where plugins and libraries come from |
| `build.gradle.kts` (root) | Declares plugins for all modules (`apply false` means "available, not applied here") |
| `app/build.gradle.kts` | The app module: SDK levels, build types, dependencies |
| `gradle/libs.versions.toml` | The **version catalog**: one place for every library version |
| `gradle.properties` | JVM memory, configuration cache, and similar flags |
| `gradle/wrapper/gradle-wrapper.properties` | The exact Gradle version (`./gradlew` downloads it) |

The version catalog is why `app/build.gradle.kts` says `libs.androidx.room.runtime` instead of
a hard-coded string. The catalog maps that name to a group, artifact and version.

### SDK levels

- `minSdk = 24`: the oldest Android version the app installs on (Android 7.0).
- `targetSdk`: the version whose behavior rules the app has opted into.
- `compileSdk`: the API level the code compiles against. You can *call* newer APIs only behind
  a version check (Module 7 shows one: `TransientAudioFocus`).

## Read the code

1. [settings.gradle.kts](../../settings.gradle.kts): repositories, and `include(":app")`. Note
   `FAIL_ON_PROJECT_REPOS`, which stops modules from adding their own repositories.
2. [gradle/libs.versions.toml](../../gradle/libs.versions.toml): read every line. Note the
   comments explaining *why* some versions are pinned (Kotlin must match the version AGP
   bundles; serialization is aligned to what Room's test tools need). Good projects write down
   the reasons for version pins, so do the same in yours.
3. [app/build.gradle.kts](../../app/build.gradle.kts):
   - The plugins block applies the Android plugin, the **Compose compiler plugin**, the
     **serialization plugin**, **KSP** (Kotlin Symbol Processing, which generates Room's code)
     and the **Room plugin**. There's no separate Kotlin Android plugin: AGP 9 has Kotlin
     support built in.
   - [Lines 40–45](../../app/build.gradle.kts#L40): `assets.directories.add(...)` adds
     `shared/assets/` as an extra assets folder, so the iOS and Android apps share one copy of
     the recordings.
   - [Lines 46–49](../../app/build.gradle.kts#L46): `noCompress += "flac"`. APKs are zip files
     and normally compress assets. Module 7 explains why these files must stay uncompressed.
   - [Lines 52–55](../../app/build.gradle.kts#L52): Room exports its database schema as JSON
     into [app/schemas/](../../app/schemas). Those files are committed to git. Module 12
     explains why.
   - `dependencies`: `implementation` is for app code, `testImplementation` for JVM tests,
     `androidTestImplementation` for on-device tests, `ksp(...)` for code generators.
     `platform(libs.androidx.compose.bom)` is a **BOM** (bill of materials): it picks matching
     versions for all Compose libraries, which is why those entries have no version.
4. [AndroidManifest.xml](../../app/src/main/AndroidManifest.xml): `android:name=".EarTrainerApplication"`
   names the Application class. The one Activity has the launcher intent filter. Notice what's
   *missing*: no permissions. This app needs none, because playing audio doesn't require one.
5. [MainActivity.kt](../../app/src/main/java/com/troanhmolo/eartrainer/MainActivity.kt): the
   whole Activity is 20 lines. `enableEdgeToEdge()` lets the app draw behind the status and
   navigation bars, and `setContent { ... }` hands the window to Compose.
6. [AppContainer.kt](../../app/src/main/java/com/troanhmolo/eartrainer/AppContainer.kt#L119):
   `EarTrainerApplication.onCreate()` builds one `AppContainer`. This object holds every
   long-lived dependency. You'll study it in Module 10.

### Source sets

```
app/src/main/          app code and resources
app/src/test/          JVM unit tests: run on your computer, no device
app/src/androidTest/   instrumented tests: run on a device or emulator
```

Look at the package folders under `app/src/main/java/com/troanhmolo/eartrainer/`:

| Package | Contents | Uses Android? |
|---------|----------|---------------|
| `music/` | Pitches, intervals, chords, keys, rhythms, `ListeningPrompt` | No |
| `exercise/` | Questions, generators, the session state machine, weak-spot weighting | No |
| `audio/` | Scheduling, synthesis, sampling, playback | Only `AudioTrackPlayer`, `AssetSampleLoader`, `TransientAudioFocus` |
| `data/` | Room history, DataStore settings, backups | Yes |
| `ui/` | Compose screens and ViewModels, one folder per feature | Yes |

## Exercises

1. **Build and run.** Open the project root in Android Studio. Let the Gradle sync finish, then
   run the app on a device. Do one interval session (20 questions) so you have some history.
2. **Run the tests from the command line.**
   ```bash
   ./gradlew test
   ```
   Then run one class:
   ```bash
   ./gradlew :app:testDebugUnitTest --tests "*.ToneSynthesizerTest"
   ```
   Open `app/build/reports/tests/testDebugUnitTest/index.html` in a browser.
3. **Look inside the APK.** Run `./gradlew :app:assembleDebug`, then in Android Studio use
   *Build → Analyze APK…* on `app/build/outputs/apk/debug/app-debug.apk`. Find the `.flac`
   files under `assets/instruments/`. What does the analyzer say about their compression?
   Compare the size of `classes*.dex` with the size of the assets.
4. **Make a tiny visible change.** In
   [strings.xml](../../app/src/main/res/values/strings.xml), change the app's title string.
   Run the app and check that the change shows up. Then revert it.
5. **Find things fast.** Learn these Android Studio shortcuts (Windows/Linux keys; macOS uses
   ⌘): *Search Everywhere* (Shift Shift), *Go to Declaration* (Ctrl+B), *Find Usages*
   (Alt+F7), *Recent Files* (Ctrl+E). Use them to answer: where is `SESSION_LENGTH` defined,
   and which files use it?
6. **Logcat.** Run the app, open Logcat, and filter by the app's package. Switch instruments
   in any exercise's settings. Is anything logged? (Look for the `AppContainer` tag in
   [AppContainer.kt](../../app/src/main/java/com/troanhmolo/eartrainer/AppContainer.kt#L94).
   When is that line logged?)

## Checkpoint

1. What's the difference between `implementation`, `testImplementation` and `ksp` dependencies?
2. Why do the Compose libraries in `app/build.gradle.kts` have no version numbers?
3. Which object does Android create first: `MainActivity` or `EarTrainerApplication`? How does
   the OS know about the second one?
4. Why can most of the `music/` and `exercise/` code be tested without a device?

<details>
<summary>Answers</summary>

1. `implementation` puts the library in the app. `testImplementation` makes it available only
   to JVM unit tests. `ksp` runs the library as a code generator at compile time (Room's
   compiler generates the DAO implementations) and doesn't ship it in the app.
2. The Compose BOM (`platform(libs.androidx.compose.bom)`) supplies a set of versions known to
   work together.
3. The Application, always, once per process. The manifest's `android:name` attribute on
   `<application>` names it.
4. Those packages don't import any Android classes, so they compile and run on a plain JVM.
   The tests in `app/src/test` run on your computer.

</details>
