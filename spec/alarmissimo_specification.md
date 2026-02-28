# Alarmissimo - Native Android Kotlin App Specification

## Overview

This document serves as both a formal specification and a prompt for an AI agent to create the app.

**Target Application:** Create a native Android app named **"Alarmissimo"** that functions as an alarm clock for scheduling and triggering audio alarms.

**Prerequisites for Implementation:** Assume you are an experienced Android developer with 10+ years of experience in building native Android applications with Kotlin.

### Use Case

The app shall be used in private situations, such as reminding children to prepare early for school or other regular activities.

Unlike a traditional alarm clock, **Alarmissimo** has the following distinct characteristics:
- The alarm plays only once (no snooze functionality)
- No user interaction is required to stop the alarm
- Combined notification: audio (gong sound) + spoken time (German) + text-to-speech message

### Technical Stack

| Concern | Choice |
|---------|--------|
| Language | Kotlin |
| UI toolkit | Jetpack Compose (Material 3) |
| Architecture | MVVM with ViewModel + StateFlow |
| Min SDK | API 23 (Android 6.0 Marshmallow) |
| Target SDK | API 34 (current) |
| Build system | Gradle (Kotlin DSL) |
| Alarm scheduling | `AlarmManager.setExactAndAllowWhileIdle()` |
| Background execution | `AlarmForegroundService` (foreground service) + `WakeLock` |
| Data persistence | Jetpack DataStore (JSON via kotlinx.serialization) |
| Audio playback | `MediaPlayer` |
| Text-to-speech | Android `TextToSpeech` API |
| DI | None (plain constructor injection) |
| Testing | JUnit 4, Espresso (optional) |

---

## Project Structure

```
app/
  src/main/
    kotlin/com/alarmissimo/
      data/
        AlarmRepository.kt
        DataStoreManager.kt
        model/
          AlarmSet.kt
          AlarmEvent.kt
      receiver/
        AlarmReceiver.kt
      service/
        AlarmForegroundService.kt
        AlarmPlaybackHelper.kt
      ui/
        MainActivity.kt
        theme/
          Theme.kt
          Color.kt
        screen/
          DashboardScreen.kt
          ConfigScreen.kt
          AlarmSetEditorScreen.kt
          AlarmEventEditorScreen.kt
        viewmodel/
          DashboardViewModel.kt
          ConfigViewModel.kt
          AlarmSetEditorViewModel.kt
          AlarmEventEditorViewModel.kt
      util/
        TimeUtils.kt
    res/
      raw/          ← gong MP3 files
      drawable/
        alarmissimo_icon.xml   ← VectorDrawable (512×512 viewport, 108dp)
        ic_launcher_foreground.xml ← foreground layer for adaptive icon
      mipmap-anydpi-v26/
        ic_launcher.xml  ← adaptive icon (API 26+)
      mipmap-anydpi/
        ic_launcher.xml  ← fallback layer-list (API < 26)
      values/
        colors.xml  ← ic_launcher_background color
  AndroidManifest.xml
build.gradle.kts
settings.gradle.kts
```

---

## Features & Content

### Core Concept

The app shall allow users to configure and manage **0 to n alarm-sets**.

### Alarm-Set

An **alarm-set** is a group of 1 to n alarm-events. The alarm-events shall be ordered by the `time` property.

#### Properties

| Property | Type | Constraints | Default |
|----------|------|-------------|---------|
| `id` | Long | auto-generated (epoch ms) | — |
| `name` | String | 0 to 30 characters (empty string is valid for new alarm-sets) | empty |
| `enabled` | Boolean | true or false | true |
| `weekdays` | List\<Int\> | 1 to 7 entries (1=Mon … 7=Sun, `Calendar` convention) | all selected |
| `audioVolume` | Int | 0 to 100 | 80 |

### Alarm-Event

An **alarm-event** is a single timed alarm within an alarm-set.

#### Properties

| Property | Type | Constraints | Default |
|----------|------|-------------|---------|
| `id` | Long | auto-generated (epoch ms) | — |
| `time` | String | `HH:mm` format | current time |
| `gong` | String | identifier: `bikebell`, `doorbell`, `kettle`, `gong`, `none`, or `system:<uri>` (Android ringtone URI) | `none` |
| `timePlayback` | Boolean | true or false | true |
| `message` | String | 0 to 300 characters | empty |

---

## Audio Volume

