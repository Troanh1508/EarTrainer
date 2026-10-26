# Module 14: Custom drawing with Canvas

[Course index](README.md) · Previous: [Module 13](13-files-and-backup.md) · Next: [Module 15: Testing strategy and release builds](15-testing-and-release.md)

## Why it matters

Material components cover buttons and lists, but music apps need things no library ships:
rhythm notation, piano keyboards, waveforms, charts. Compose's `Canvas` gives you a
`DrawScope` where you draw lines, arcs and paths yourself. This module joins the UI and audio
tracks: by the end you'll draw the actual samples your synthesizer produced.

Read [Graphics in Compose](https://developer.android.com/develop/ui/compose/graphics/draw/overview).

## Concepts

- **`Canvas(modifier) { … }`**: a composable whose lambda receives a `DrawScope`. Its size
  comes from the modifier (`fillMaxWidth().height(64.dp)`). `size.width` and `size.height` are
  in **pixels**.
- **Density**: design in `dp`, draw in `px`. Inside `DrawScope`, `8.dp.toPx()` converts using
  the screen density. Never hard-code pixel values, or the drawing will look tiny on
  high-density screens.
- **Primitives**: `drawLine`, `drawRect`, `drawRoundRect`, `drawCircle`, `drawArc`, `drawPath`,
  and `drawText` (with a `TextMeasurer`). `Stroke(width, cap, join)` vs the default fill.
- **`Path`**: `moveTo`, `lineTo`, `cubicTo`, `close()`. Use it for any custom shape.
- **Coordinate mapping**: every chart is a function from data space to pixel space, like
  `y(value) = bottom − (value − low) / (high − low) · (bottom − top)`. Write it as a local
  function and the rest of the drawing code becomes simple.
- **Accessibility**: a Canvas is invisible to TalkBack. Give the container a
  `contentDescription` that says what the drawing shows.
- **Performance**: the draw lambda runs on every frame that needs redrawing. Compute
  expensive things outside it (in `remember`), and read animated state *inside* the draw
  phase where possible, so only drawing re-runs, not composition.

## Read the code

### Rings and donuts

- [HomeScreen `Ring`, line 163](../../app/src/main/java/com/troanhmolo/eartrainer/ui/home/HomeScreen.kt#L163)
  and [`AccuracyDonut`, lines 59–99](../../app/src/main/java/com/troanhmolo/eartrainer/ui/progress/ProgressCharts.kt#L59):
  a full-circle "track" arc, then a progress arc starting at −90° (12 o'clock) sweeping
  `360 · fraction`. Note the inset `topLeft = stroke / 2`: a stroke is centered on its path,
  so without the inset half the ring would be clipped. Text is a normal composable stacked on
  top in a `Box`.
- `clearAndSetSemantics { contentDescription = description }`: TalkBack reads one sentence
  instead of the parts.

### A line chart

[`AccuracyTrendChart`, lines 107–159](../../app/src/main/java/com/troanhmolo/eartrainer/ui/progress/ProgressCharts.kt#L107):

- `fun y(value: Float)` is the coordinate mapping (line 132).
- The y-axis starts at 50% when every session is above half (line 109), so normal variation
  is visible. That's a design decision documented in the KDoc.
- Two paths from the same points: `outline` (stroked) and `area` (closed down to the baseline,
  filled at 12% alpha).
- The last point is drawn as a ring (a filled circle in the surface color, then a stroked
  circle on top) to mark "latest".
- Axis labels are ordinary `Text` composables beside the Canvas, laid out so the gridlines line
  up with them (comment on line 128).
- `remember { SimpleDateFormat(…) }`: built once, not per recomposition.

### Music notation

[ui/rhythm/RhythmNotation.kt](../../app/src/main/java/com/troanhmolo/eartrainer/ui/rhythm/RhythmNotation.kt):

- One Canvas per bar ([line 38](../../app/src/main/java/com/troanhmolo/eartrainer/ui/rhythm/RhythmNotation.kt#L38)).
  The KDoc explains it keeps sixteenths legible on a phone.
- [`drawBar`, lines 44–84](../../app/src/main/java/com/troanhmolo/eartrainer/ui/rhythm/RhythmNotation.kt#L44):
  an extension function on `DrawScope`. `xOf(beat)` maps musical time to x, so longer notes
  take more room. Notes shorter than a beat collect their stems for beaming.
- [`NotationGlyphs`, line 87 onward](../../app/src/main/java/com/troanhmolo/eartrainer/ui/rhythm/RhythmNotation.kt#L87):
  noteheads (rotated ovals), stems, beams, flags and rests from primitives, with all sizes in
  dp converted once.
- The KDoc says "Purely visual: callers provide the spoken description for accessibility".
  See how `AnswerGrid` passes the text label as the content description when a graphic is
  shown (Module 8).

## Exercises

Branch: `git switch -c learning/module-14`.

1. **Tweak and preview.** Open the `RhythmNotationPreview`. Change the stem height and the
   notehead tilt. Add a time signature ("4/4") at the start of each bar using `drawText` with
   `rememberTextMeasurer()`.
2. **A Canvas piano keyboard.** Redo Module 8's keyboard with one `Canvas`: white keys as
   rounded rects, black keys drawn on top at the right positions (there's no black key between
   E–F and B–C). Highlight played pitches in `primary`, and the correct answer's notes in
   `tertiary`. Make it accessible with a description like "C4, E4 highlighted". Detect taps
   with `Modifier.pointerInput { detectTapGestures { offset -> … } }` and map the offset back
   to a `Pitch` (check black keys first). Then make tapping a key play that note.
3. **Draw your synthesizer's output.** Make a `WaveformView(pcm: ShortArray)`. A prompt has
   ~100,000 samples and the screen ~1,000 pixels, so for each pixel column compute the **min
   and max** sample in its range and draw a vertical line between them (a peak envelope). Do
   that reduction in `remember(pcm) { … }`, not in the draw lambda. Show it in a debug screen
   that renders a prompt with `ToneSynthesizer` + `NoteSchedule.from(prompt, 48_000)`. Can you
   see the envelope's attack, decay and release? The silent gap between melodic notes?
4. **Zoom.** Add pinch-to-zoom to the waveform (`detectTransformGestures`) so you can zoom in
   until individual sine cycles are visible. At what zoom level does min/max stop being
   necessary?
5. **Stretch: a playhead.** While a prompt plays, draw a vertical line moving across the
   waveform. A first version uses elapsed time from `withFrameNanos`. Why can that drift from
   what you hear? The precise version asks the audio system where it is
   (`AudioTrack.getTimestamp` or `playbackHeadPosition`). Expose that from `AudioPlayer`
   without leaking `AudioTrack` into the UI layer.
6. **Stretch: a spectrum.** Implement a radix-2 FFT (about 40 lines of Kotlin; see dspguide.com,
   chapter 12) and draw the magnitude spectrum of one note with log-scaled frequency. Compare a
   sine, the default synthesizer harmonics, and a sawtooth. You've rebuilt Audacity's "Plot
   Spectrum" from Module 3.

## Checkpoint

1. Why must drawing code convert `dp` to `px`?
2. Why does `AccuracyDonut` inset the arc by half the stroke width?
3. How do you make a Canvas drawing accessible?
4. You need to draw 100,000 samples into 1,000 pixels. What do you compute, and where?
5. Why might a playhead driven by frame time drift from the audio?

<details>
<summary>Answers</summary>

1. Screens have different densities. Sizes defined in dp look the same physical size
   everywhere, and `DrawScope` works in pixels.
2. Strokes are centered on the path. Without the inset, half the stroke falls outside the
   Canvas bounds and gets clipped.
3. Put a `contentDescription` (often with `clearAndSetSemantics`) on the Canvas or its
   container describing what it shows.
4. A min/max (peak) envelope per pixel column, computed once per PCM buffer in `remember`,
   outside the draw lambda.
5. Audio starts with some output latency, and the audio clock and the display clock are
   independent. Frame time only estimates where the audio is. Asking the audio system for its
   timestamp measures it.

</details>
