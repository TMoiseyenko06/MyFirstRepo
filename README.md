# BeatShow

An Android photo slideshow whose slide changes are locked to a metronome beat. After a random
number of slides the metronome jumps to a new random tempo, always on a bar boundary.

## Download

The APK is built by GitHub Actions (`.github/workflows/build.yml`) on every push:
open the **Actions** tab, pick the latest green **Build APK** run, and download the
**BeatShow-apk** artifact (a zip containing `app-debug.apk`). Pushing a tag such as `v1.0`
also attaches the APK to a GitHub Release.

## How timing works

- `engine/Conductor.kt` schedules beats, slide changes and tempo switches in audio sample frames.
  Beat *k* of a tempo lands at exactly `start + round(k * sampleRate * 60 / bpm)`, so nothing drifts.
- `audio/MetronomeEngine.kt` streams PCM through `AudioTrack` (MODE_STREAM) and mixes the
  synthesized clicks in at those exact sample offsets.
- The UI reads the audio clock (`AudioTrack.getTimestamp`) every display frame and applies each
  slide change once the speaker has reached its frame, so pictures and clicks stay together.

## Settings and defaults

| Setting | Default | Range |
|---|---|---|
| Slide timing mode | Beat-sync | Beat-sync / Fixed seconds |
| Beats per slide | 4 | 1–16 |
| Seconds per slide (fixed mode) | 3.0 s | 0.5–30 s |
| Crossfade | 300 ms | 0–1000 ms |
| Image scaling | Fit (black bars) | Fit / Fill (crop) |
| Show BPM overlay | On | |
| Metronome sound | On | |
| Volume | 80% | 0–100% |
| Beats per bar | 4 | 2–7 |
| Click sound | Woodblock | Woodblock, Beep, Hi-hat, Cowbell |
| Min / max slides before tempo switch | 2 / 10 | 1–30 |
| Min / max BPM | 70 / 140 | 40–240 (kept at least 10 apart) |
| Randomize click sound on switch | Off | |
| Randomize time signature on switch | Off | |

## Building locally

Requires JDK 17+ and the Android SDK: `./gradlew testDebugUnitTest assembleDebug`.
