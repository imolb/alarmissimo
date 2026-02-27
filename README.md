# Alarmissimo

A native Android alarm clock app written in Kotlin, designed for scheduling and triggering audio alarms — e.g. reminding children to prepare for school on weekday mornings.

## Features

- **Multiple Alarm-Sets**: Group alarms by profile (e.g. "School", "Weekend")
- **Flexible Scheduling**: Per-alarm weekday selection and 24-hour time
- **Rich Audio**: Gong sounds, spoken time announcements (German), and TTS messages
- **Reliable Triggering**: `AlarmManager.setExactAndAllowWhileIdle()` — fires even in Doze mode
- **Boot Persistence**: Alarms are rescheduled automatically after device reboot
- **Local Storage**: All data stored on-device via Jetpack DataStore

## Technical Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVVM — ViewModel + StateFlow
- **Alarm scheduling**: `AlarmManager` + `BroadcastReceiver`
- **Notifications**: Heads-up notification with high priority channel
- **Audio**: `MediaPlayer` in `BroadcastReceiver`
- **Storage**: Jetpack DataStore + `kotlinx.serialization` (JSON)
- **Min SDK**: API 23 (Android 6.0) | **Target SDK**: API 34

## Project Structure

```
app/src/main/
├── kotlin/com/alarmissimo/
│   ├── data/           # DataStore repository, AlarmSet / AlarmEvent models
│   ├── receiver/       # AlarmReceiver (BroadcastReceiver), BootReceiver
│   ├── service/        # Audio playback helpers
│   ├── ui/             # Compose screens + ViewModel
│   └── MainActivity.kt
├── res/
│   ├── raw/            # Alarm sounds (gong, doorbell, bikebell, kettle)
│   └── …
└── AndroidManifest.xml

spec/
└── alarmissimo_specification.md   # Full specification

scripts/
└── setup-android-sdk.sh           # One-shot Android SDK installer
```

## Getting Started

### Prerequisites

- JDK 21 (`/usr/lib/jvm/java-21-openjdk-amd64` on Debian/Ubuntu)
- Android SDK (run `scripts/setup-android-sdk.sh` to install)
- Android device or emulator (API 23+)

### Build

```bash
./gradlew app:assembleDebug
```

### Install on device

```bash
./gradlew app:installDebug
```

### Run tests

```bash
./gradlew app:test                  # unit tests
./gradlew app:connectedAndroidTest  # instrumentation tests (device required)
```

VS Code tasks for all of the above are configured in `.vscode/tasks.json`.

## How Alarms Work

When a scheduled alarm fires the `BroadcastReceiver`:

1. Posts a heads-up notification
2. Plays the selected gong sound via `MediaPlayer`
3. Announces the time in German: *"Es ist 7 Uhr 30."*
4. Reads the configured message aloud via `TextToSpeech`

## Data Model

**AlarmSet** — `id`, `name`, `enabled`, `weekdays`, `audioVolume`, `alarmEvents[]`

**AlarmEvent** — `id`, `time`, `gong`, `timePlayback`, `message`

Default config: alarm set "Demo", Mon–Fri, 07:30, gong sound, German message.

## License

GNU General Public License v3.0 (GPL-3.0) — see LICENSE file.

---

**Status**: In Development | **Updated**: 27 February 2026