The alarm-set's `audioVolume` (0–100) is applied to **all** audio output during playback (item 24):
- **Gong** (`MediaPlayer`): `setVolume(vol, vol)` where `vol = audioVolume / 100f`
- **TTS** (time announcement + message): `TextToSpeech.Engine.KEY_PARAM_VOLUME` bundle parameter passed to each `tts.speak()` call
- Applies both when an alarm fires (`AlarmReceiver`) and when "Jetzt abspielen" is tapped in the alarm-event editor

---

## Alarm Scheduling

### Scheduling Strategy

- When a configuration is saved, all enabled alarm-events shall be (re-)scheduled using `AlarmManager.setExactAndAllowWhileIdle()`.
- **Permissions:** `USE_EXACT_ALARM` (auto-granted on API 33+) is the primary exact-alarm permission. `SCHEDULE_EXACT_ALARM` is also declared for API 31/32 compatibility; if `canScheduleExactAlarms()` returns false on API 31/32, `MainActivity` redirects the user to `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`.
- `POST_NOTIFICATIONS` is requested at runtime in `MainActivity` for Android 13+ to enable the foreground-service notification.
- The next occurrence of each alarm-event (considering weekday constraints) shall be calculated and scheduled individually as a one-shot alarm.
- After an alarm fires, `AlarmForegroundService` immediately reschedules the alarm for its next occurrence.

### AlarmReceiver

- `AlarmReceiver extends BroadcastReceiver` handles `ACTION_ALARM_FIRE`.
- On `onReceive`, it immediately calls `startForegroundService(AlarmForegroundService)` and returns — it performs **no** long-running work itself (BroadcastReceiver.goAsync() is limited to ~10 s on Android 8+, which is insufficient for TTS).

### AlarmForegroundService

- Starts as a foreground service (posts a persistent notification immediately via `startForeground()`).
- Acquires a `WakeLock` (PARTIAL_WAKE_LOCK, 60 s safety timeout) before playback.
- Runs the full playback sequence (`AlarmPlaybackHelper.play()`) inside a `CoroutineScope(SupervisorJob() + Dispatchers.IO)`.
- After playback completes, reschedules the alarm for its next occurrence and calls `stopSelf()`.
- `foregroundServiceType="mediaPlayback"` declared in the manifest (required on API 34+).

### Boot Persistence

- The app shall register a `BOOT_COMPLETED` receiver to reschedule all alarms after device reboot.
- Permission `RECEIVE_BOOT_COMPLETED` shall be declared in the manifest.

---

## Alarm Trigger Sequence

**Trigger Condition:** If the alarm-set is `enabled` and the current weekday is in `weekdays`, the `AlarmReceiver` plays — in order:

1. The **gong sound** (via `MediaPlayer`, from `res/raw/`, if not `none`)
2. The **time announcement** via `TextToSpeech` (if `timePlayback` is `true`):  
   *"Es ist \<H\> Uhr \<M\>."* — no leading zeros, minutes omitted if 0  
   e.g. "Es ist 7 Uhr 30." / "Es ist 8 Uhr."  
   Language: `de-DE`
3. The **message** via `TextToSpeech` (if message is non-empty)

**Multiple Alarms at Same Time:** If two alarm-events trigger simultaneously, play sequentially. Order within the same alarm-set shall be defined by the time property (ties by id).

**Empty Alarm Scenario:** If gong is `none` and message is empty but `timePlayback` is enabled, speak only the current time.

---

## Gong Sounds

| Identifier | Display Name | File |
|------------|--------------|------|
| `bikebell` | Fahrradklingel | `res/raw/bikebell.mp3` |
| `doorbell` | Türklingel | `res/raw/doorbell.mp3` |
| `kettle` | Pauke | `res/raw/kettle.mp3` |
| `gong` | Gong | `res/raw/gong.mp3` |
| `none` | Kein Sound | — |
| `system:<uri>` | System-Alarmton (Android) | Android `RingtoneManager` URI |

All raw files must be shorter than 10 seconds. Format: MP3.
System ringtones are selected via `RingtoneManager.ACTION_RINGTONE_PICKER` (type `TYPE_ALARM`) and played via `MediaPlayer.setDataSource(context, uri)` with `isLooping = false`.
Because some system alarm URIs contain OGG Vorbis tracks with embedded loop tags that the codec honours regardless of `isLooping`, a `Handler.postDelayed` based on `MediaPlayer.duration` is used to guarantee the sound stops after exactly one play (item 18).

