# Sequence Maker Buddy

**Android companion app for [Sequence Maker](../Projects/ltx_guru/sequence_maker)** — play LED juggling ball color sequences synced with music on your phone.

> Last updated: 2026-03-25T17:47-06:00

## What It Does

This app simulates 3 LED juggling balls as colored circles on screen. You load a `.smbuddy` sequence file (exported from Sequence Maker) and an audio file, then hit Play — the ball colors change in sync with the music, exactly as they would on real hardware.

## How It Works

### Architecture

```
┌─────────────────────────────────────────┐
│  Sequence Maker (PC)                    │
│  └─ BuddyExporter → .smbuddy file      │
│     (combined JSON: 3 balls + metadata) │
└──────────────┬──────────────────────────┘
               │ transfer file to phone
┌──────────────▼──────────────────────────┐
│  Sequence Maker Buddy (Android)         │
│  ├─ Load .smbuddy + audio file          │
│  ├─ SequencePlayerViewModel             │
│  │   ├─ MediaPlayer (audio)             │
│  │   └─ 100Hz coroutine timer           │
│  └─ PlayerScreen (3 ball circles)       │
└─────────────────────────────────────────┘
```

### .smbuddy File Format

A single JSON file containing all ball sequences:

```json
{
  "format": "sequence_maker_buddy",
  "version": 1,
  "project_name": "My Show",
  "audio_filename": "song.mp3",
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

### Key Files

| File | Purpose |
|------|---------|
| `app/.../model/SequenceBundle.kt` | Data model + JSON parser for `.smbuddy` files |
| `app/.../player/SequencePlayerViewModel.kt` | Playback engine: syncs audio + color updates at 100Hz |
| `app/.../ui/PlayerScreen.kt` | Compose UI: 3 ball circles + load/play controls |
| `app/.../MainActivity.kt` | Entry point, wires ViewModel to UI |

### Exporting from Sequence Maker

The buddy exporter lives in the Sequence Maker project at `export/buddy_exporter.py`. Two ways to use it:

**1. From command line (standalone):**
```bash
python3 export/buddy_exporter.py output.smbuddy Ball_1.json Ball_2.json Ball_3.json --audio song.mp3
```

**2. From Sequence Maker app (integrated):**
```python
from export.buddy_exporter import BuddyExporter
exporter = BuddyExporter(app)
exporter.export_project("output.smbuddy")
```

## Usage

1. Export a `.smbuddy` file from Sequence Maker on your PC
2. Transfer the `.smbuddy` file and the audio file to your phone
3. Open Sequence Maker Buddy
4. Tap **Load Sequence** → pick the `.smbuddy` file
5. Tap **Load Audio** → pick the audio file (MP3, WAV, etc.)
6. Tap **▶ Play** — balls light up in sync with the music!

## UI Design

The app uses a **greyscale** color scheme — nearly everything is black and white. The only elements rendered in full color are the **3 simulated ball circles**, since seeing their colors is the entire point of the app. This design choice ensures the ball colors pop and are easy to read at a glance.

*(Greyscale theme applied 2026-03-25T17:47-06:00)*

## Tech Stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **Gson** for JSON parsing
- **MediaPlayer** for audio playback
- **Coroutines** for 100Hz timer loop
- Min SDK 26 (Android 8.0+)

## Building

Open in Android Studio and run, or:

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`
