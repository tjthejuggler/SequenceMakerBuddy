# Sequence Maker Buddy

**Android companion app for [Sequence Maker](../Projects/ltx_guru/sequence_maker)** — play LED juggling ball color sequences synced with music on your phone, with support for connecting to real LTX juggling balls over WiFi.

> Last updated: 2026-05-11T09:25+01:00

## What It Does

This app simulates 3 LED juggling balls as colored circles on screen. You configure a folder where your `.smbuddy` files live, browse them from within the app, and hit Play — the ball colors change in sync with the music, exactly as they would on real hardware.

You can also load external audio files from a separate audio folder, and adjust the audio delay to fine-tune synchronization between the sequence and the music.

**Real Ball Support:** Connect to real LTX juggling balls on the same WiFi network. The app can automatically discover balls, upload PRG sequence files to them, and send play/stop commands in sync with the simulated playback.

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
│  ├─ Ball Manager                        │
│  │   ├─ UDP discovery (port 41412)      │
│  │   ├─ Auto-assign to 3 slots          │
│  │   ├─ PRG generation per ball         │
│  │   ├─ TCP upload (port 8888)          │
│  │   └─ UDP play/stop (port 41412)      │
│  ├─ SequencePlayerViewModel             │
│  │   ├─ Extracts JSON + audio from ZIP  │
│  │   ├─ MediaPlayer (audio)             │
│  │   ├─ Audio delay (±10s)             │
│  │   ├─ Delay labels (saved presets)    │
│  │   └─ 100Hz coroutine timer           │
│  └─ PlayerScreen (3 ball circles)       │
│      ├─ Green ring = real ball connected│
│      ├─ Scan Balls button               │
│      └─ Upload button                   │
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

### Real Ball Connection

The app can connect to real LTX juggling balls over WiFi. The protocol was reverse-engineered from the official LTX Remote app:

1. **Ball Discovery:** Balls broadcast UDP packets on port 41412 containing the identifier `NPLAYLTXBALL`. The app listens for these broadcasts and automatically discovers balls on the same network. A Wi-Fi `MulticastLock` is acquired while scanning so Android's chipset stops filtering out the broadcast packets (without it the scan was unreliable — fixed 2026-05-10).

2. **Auto-Assignment:** Discovered balls are automatically assigned to the 3 ball slots in discovery order (1st discovered → Ball 1, etc.). A green ring appears around each simulated ball that has a real ball connected.

3. **PRG Generation:** The app generates `.prg` binary files (the native ball sequence format) from the loaded sequence data. Each of the 3 ball timelines produces a separate PRG file. The PRG format uses 100Hz refresh rate with solid color segments.

4. **Upload:** PRG files are uploaded to balls via TCP port 8888 using the LTX upload protocol (16-byte header + filename + PRG data).

5. **Play/Stop:** When you press Play, the app simultaneously sends a UDP PLAY command (port 41412) to all connected balls. When you press Stop or Pause, it sends a STOP command.

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

### Audio Sync Calibration

Different audio output paths have wildly different latencies — phone speakers might be ~50ms behind, while Bluetooth headphones can be 150–250ms behind, and real LTX balls have their own WiFi+firmware delay. The **calibration wizard** measures whatever situation you're actually in and produces a delay value that compensates for it.

**Two-phase tap test:**

1. **Audio phase**: The app plays a synthesized WAV with 6 short, sharp beeps at known timestamps (1.5s spacing). The **first beep is an explicit warm-up** — the UI tells you NOT to tap on it. You tap a big button the moment you *hear* each of the remaining 5 beeps. The app records `tap_time − scheduled_beep_time` for each beep, drops the warm-up, and takes the median of the rest. This is your **audio perceived latency** (audio output latency + your reaction time).
2. **Visual phase**: The app emits 6 bright flashes on a square in the dialog at the same schedule. Same warm-up rule — first flash is "don't tap, just watch". You tap the moment you *see* each of the remaining 5 flashes. Same math gives you **visual perceived latency** (frame rendering latency + your reaction time).
3. **Verification**: A **"🔁 Verify Sync"** button on the **main screen** (next to the Calibrate button) plays 3 synchronized beep+flash pairs using the currently-applied delay so you can confirm the result is in sync. The inline flash square appears right below the delay slider while verifying. The workflow is: run calibration → save preset → close dialog → fine-tune with the slider → tap Verify → repeat until perfect. Verification lives on the main screen (not inside the dialog) so the slider and the verify run are visible at the same time.

Because your reaction time is roughly the same in both phases, it cancels out:

```
delaySeconds = (visual_perceived_ms − audio_perceived_ms) / 1000
```

For Bluetooth headphones, where audio is heard *later* than the flash is seen, this comes out **negative** → the audio is started earlier (skips ahead) by that amount, which compensates exactly.

**You decide what to look at**: the on-screen ball circles, or the real LTX balls if they're connected. The code doesn't know or care — name the saved preset accordingly (`"AirPods Pro"`, `"Phone speaker + real balls"`, etc.).

