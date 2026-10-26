# Module 7: Android audio APIs

[Course index](README.md) · Previous: [Module 6](06-coroutines-and-flow.md) · Next: [Module 8: Compose fundamentals](08-compose-basics.md)

## Why it matters

Modules 3–5 produced a `ShortArray`. This module gets it out of the speaker, and back in
from compressed files. You'll learn the Android platform APIs for playing raw PCM, being a
good audio citizen with other apps, and decoding FLAC with the platform codecs. These APIs
are old and stateful, with callbacks and threading rules, so they're also good practice in
wrapping legacy APIs safely.

## Concepts

### The Android audio path

```
your AudioTrack ─► AudioFlinger (system mixer) ─► audio HAL ─► DAC ─► speaker / headphones
   other apps  ─┘
```

The system **mixer** combines all apps' audio at the device's **native sample rate** (usually
48 kHz). If your track uses another rate, the mixer resamples it. That works, but it costs
CPU and quality. Asking the device for its native rate avoids it.

### AudioTrack

`AudioTrack` plays raw PCM. Configure it with:

- **`AudioAttributes`**: *what* the sound is (usage `MEDIA`, content type `MUSIC`). The
  system uses this for routing, which volume slider applies, and how focus and ducking behave.
- **`AudioFormat`**: encoding (16-bit PCM), sample rate, channel mask (mono).
- **Transfer mode**:
  - `MODE_STATIC`: write the whole sound once, then `play()`. Low overhead, can replay,
    ideal for short sounds. This app uses it.
  - `MODE_STREAM`: create a small buffer and keep `write()`-ing into it while it plays. Needed
    for long or generated-on-the-fly audio. Fail to write in time and you get an **underrun**
    (a glitch).

Playback position callbacks (`setNotificationMarkerPosition` +
`OnPlaybackPositionUpdateListener`) report when the hardware reaches a given frame. Those
callbacks arrive on a `Handler`'s thread.

`AudioTrack` holds native resources. You **must** call `release()`, or the system can run out
of tracks.

### Audio focus

Android expects apps to coordinate who's making sound:

- Request focus before playing. `AUDIOFOCUS_GAIN_TRANSIENT` means "briefly, others should
  pause".
- The system can refuse, for example during a phone call.
- Listen for losses: `LOSS` (another app took over), `LOSS_TRANSIENT` (a call or alarm),
  `LOSS_TRANSIENT_CAN_DUCK` (a notification, so you can keep playing quieter).
- Abandon focus when done, so the music app the user was listening to resumes.

API 26 (Android 8.0) introduced `AudioFocusRequest`. Below that you use deprecated overloads,
so code supporting `minSdk 24` needs a version check.

### Decoding compressed audio

FLAC is lossless but compressed. Android decodes it in two steps:

1. **`MediaExtractor`** reads the container and hands out compressed packets ("samples" in
   its API, not to be confused with audio samples) plus the track's `MediaFormat`.
2. **`MediaCodec`** is a decoder with **input buffers** (you fill them with packets) and
   **output buffers** (it fills them with PCM). In synchronous mode you loop: dequeue an input
   buffer, fill it, queue it; dequeue an output buffer, copy it out, release it. You stop when
   you see the `END_OF_STREAM` flag.

The output format can change mid-stream (`INFO_OUTPUT_FORMAT_CHANGED`), and the decoder may
output 16-bit integers or 32-bit floats. Robust code handles both.

### Assets and file descriptors

