# Sequence Maker Buddy

**Android companion app for [Sequence Maker](../Projects/ltx_guru/sequence_maker)** — play LED juggling ball color sequences synced with music on your phone.

> Last updated: 2026-05-10T15:40+01:00

## What It Does

This app simulates 3 LED juggling balls as colored circles on screen. You configure a folder where your `.smbuddy` files live, browse them from within the app, and hit Play — the ball colors change in sync with the music, exactly as they would on real hardware.

You can also load external audio files from a separate audio folder, and adjust the audio delay to fine-tune synchronization between the sequence and the music.

## How It Works

### Architecture

```
┌─────────────────────────────────────────┐
│  Sequence Maker (PC)                    │
│  └─ BuddyExporter → .smbuddy file      │
│     (ZIP: sequence.json + audio file)   │
│     Remembers last export directory     │
└──────────────┬──────────────────────────┘
               │ transfer .smbuddy to phone
┌──────────────▼──────────────────────────┐
│  Sequence Maker Buddy (Android)         │
│  ├─ Settings: configure folders         │
│  │   ├─ .smbuddy folder                │
│  │   └─ Audio folder                   │
│  ├─ File browser: pick sequence         │
│  ├─ Audio browser: pick audio file      │
│  ├─ SequencePlayerViewModel             │
│  │   ├─ Extracts JSON + audio from ZIP  │
│  │   ├─ MediaPlayer (audio)             │
│  │   ├─ Audio delay (±10s)             │
│  │   ├─ Delay labels (saved presets)    │
│  │   └─ 100Hz coroutine timer           │
│  └─ PlayerScreen (3 ball circles)       │
└─────────────────────────────────────────┘
```

### .smbuddy File Format (v2)

A **ZIP archive** containing:
- `sequence.json` — The sequence data
- `audio.<ext>` — The audio file (mp3, wav, etc.) from the project

The `sequence.json` inside the ZIP:

```json
{
  "format": "sequence_maker_buddy",
  "version": 2,
  "project_name": "My Show",
  "audio_filename": "audio.mp3",
  "refresh_rate": 100,
  "balls": [
    {
      "name": "Ball 1",
      "default_pixels": 4,
      "sequence": {
        "0": [255, 0, 0],
        "100": [0, 255, 0],
        "200": [0, 0, 255]
      }
    }
  ]
}
```

- Keys in `sequence` are time in **centiseconds** (100Hz, so `"100"` = 1 second)
- Values are `[R, G, B]` arrays (0-255)
- Audio is bundled inside the ZIP — no separate file transfer needed

### Audio Delay

The delay slider controls the offset between the sequence and the audio:

- **Positive delay** (0 to +10s): Adds silence before the audio starts. The sequence begins immediately, but the audio track waits. Useful when the audio should start later than the sequence.
- **Negative delay** (0 to −10s): Skips the beginning of the audio. Both the sequence and audio start together, but the audio begins partway through. Useful when the audio should start earlier than the sequence.
- **0 delay** (default): Sequence and audio start at the same time.

The delay can be adjusted in 0.05s increments using the +/− buttons, or by dragging the slider.

### Delay Labels

You can save the current delay value with a descriptive label (e.g., "Chorus sync", "Verse offset"). Labels are:
- Saved per sequence (persisted across sessions)
- Quickly applied via a dropdown
- Deletable from the same dropdown

### Per-Sequence Audio Memory

When you open a sequence, the app automatically restores:
- The last audio file you had open with that sequence
- The delay setting and saved labels

This means you only need to set up the audio once per sequence — next time you open it, everything is as you left it.

### Key Files

