# Sequence Maker Buddy

**Android companion app for [Sequence Maker](../Projects/ltx_guru/sequence_maker)** — play LED juggling ball color sequences synced with music on your phone.

> Last updated: 2026-03-25T18:11-06:00

## What It Does

This app simulates 3 LED juggling balls as colored circles on screen. You configure a folder where your `.smbuddy` files live, browse them from within the app, and hit Play — the ball colors change in sync with the music, exactly as they would on real hardware.

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
│  ├─ Settings: configure .smbuddy folder │
│  ├─ File browser: pick from folder      │
│  ├─ SequencePlayerViewModel             │
│  │   ├─ Extracts JSON + audio from ZIP  │
│  │   ├─ MediaPlayer (audio from bundle) │
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

### Key Files

| File | Purpose |
|------|---------|
| `app/.../model/SequenceBundle.kt` | Data model + ZIP/JSON parser for `.smbuddy` files |
| `app/.../player/SequencePlayerViewModel.kt` | Playback engine: extracts audio from bundle, syncs color updates at 100Hz |
| `app/.../settings/SettingsManager.kt` | Persists .smbuddy folder location across app sessions |
| `app/.../ui/PlayerScreen.kt` | Compose UI: settings, file browser trigger, 3 ball circles + play controls |
| `app/.../ui/FileBrowserDialog.kt` | Popup dialog listing .smbuddy files, sortable by name or date |
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
2. Transfer the single `.smbuddy` file to a folder on your phone
3. Open Sequence Maker Buddy
4. Tap **⚙** (settings) → **Select Folder** → pick the folder with your `.smbuddy` files
5. Tap **Open Sequence** → pick a file from the list (sortable by name or date)
6. Use the **time slider** to scrub to any point in the sequence
7. Tap **▶ Play** — balls light up in sync with the music!

## UI Design

The app uses a **greyscale** color scheme — nearly everything is black and white. The only elements rendered in full color are the **3 simulated ball circles**, since seeing their colors is the entire point of the app. This design choice ensures the ball colors pop and are easy to read at a glance. Playback control icons (stop, pause) use plain text glyphs in greyscale to stay consistent with the theme.

A **time slider** sits below the time display, allowing you to scrub to any point in the sequence/song. Dragging the slider seeks both the audio and the sequence position. The total duration is shown at the end of the slider.

The file browser is a popup dialog with a scrollable list of `.smbuddy` files, sortable by **name** or **date**. No separate import buttons — just configure the folder once in settings, then browse and tap to load.

*(Greyscale theme applied 2026-03-25T17:47-06:00)*
*(ZIP bundle format + folder-based browsing added 2026-03-25T17:59-06:00)*
*(Time slider + greyscale playback icons added 2026-03-25T18:11-06:00)*

## Tech Stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **Gson** for JSON parsing
- **MediaPlayer** for audio playback
- **Coroutines** for 100Hz timer loop
- **DocumentFile** + Storage Access Framework for folder browsing
- **SharedPreferences** for persisting settings
- Min SDK 26 (Android 8.0+)

## Building

Open in Android Studio and run, or:

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`