---

## User Interface

### Design Guidelines

- **UI language: German**
- Material 3 design system (dynamic color where available, static fallback)
- Neutral business-like style
- Designed for ~4 alarm-sets with ~5 alarm-events each
- Single `Activity` (`MainActivity`) hosting a `NavHost` with four destinations

### Navigation

```
Dashboard  ←→  Config  →  AlarmSetEditor  →  AlarmEventEditor
```

Back navigation via the system back gesture / back button.

### Dashboard Screen

- **Purpose:** Display upcoming alarms in the next 24 hours
- **Content:** Ordered list (earliest first) of upcoming alarm-events
- **TopAppBar title:** App icon (32 dp) + text "Alarmissimo" in a Row (item 1)
- Each list item shows three lines:
  1. Alarm-set name (bold, secondary color)
  2. Alarm time (large, primary color)
  3. Message (grey, truncated if long)
- Remaining time shown in right column as **`in hh:mm:ss`** (e.g. "in 1:23:45"), updated every second (items 15, 16 & 21)
  - `DashboardViewModel` exposes a public `tickMillis: StateFlow<Long>` that ticks every second; the composable collects it and passes `nowMillis` explicitly to the formatting function so Compose recomposes the countdown text each second
- Pencil icon on each item → opens alarm-event editor directly
- Gear icon in toolbar → opens configuration screen

### Configuration Screen

- **TopAppBar:** Back arrow (ArrowBack icon) returns to Dashboard (item 14)
- **Content:** List of all alarm-sets by name
- Each alarm-set card shows the **first 5 alarm-events** as preview rows (time + message); if more exist, a "+N weitere…" label is appended (item 13)
- Each alarm-set card has icon-only action buttons in a vertical column on the right:
  - Pencil icon → opens alarm-set editor
  - Copy icon → duplicates alarm-set and **immediately opens its editor** (item 10)
  - Trash icon → deletes with confirmation dialog
- FAB: "Neue Weckergruppe" — immediately **opens the alarm-set editor** for the new entry (item 4)
- New alarm-sets have an **empty name** by default (items 4 & 5)
- Duplicated alarm-sets have the name `"<original> (Kopie)"` appended (item 19)
- Duplication logic is implemented **once** in `AlarmRepository.duplicateAlarmSet(id)` and called from both `ConfigViewModel` and `AlarmSetEditorViewModel` to avoid divergence

### Alarm-Set Editor Screen

- Editable fields: name (text input, max 30 chars with counter), enabled (toggle switch), volume (slider 0–100)
- Weekday selector: toggle chips in a `FlowRow`, Monday first (Mo Di Mi Do Fr Sa So); uses spec weekday values 1–7 (1=Mon … 7=Sun) — all 7 chips always visible (items 6)
- **"Neuer Alarm" icon button shown inline in the "Alarme" section header**, above the alarm-event list (item 22)
- List of alarm-events shows **time** (primary color) and **message** (muted, or "(keine Nachricht)") per row (item 9)
  - Each row has pencil/copy/trash icon buttons
  - Copy → **opens alarm-event editor** for the duplicate (item 7)
  - Trash → deletes event and **stays on this screen** (item 8)
- "Neuer Alarm" button adds a new alarm-event and **opens its editor** (item 7)
- Bottom action bar uses **icon-only buttons** (no label text): Add (`Add`), Save (`Save`), Copy (`ContentCopy`), Delete (`Delete`) (items 12 & 20)

### Alarm-Event Editor Screen

- Editable fields: time (Material3 `TimePicker` in `AlertDialog` — item 17), gong (dropdown), timePlayback (toggle switch), message (multi-line text, max 300 chars with counter)
- Gong dropdown lists built-in sounds; a separate **"Systemton wählen…" button** opens the Android ringtone picker (item 3)
  - Selected system ringtone is stored as `"system:<content-uri>"` in the `gong` field
  - Display name resolved via `RingtoneManager.getRingtone(context, uri)?.getTitle(context)`
- Action buttons use **icon buttons** (item 12): Save (`Save`), Play (`PlayArrow`), Duplicate (`ContentCopy`), Delete (`Delete`)

### Deletion & Duplication

- Delete always shows a confirmation dialog before removing
- Duplicate creates a copy with a new auto-generated id

---

## Alarm Notification

When an alarm fires, the app shall show a **heads-up notification** (high-priority, shows on lock screen):