| File | Purpose |
|------|---------|
| `app/.../model/SequenceBundle.kt` | Data model + ZIP/JSON parser for `.smbuddy` files |
| `app/.../model/AudioState.kt` | Delay label + per-sequence audio state data classes |
| `app/.../player/SequencePlayerViewModel.kt` | Playback engine: audio, delay, labels, file browsing |
| `app/.../settings/SettingsManager.kt` | Persists folder locations + per-sequence audio state |
| `app/.../ui/PlayerScreen.kt` | Compose UI: settings, browsers, balls, play controls, delay |
| `app/.../ui/FileBrowserDialog.kt` | Popup dialog listing .smbuddy files, sortable by name or date |
| `app/.../ui/AudioBrowserDialog.kt` | Popup dialog listing audio files from the audio folder |
| `app/.../ui/DelayControls.kt` | Delay slider, increment buttons, label dropdown, add-label dialog |
| `app/.../MainActivity.kt` | Entry point, wires ViewModel to UI |

### Exporting from Sequence Maker

The buddy exporter lives in the Sequence Maker project at `export/buddy_exporter.py`. It now creates a ZIP bundle that includes the audio file automatically.

**Key improvements:**
- The export dialog **remembers the last export directory** across sessions
- The audio file from the open project is **automatically bundled** into the .smbuddy ZIP
- Only **one file** needs to be transferred to the phone (no separate audio file)

**1. From command line (standalone):**
```bash
python3 export/buddy_exporter.py output.smbuddy Ball_1.json Ball_2.json Ball_3.json --audio path/to/song.mp3
```

**2. From Sequence Maker app (integrated):**
```python
from export.buddy_exporter import BuddyExporter
exporter = BuddyExporter(app)
exporter.export_project("output.smbuddy")
```

## Usage

1. Export a `.smbuddy` file from Sequence Maker on your PC (audio is included automatically)
2. Transfer the `.smbuddy` file to a folder on your phone
3. Open Sequence Maker Buddy
4. Tap **⚙** (settings) → **Select Folder** → pick the folder with your `.smbuddy` files
5. Optionally, also set an **Audio Folder** in settings (for loading external audio files)
6. Tap **Open Sequence** → pick a file from the list (sortable by name or date)
7. Optionally, tap **Open Audio** → pick an audio file (overrides bundle audio)
8. Adjust the **delay slider** if the audio/sequence timing needs fine-tuning
9. Use the **time slider** to scrub to any point in the sequence
10. Tap **▶ Play** — balls light up in sync with the music!

## UI Design

The app uses a **greyscale** color scheme — nearly everything is black and white. The only elements rendered in full color are the **3 simulated ball circles**, since seeing their colors is the entire point of the app. This design choice ensures the ball colors pop and are easy to read at a glance. Playback control icons (stop, pause) use plain text glyphs in greyscale to stay consistent with the theme.

A **time slider** sits below the time display, allowing you to scrub to any point in the sequence/song. Dragging the slider seeks both the audio and the sequence position. The total duration is shown at the end of the slider.

The **delay slider** ranges from −10s to +10s with 0 (no delay) in the center. Increment/decrement buttons adjust by 0.05s. Saved delay labels appear in a dropdown for quick recall.

The file browser is a popup dialog with a scrollable list of `.smbuddy` files, sortable by **name** or **date**. The audio browser similarly lists audio files (mp3, wav, ogg, flac, aac, m4a, wma, opus) from the configured audio folder.

*(Greyscale theme applied 2026-03-25T17:47-06:00)*
*(ZIP bundle format + folder-based browsing added 2026-03-25T17:59-06:00)*
*(Time slider + greyscale playback icons added 2026-03-25T18:11-06:00)*
*(Open Audio + delay slider + delay labels + per-sequence audio memory added 2026-05-10T15:40+01:00)*

## Tech Stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **Gson** for JSON parsing
- **MediaPlayer** for audio playback
- **Coroutines** for 100Hz timer loop
- **DocumentFile** + Storage Access Framework for folder browsing
- **SharedPreferences** for persisting settings and per-sequence audio state
- Min SDK 26 (Android 8.0+)

## Building

Open in Android Studio and run, or:

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`