`AssetManager.openFd()` returns a file descriptor *pointing into the APK*. That only works if
the zip entry is **stored uncompressed**. For a compressed entry there's no contiguous byte
range to point to, and `openFd` throws. Hence `noCompress += "flac"` in
[app/build.gradle.kts](../../app/build.gradle.kts#L46). FLAC is already compressed, so zip
compression saved almost nothing anyway.

## Read the code

### The player

[audio/AudioTrackPlayer.kt](../../app/src/main/java/com/troanhmolo/eartrainer/audio/AudioTrackPlayer.kt)

You read `play()` for its coroutines in Module 6. Now read it for the Android parts.

- [Lines 53–56](../../app/src/main/java/com/troanhmolo/eartrainer/audio/AudioTrackPlayer.kt#L53):
  the native output rate from `AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE`, falling back to
  44.1 kHz. Rendering happens at this rate (Modules 3–5 always took `sampleRate` as a
  parameter for this reason).
- [Lines 58–61](../../app/src/main/java/com/troanhmolo/eartrainer/audio/AudioTrackPlayer.kt#L58):
  attributes: media / music.
- [`createLoadedTrack`, lines 132–158](../../app/src/main/java/com/troanhmolo/eartrainer/audio/AudioTrackPlayer.kt#L132):
  `AudioTrack.Builder` with `MODE_STATIC` and a buffer exactly the size of the PCM. Note the
  error handling: builder exceptions become `AudioPlaybackException` with clear messages, and
  a failed write releases the track before throwing.
- [Lines 94–97](../../app/src/main/java/com/troanhmolo/eartrainer/audio/AudioTrackPlayer.kt#L94):
  the completion marker is set at `contentEndFrame`, *inside* the buffer, because "some
  devices never report a marker placed on the final frame of a static buffer". That's
  device-specific knowledge again, written down where it applies.
- [`begin` / `end` / `silence`, lines 161–194](../../app/src/main/java/com/troanhmolo/eartrainer/audio/AudioTrackPlayer.kt#L161):
  only one prompt sounds at a time. A `lock` guards `active` and `released`, because the
  focus-loss callback (main thread) and coroutines (background threads) can touch them
  concurrently. Note the ownership rule: only the playback that's still active gives up focus,
  so a replaced prompt can't abandon focus the new prompt holds.
- [Lines 69–71, 125–130](../../app/src/main/java/com/troanhmolo/eartrainer/audio/AudioTrackPlayer.kt#L125):
  replays reuse the last rendered buffer when the prompt and instrument are equal. Rendering
  a chord with a sampled instrument costs real CPU time. `@Volatile` makes the cached
  reference visible across threads.

### Audio focus

[audio/TransientAudioFocus.kt](../../app/src/main/java/com/troanhmolo/eartrainer/audio/TransientAudioFocus.kt):
the API-level split (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.O`), the deprecated
fallback with `@Suppress("DEPRECATION")`, and the deliberate decision (in the KDoc) to
**ignore** "can duck" losses: on API 26+ the system ducks automatically, and stopping the
prompt for every notification would be worse.

### Lifecycle: stop when hidden, release when gone

- [PracticeHost](../../app/src/main/java/com/troanhmolo/eartrainer/ui/practice/PracticeScreen.kt#L98):
  `LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onScreenHidden() }`. Audio stops
  when the app goes to the background.
- [PracticeViewModel.onCleared](../../app/src/main/java/com/troanhmolo/eartrainer/ui/practice/PracticeViewModel.kt#L219):
  the player is released when the screen is destroyed for good.
- [IntervalTrainingRoute](../../app/src/main/java/com/troanhmolo/eartrainer/ui/intervals/IntervalPractice.kt#L69):
  the comment explains that ViewModel scoping (Module 11) is what makes this work.

### The decoder

[audio/AssetSampleLoader.kt](../../app/src/main/java/com/troanhmolo/eartrainer/audio/AssetSampleLoader.kt)

- [`decode`, lines 33–50](../../app/src/main/java/com/troanhmolo/eartrainer/audio/AssetSampleLoader.kt#L33):
  `openFd` → `setDataSource(fd, offset, length)`. The descriptor plus offset and length is
  how a file *inside* the APK is addressed. Then find the first `audio/` track, select it,
  and read the sample rate.
- [`decodeInto`, lines 53–99](../../app/src/main/java/com/troanhmolo/eartrainer/audio/AssetSampleLoader.kt#L53):
  the synchronous codec loop. Trace one iteration with a pencil: input side (lines 65–77), then
  output side (lines 78–92). Note `TIMEOUT_US = 10_000`: each dequeue waits at most 10 ms, so
  the loop alternates between feeding and draining.
- [`appendFrames`, lines 102–125](../../app/src/main/java/com/troanhmolo/eartrainer/audio/AssetSampleLoader.kt#L102):
  handles float and 16-bit output, downmixes channels by averaging, and uses
  `ByteOrder.nativeOrder()` because codec buffers use the platform's byte order.
- `finally` blocks release the extractor and codec. Like `AudioTrack`, these hold native
  resources.

## Exercises

Branch: `git switch -c learning/module-07`. Use a real phone.

1. **Know your device.** Log `PROPERTY_OUTPUT_SAMPLE_RATE` and
   `PROPERTY_OUTPUT_FRAMES_PER_BUFFER` at startup. Compare an emulator with your phone, and
   your phone's speaker with Bluetooth headphones. What do you think frames-per-buffer means
   for latency?
2. **See the fallback.** Remove `noCompress += "flac"` and run the app. Does it crash? Find the
   `AppContainer` warning in Logcat and read the exception. What does the app sound like now,
   and why? Restore the line.
3. **Be a good citizen.** Play music in another app, then play a prompt. What happens to the
   music, and when does it come back? Now change `AUDIOFOCUS_GAIN_TRANSIENT` to
   `AUDIOFOCUS_GAIN` and repeat. Then try `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`. Which is right
   for an ear trainer, and why?
4. **Interrupt it.** Set an alarm for one minute ahead and keep replaying a long prompt
   (a progression). What does the app do when the alarm fires? Trace the path from
   `listener` in `TransientAudioFocus` to `play()` returning.
5. **Measure decoding.** Time `AssetSampleLoader.load` for each instrument with
   `System.nanoTime()`. Try `PARALLEL_DECODES` = 1, 2, 4 and 8. Plot the results. Where's the
   knee, and why might it differ on another phone?
6. **Leak a track.** Comment out `playback.track.release()` in `end()`. Replay a prompt
   several hundred times (write an instrumented test that loops). What happens eventually?
   Look for `AudioTrack` errors in Logcat. Restore the line.
7. **Stretch: a streaming metronome.** Write `MetronomePlayer` using `MODE_STREAM`: a loop on
   `Dispatchers.Default` writes chunks of ~10 ms, and clicks are placed by **counting frames**
   (not with `delay`). Tempo changes take effect on the next beat. Use `track.underrunCount`
   to detect glitches. This is the architecture behind every real-time audio app.
8. **Further reading.** For truly low-latency audio (instruments, games), Android offers
   **AAudio** and Google's C++ library [Oboe](https://github.com/google/oboe). Read Oboe's
   "Getting Started" and "Full Guide" to see how a real-time callback differs from what this
   app does.

## Checkpoint

1. When would you choose `MODE_STREAM` over `MODE_STATIC`?
2. Why does the app render at the device's native sample rate?
3. What does the app do when it loses transient audio focus, and when it's asked to duck?
4. Why must the FLAC assets be stored uncompressed in the APK?
5. Name three objects in this module that must be explicitly released.
6. Why does `AudioTrackPlayer` need a lock at all, when coroutines are "single-threaded by
   default"?

<details>
<summary>Answers</summary>

1. When the sound is long, open-ended, generated in real time, or must react quickly to
   changes (a metronome, a synthesizer keyboard, streaming music).
2. So the system mixer doesn't have to resample it, which saves CPU and keeps the quality
   the renderer produced.
3. On `LOSS` or `LOSS_TRANSIENT` it silences the active track, so `play()` returns. For
   `CAN_DUCK` it does nothing and lets the system lower its volume (API 26+).
4. `openFd` needs a contiguous uncompressed byte range inside the APK to pass to
   `MediaExtractor`. Compressed entries can't be opened that way.
5. `AudioTrack`, `MediaCodec`, `MediaExtractor` (all hold native resources).
6. Coroutines here run on `Dispatchers.Default` (a thread pool), and the focus and marker
   callbacks run on the main thread. Several threads can touch `active` at once, so it needs
   synchronization.

</details>