- Title: `Alarmissimo` (static)
- Body: the alarm-event's `message` if non-empty; otherwise `"Alarm wird abgespielt …"` as fallback
- Category: `CATEGORY_ALARM`
- Audio: **silent** — `setSound(null, null)` on the channel and `.setSound(null)` on the builder; audio is driven entirely by `AlarmForegroundService` via `AlarmPlaybackHelper`
- Notification channel: `alarm_channel` (importance = HIGH, `setBypassDnd = true`)
- Ongoing (not dismissable by the user while the service is running); removed automatically when `AlarmForegroundService` calls `stopSelf()`

The notification channel is registered in `AlarmissimoApp.onCreate()`. Because Android caches channel configuration after first registration, sound suppression requires a clean install (or clearing app data) if the channel was previously registered with sound.

---

## Data Persistence

### DataStore (JSON)

- All alarm-sets (including their alarm-events) are serialized to JSON using `kotlinx.serialization` and stored in a single `DataStore<Preferences>` key.
- Configuration is **read at app start** and **saved on every change** (after each edit/delete/add operation).
- On **first startup** (empty DataStore), a default demo configuration shall be created:
  - Alarm-Set: "Demo", enabled, volume 80%, weekdays Monday–Friday (1–5)
  - Alarm-Event: time "07:30", gong "gong", timePlayback true, message "John, es ist Zeit, die Schuhe anzuziehen."

### Data Model Classes

```kotlin
@Serializable
data class AlarmSet(
    val id: Long,
    val name: String,
    val enabled: Boolean,
    val weekdays: List<Int>,
    val audioVolume: Int,
    val alarmEvents: List<AlarmEvent>
)

@Serializable
data class AlarmEvent(
    val id: Long,
    val time: String,     // "HH:mm"
    val gong: String,     // identifier or "none"
    val timePlayback: Boolean,
    val message: String
)
```

---

## Permissions (AndroidManifest.xml)

| Permission | Reason |
|------------|--------|
| `RECEIVE_BOOT_COMPLETED` | Reschedule alarms after reboot |
| `WAKE_LOCK` | Keep CPU awake during playback |
| `VIBRATE` | Optional: vibrate when alarm triggers |
| `USE_FULL_SCREEN_INTENT` | Declared but not used (heads-up only) |
| `SCHEDULE_EXACT_ALARM` | Required on API 31+ for exact alarm scheduling |
| `POST_NOTIFICATIONS` | Required on API 33+ to show notifications |

---

## Error Handling

### Audio Playback Failures

- If a gong file fails to load, skip the gong and continue with TTS
- If `TextToSpeech` is unavailable or returns `ERROR`, mark alarm as triggered and continue
- If no German voice is installed, fall back to the system default voice

### Permission Handling

- If `SCHEDULE_EXACT_ALARM` is not granted on API 31+, show an in-app banner directing the user to system settings
- If `POST_NOTIFICATIONS` is not granted on API 33+, alarms still fire (audio plays), but no notification is shown

### DataStore Failures

- If DataStore read fails, start with an empty alarm list and log the error
- If DataStore write fails, log the error and show a brief Snackbar warning

---

## Timezone & Time Handling

- All time calculations use the **device's local timezone**
- Dashboard "next 24 hours" is relative to the device's current local time
- DST transitions are not specially handled; user should verify alarms after timezone changes

---

## Build & Development Environment

### Gradle Dependencies (key)

```kotlin
// build.gradle.kts (app)
implementation("androidx.core:core-ktx:1.13+")
implementation("androidx.compose.material3:material3:1.2+")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7+")
implementation("androidx.navigation:navigation-compose:2.7+")
implementation("androidx.datastore:datastore-preferences:1.1+")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6+")
```

### Code Guidelines

- All Kotlin code shall follow the [official Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- All public classes and functions shall have KDoc comments documenting purpose, parameters, and return values
- ViewModels shall expose state via `StateFlow<UiState>` where `UiState` is a sealed class or data class
- Repository functions shall be `suspend` functions; coroutine scope provided by ViewModel

---

## Scalability

- Minimum: 1 alarm-set with 1 alarm-event
- Recommended design target: ~4 alarm-sets with ~5 alarm-events
- Maximum: No hard limit; UI scrolls beyond 10 alarm-sets

---

## Out of Scope

- iOS support
- Export / import
- Cloud sync
- Snooze
- Widget
- Wear OS


