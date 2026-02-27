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
| Background execution | `BroadcastReceiver` + `WakeLock` |
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
      drawable/     ← app icon
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
| `name` | String | 1 to 30 characters | empty |
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
| `gong` | String | identifier: `bikebell`, `doorbell`, `kettle`, `gong`, `none` | `none` |
| `timePlayback` | Boolean | true or false | true |
| `message` | String | 0 to 300 characters | empty |

---

## Alarm Scheduling

### Scheduling Strategy

- When a configuration is saved, all enabled alarm-events shall be (re-)scheduled using `AlarmManager.setExactAndAllowWhileIdle()`.
- On Android 12+ (API 31+), the app shall check and request the `SCHEDULE_EXACT_ALARM` permission at runtime before scheduling.
- The next occurrence of each alarm-event (considering weekday constraints) shall be calculated and scheduled individually as a one-shot alarm.
- After an alarm fires, `AlarmReceiver` immediately reschedules the alarm for its next occurrence.

### AlarmReceiver

- `AlarmReceiver extends BroadcastReceiver` handles `ACTION_ALARM_FIRE`.
- It acquires a `WakeLock` (PARTIAL_WAKE_LOCK) via `PowerManager` before starting playback.
- Playback sequence: gong → time announcement → message (all on a background coroutine / thread).
- After playback, the `WakeLock` is released.

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

All files must be shorter than 10 seconds. Format: MP3.

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
- Each list item shows three lines:
  1. Alarm-set name (bold, secondary color)
  2. Alarm time (large, primary color)
  3. Message (grey, truncated if long)
- Remaining time shown in right column: `X:MM h` or `N min`
- Pencil icon on each item → opens alarm-event editor directly
- Gear icon in toolbar → opens configuration screen

### Configuration Screen

- **Content:** List of all alarm-sets by name
- Each alarm-set card has icon-only action buttons:
  - Pencil icon → opens alarm-set editor
  - Copy icon → duplicates alarm-set
  - Trash icon → deletes with confirmation dialog
- FAB or toolbar button: "Neue Weckergruppe"

### Alarm-Set Editor Screen

- Editable fields: name (text input, max 30 chars with counter), enabled (toggle switch), volume (slider 0–100)
- Weekday selector: toggle chip buttons, Monday first (Mo Di Mi Do Fr Sa So)
- List of alarm-events (time + truncated message) with pencil/copy/trash icon buttons
- "Neuer Alarm" button adds a new alarm-event
- Save / Delete / Duplicate buttons

### Alarm-Event Editor Screen

- Editable fields: time (time picker), gong (dropdown), timePlayback (toggle switch), message (multi-line text, max 300 chars with counter)
- Gong dropdown uses display names from the table above
- "Jetzt abspielen" button plays the alarm immediately (test mode, does not mark as triggered)
- Save / Delete / Duplicate buttons

### Deletion & Duplication

- Delete always shows a confirmation dialog before removing
- Duplicate creates a copy with a new auto-generated id

---

## Alarm Notification

When an alarm fires, the app shall show a **heads-up notification** (high-priority, shows on lock screen):

- Title: alarm-set name
- Body: alarm time + message (truncated)
- Category: `CATEGORY_ALARM`
- Audio: silent (sound is played directly by `MediaPlayer` via `AlarmReceiver`, not via notification sound)
- Notification channel: `alarm_channel` (importance = HIGH)
- Auto-dismisses after playback completes

The notification shall be declared in the manifest and the channel registered in `Application.onCreate()`.

---

## Data Persistence

### DataStore (JSON)

- All alarm-sets (including their alarm-events) are serialized to JSON using `kotlinx.serialization` and stored in a single `DataStore<Preferences>` key.
- Configuration is **read at app start** and **saved on every change** (after each edit/delete/add operation).
- On **first startup** (empty DataStore), a default demo configuration shall be created:
  - Alarm-Set: "Demo", enabled, volume 80%, weekdays Monday–Friday (1–5)
  - Alarm-Event: time "07: 30", gong "gong", timePlayback true, message "John, es ist Zeit, die Schuhe anzuziehen."

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


