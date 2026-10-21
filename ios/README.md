# Ear Trainer for iOS

A native UIKit port of the Android app in `../app`: the same six exercises (intervals, chords,
scale degrees, progressions, melodic dictation, rhythm), weak-spot practice, end-of-session
summaries, progress charts, mistakes with comparison drills, the practice log, the daily goal,
and backup/restore.

## Building

Open `EarTrainer.xcodeproj` in Xcode 16 or later and run the `EarTrainer` scheme. The deployment
target is iOS 17. To run on a device, choose your team under Signing & Capabilities.

From the command line:

```sh
xcodebuild -project EarTrainer.xcodeproj -scheme EarTrainer \
  -destination 'platform=iOS Simulator,name=<your simulator>' test
```

`EarTrainerTests` holds unit tests for the music theory, generators, scheduling, history queries,
backups and settings. `EarTrainerUITests` walks through every screen; set
`TEST_RUNNER_SCREENSHOT_DIR=<folder>` to keep its screenshots.

The project uses synchronized folders, so new files under `EarTrainer/` are picked up without
editing the project.

## How it maps to the Android app

| Android | iOS |
| --- | --- |
| `music/`, `exercise/` | `Music/`, `Exercise/`: the same logic, ported line by line |
| `audio/` (AudioTrack, MediaCodec) | `Audio/`: one pre-rendered buffer per prompt on `AVAudioEngine`; FLAC decoded with `AVAudioFile` |
| Room database | `Data/History/`: SQLite with the same tables, columns and queries |
| Preferences DataStore | `Data/Settings/`: one dictionary in `UserDefaults`, same key names |
| Backup JSON | `Data/Backup/`: the same file format, so backups move between the two apps |
| Flows | Screens reload on `.practiceHistoryDidChange` / `.settingsDidChange` notifications |
| Compose screens | `UI/`: programmatic UIKit; settings use inset-grouped tables, charts are drawn with Core Graphics |

The instrument recordings are not copied: the project bundles `../shared/assets/instruments`,
the same folder the Android app packages, so both apps share one set of samples.

Audio uses the `.playback` session category, so prompts play with the silent switch on. The
session is activated per prompt, pausing other apps' audio, and handed back shortly after the
last prompt, like transient audio focus on Android.