**Calibration presets** are stored device-wide (independent of any particular sequence) and appear in their own **"Calibration Presets"** section in the delay controls (always visible, with a placeholder message when empty). Tap a preset to apply its delay to the currently-loaded sequence. The preset dropdown is **not gated on having a sequence loaded** — presets describe a hardware situation, not a sequence-specific tweak.

The beep WAV is generated in-memory at runtime by [`CalibrationToneGenerator`](app/src/main/java/com/example/sequencemakerbuddy/calibration/CalibrationToneGenerator.kt:1) (no asset bundling) so the schedule and the tone are guaranteed to stay in sync.

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
| `app/.../ball/PrgGenerator.kt` | Generates PRG binary files from BallSequence data (ported from Python) |
| `app/.../ball/LtxBallClient.kt` | Network protocol client: upload (TCP/8888), play/stop (UDP/41412) |
| `app/.../ball/BallManager.kt` | Ball discovery, auto-assignment, upload orchestration |
| `app/.../player/SequencePlayerViewModel.kt` | Playback engine: audio, delay, labels, file browsing, ball integration |
| `app/.../settings/SettingsManager.kt` | Persists folder locations + per-sequence audio state |
| `app/.../ui/PlayerScreen.kt` | Compose UI: settings, browsers, balls, play controls, delay, ball controls |
| `app/.../ui/FileBrowserDialog.kt` | Popup dialog listing .smbuddy files, sortable by name or date |
| `app/.../ui/AudioBrowserDialog.kt` | Popup dialog listing audio files from the audio folder |
| `app/.../ui/DelayControls.kt` | Delay slider, increment buttons, label dropdown, calibration preset dropdown, calibrate button |
| `app/.../ui/CalibrationDialog.kt` | Two-phase calibration wizard UI (audio tap test + visual tap test + save preset) |
| `app/.../calibration/CalibrationToneGenerator.kt` | In-memory WAV synthesis with beeps at known timestamps |
| `app/.../calibration/CalibrationEngine.kt` | State machine that runs the audio + visual phases, records taps, computes median latency, derives final delay |
| `app/.../calibration/CalibrationPreset.kt` | Data class for a named device-wide calibration preset |
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

### Connecting Real Balls

1. Make sure your phone is on the same WiFi network as the LTX balls
2. Tap **📡 Scan Balls** — the app listens for ball broadcasts on the network
3. Discovered balls appear automatically with a **green ring** around their simulated circle
4. The ball's IP address is shown below its label
5. Tap **⬆ Upload** to generate PRG files and upload them to all connected balls
6. When you press **▶ Play**, the sequence starts on both the simulated and real balls simultaneously

## UI Design

The app uses a **greyscale** color scheme — nearly everything is black and white. The only elements rendered in full color are the **3 simulated ball circles**, since seeing their colors is the entire point of the app. This design choice ensures the ball colors pop and are easy to read at a glance. Playback control icons (stop, pause) use plain text glyphs in greyscale to stay consistent with the theme.

A **time slider** sits below the time display, allowing you to scrub to any point in the sequence/song. Dragging the slider seeks both the audio and the sequence position. The total duration is shown at the end of the slider.

The **delay slider** ranges from −10s to +10s with 0 (no delay) in the center. Increment/decrement buttons adjust by 0.05s. Saved delay labels appear in a dropdown for quick recall.

**Ball connection indicators:** When a real ball is connected, a **green ring** appears around the simulated ball circle, and the ball's IP address is displayed below the label. The **📡 Scan Balls** button turns green while scanning is active.

*(Greyscale theme applied 2026-03-25T17:47-06:00)*
*(ZIP bundle format + folder-based browsing added 2026-03-25T17:59-06:00)*
*(Time slider + greyscale playback icons added 2026-03-25T18:11-06:00)*
*(Open Audio + delay slider + delay labels + per-sequence audio memory added 2026-05-10T15:40+01:00)*
*(Real ball connection: discovery, PRG generation, upload, play/stop added 2026-05-10T16:40+01:00)*
*(Audio sync calibration wizard + device-wide presets added 2026-05-10T18:25+01:00)*
*(Calibration: explicit warm-up banner, in-dialog "Test Sync" verification, always-visible presets section added 2026-05-11T09:00+01:00)*
*(Verification moved out of the calibration dialog onto the main screen — single "🔁 Verify Sync" button next to Calibrate, with inline flash square so you can fine-tune the slider and verify in one place — 2026-05-11T09:15+01:00)*
*(Calibration now drives the physical LTX balls: dedicated "📤 Upload PRG" and "🔔 Test Start" buttons in the calibration dialog, and the visual / verify phases automatically send the same PLAY UDP frame used by normal playback so the balls flash in sync — pick whichever you watch (phone screen vs. balls) and name the preset accordingly — 2026-05-11T09:25+01:00)*

## Tech Stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **Gson** for JSON parsing
- **MediaPlayer** for audio playback
- **Coroutines** for 100Hz timer loop + ball network I/O
- **DocumentFile** + Storage Access Framework for folder browsing
- **SharedPreferences** for persisting settings and per-sequence audio state
- **java.net.Socket** for TCP upload to balls
- **java.net.DatagramSocket** for UDP discovery and play/stop commands
- Min SDK 26 (Android 8.0+)

## Building

Open in Android Studio and run, or:

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`
