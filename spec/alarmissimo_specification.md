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
          VoiceProfile.kt
          SoundDeviceConfig.kt
      receiver/
        AlarmReceiver.kt
        BtCheckReceiver.kt
        BtKeepAliveReceiver.kt
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
          VoiceListScreen.kt
          VoiceProfileScreen.kt
          SoundDeviceScreen.kt
        viewmodel/
          DashboardViewModel.kt
          ConfigViewModel.kt
          AlarmSetEditorViewModel.kt
          AlarmEventEditorViewModel.kt
          VoiceListViewModel.kt
          VoiceProfileViewModel.kt
          SoundDeviceViewModel.kt
      util/
        TimeUtils.kt
    res/
      raw/          ← gong/bikebell/doorbell/kettle MP3 files
      drawable/
        alarmissimo_icon.xml
        ic_launcher_foreground.xml
      mipmap-anydpi-v26/
        ic_launcher.xml
      mipmap-anydpi/
        ic_launcher.xml
      values/
        colors.xml
  AndroidManifest.xml
build.gradle.kts
settings.gradle.kts
```

---

## Features & Content

### Core Concept

The app shall allow users to configure and manage **0 to n alarm-sets**.

### Alarm-Set

An **alarm-set** is a group of 1 to n alarm-events. The alarm-events shall be ordered by their computed absolute trigger time.

#### Properties

| Property | Type | Constraints | Default |
|----------|------|-------------|---------|
| `id` | Long | auto-generated (epoch ms) | — |
| `name` | String | 0 to 30 characters (empty string is valid for new alarm-sets) | empty |
| `enabled` | Boolean | true or false | true |
| `weekdays` | List\<Int\> | 1 to 7 entries (1=Mon … 7=Sun, `Calendar` convention) | all selected |
| `specificDate` | String? | `yyyy-MM-dd` format, or null (weekday mode) | null |
| `timeMode` | String | `"absolute"` or `"relative"` | `"absolute"` |
| `endTime` | String? | `HH:mm` format; only used when `timeMode = "relative"`; null otherwise | null |
| `endEventName` | String | Human-readable name for the end event (0 to 50 chars); shown in `durationPlayback` TTS announcement; only relevant when `timeMode = "relative"` | `""` |
| `audioVolume` | Int | 0 to 100; controls playback volume for both gong and TTS | 80 |

### Alarm-Event

An **alarm-event** is a single timed alarm within an alarm-set.

#### Properties

| Property | Type | Constraints | Default |
|----------|------|-------------|---------|
| `id` | Long | auto-generated (epoch ms) | — |
| `enabled` | Boolean | true or false | true |
| `time` | String | `HH:mm` format; used when alarm-set `timeMode = "absolute"` | current time |
| `offsetMinutes` | Int | 0 to 480; used when alarm-set `timeMode = "relative"` (minutes before `endTime`) | 0 |
| `gong` | String | identifier: `bikebell1x`, `bikebell2x`, `doorbell`, `kettle`, `gong1x`, `gong2x`, `gong3x`, `gong4x`, `none`, or `system:<uri>` (Android ringtone URI) | `none` |
| `timePlayback` | Boolean | If true, TTS announces the current time: *"Es ist H Uhr M."* | true |
| `durationPlayback` | Boolean | If true **and** alarm-set `timeMode = "relative"` **and** `endEventName` is non-empty, TTS announces *"Es sind noch X Minuten bis Y."* (where X = `offsetMinutes`, Y = `endEventName`) before the time and message | false |
| `message` | String | 0 to 300 characters | empty |
| `voiceProfileId` | Long | id of a `VoiceProfile`; 0 = built-in \"Standard\" profile | 0 |

---

## Voice Profiles

Voice profiles replace the former global `VoiceConfig`. Multiple named profiles can be defined by the user. Each alarm-event selects one profile via `voiceProfileId`.

All user-defined voice profiles are stored in DataStore under the key `voice_profiles` (JSON array). The **"Standard"** profile is a compile-time constant in the repository and is never written to DataStore — it is always merged into the list at read-time.

### VoiceProfile Properties

| Property | Type | Constraints | Default |
|----------|------|-------------|---------|
| `id` | Long | auto-generated (epoch ms); \"Standard\" profile uses id = 0 | — |
| `name` | String | 1 to 20 characters; must be unique across all profiles | empty (must be set before saving) |
| `speechRate` | Float | 0.5 to 2.0 | 1.0 |
| `pitch` | Float | 0.5 to 2.0 | 1.0 |
| `pan` | Float | −1.0 to +1.0 | 0.0 |
| `language` | String | BCP-47 tag; `"system"` = device default | `"system"` |
| `voiceName` | String? | `Voice.getName()` of a specific installed voice; null = engine default | null |
| `enginePackage` | String? | Package name of the TTS engine; null = system default | null |

**"Standard" profile fixed values:** id=0, name="Standard", speechRate=1.0, pitch=1.0, pan=0.0, language="system", voiceName=null, enginePackage=null. It is not editable and not deletable.

**Duplicate name suffix:** When duplicating, append "(Kopie)". If that name already exists, use "(Kopie 2)", "(Kopie 3)", etc.

### Voice List Screen

**Route:** `voice_list`
**ViewModel:** `VoiceListViewModel`

- Accessible from the **Config screen** via a **"Stimmprofile"** button at the top (replacing the former "Sprachkonfiguration" button).
- Lists all voice profiles ("Standard" first, then user-defined).
- Each list item shows: **Name**, **Speed** (e.g. "1.0×").
- Action buttons per item:
  - **"Standard" profile:** View icon only (opens the screen in read-only mode; no edit/duplicate/delete).
  - **User-defined profiles:** Edit (pencil), Duplicate (copy), Delete (trash).
- Delete shows a confirmation dialog (see Voice Profile Deletion Rules below).
- Duplicate creates a copy with name suffix "(Kopie)" and **immediately opens its editor**.
- FAB: **"Neues Stimmprofil"** — creates a new profile with empty name and default values, immediately opens its editor.

### Voice Profile Screen

**Route:** `voice_profile/{id}`
**ViewModel:** `VoiceProfileViewModel`

- Editable fields: name (text input, max 20 chars), speechRate (slider 0.5–2.0), pitch (slider 0.5–2.0), pan (slider −1.0–1.0), language (dropdown), voice (dropdown, populated via `TextToSpeech.voices`), TTS engine (dropdown, populated via `PackageManager.queryIntentServices`).
- **"Vorschautext"** text field (default "Es ist 7 Uhr 30. Guten Morgen.") + icon-only **"Jetzt abspielen"** button that speaks the preview text with the current profile settings. A progress indicator is shown while speaking.
- Changing the engine triggers a reload of available voices.
- **Back button (top left) saves** and navigates back; for the "Standard" profile the screen is read-only and back simply navigates without saving.
- Bottom action bar: **Duplicate** (ContentCopy) and **Delete** (Delete) icon buttons only. No Save button (back arrow saves). Duplicate and Delete not shown for "Standard" profile.
- **Unique name validation:** Saving is blocked with inline error "Name bereits vergeben" if the name is already used by another profile.

### Voice Profile Deletion Rules

When deleting a voice profile (from voice list or voice profile screen):

1. Check whether any alarm-event references this profile's id.
2. If **no references exist:** show a standard confirmation dialog; delete on confirm.
3. If **references exist:** show the dialog (in German):
   > "Das Stimmprofil wird in folgenden Alarm-Ereignissen verwendet: \<Weckerset-Name\> \<Zeit\> \<Nachricht\>. Diese Alarm-Ereignisse erhalten das Stimmprofil „Standard". Möchten Sie das Stimmprofil „\<Name\>" wirklich löschen?"

   On confirm: reassign all referencing alarm-events to `voiceProfileId = 0` (Standard), then delete the profile.

---

---

## Audio Volume

Audio volume is controlled at two independent levels:

### Absolute System Volume

Before playback begins, `AlarmForegroundService` sets the device's **`AudioManager.STREAM_ALARM`** stream to an absolute level derived from the configured volume (0–100 mapped to 0–`AudioManager.getStreamMaxVolume(STREAM_ALARM)`). This overrides any prior user-set system volume. After playback ends, the original volume is restored.

- Requires `<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS"/>` in the manifest (normal-level, no runtime grant needed).
- Both gong (`MediaPlayer`) and TTS (`AudioAttributes`) use `STREAM_ALARM` as their audio stream.

### Per-Component Volume

| Component | Source | How applied |
|-----------|--------|-------------|
| **Gong** | `AlarmEvent.gongVolume` (0–100) | `MediaPlayer.setVolume(v, v)` where `v = gongVolume / 100f` |
| **TTS** (time + message) | `VoiceProfile.volume` (0–100) | `TextToSpeech.Engine.KEY_PARAM_VOLUME` bundle param |

Both volume values control volume *relative to the STREAM_ALARM level* set above.

These apply both when an alarm fires and when "Jetzt abspielen" is tapped in the alarm-event editor or voice profile screen.

---

## Alarm Scheduling

### Scheduling Strategy

- When a configuration is saved, all enabled alarm-sets and their enabled alarm-events are (re-)scheduled using `AlarmManager.setExactAndAllowWhileIdle()`.
- **Permissions:** `USE_EXACT_ALARM` (auto-granted on API 33+) is the primary exact-alarm permission. `SCHEDULE_EXACT_ALARM` is also declared for API 31/32 compatibility; if `canScheduleExactAlarms()` returns false on API 31/32, `MainActivity` redirects the user to `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`.
- `POST_NOTIFICATIONS` is requested at runtime in `MainActivity` for Android 13+ to enable the foreground-service notification.
- The next occurrence of each alarm-event (considering weekday/date constraints) shall be calculated and scheduled individually as a one-shot alarm.
- **Date-specific alarms** (`specificDate` is non-null): scheduled for exactly that date. After the alarm fires, `AlarmForegroundService` does **not** reschedule it. The alarm-set remains in the list with the past date visible; the user must manually clear the date or pick a new one.
- **Weekday-recurring alarms** (`specificDate` is null): after firing, `AlarmForegroundService` reschedules for the next matching weekday as before.
- **Trigger time calculation for `timeMode = "relative"`**: `triggerTime = endTime − offsetMinutes`. Midnight wrap is handled correctly (modulo 24 h).
- Disabling an alarm-set (or individual alarm-event) immediately cancels its pending `AlarmManager` alarm. Re-enabling it immediately reschedules.

### AlarmReceiver

- `AlarmReceiver extends BroadcastReceiver` handles `ACTION_ALARM_FIRE`.
- On `onReceive`, it immediately calls `startForegroundService(AlarmForegroundService)` and returns — it performs **no** long-running work itself (BroadcastReceiver.goAsync() is limited to ~10 s on Android 8+, which is insufficient for TTS).

### AlarmForegroundService

- Starts as a foreground service (posts a persistent notification immediately via `startForeground()`).
- Acquires a `WakeLock` (PARTIAL_WAKE_LOCK, 60 s safety timeout) before playback.
- **Enabled-check at fire time:** After loading the alarm-set and alarm-event from the repository at the moment of firing, verify that **both** `alarmSet.enabled` and `alarmEvent.enabled` are true before playing. If either is false, skip playback entirely but still reschedule (so the alarm becomes active again if re-enabled later), then call `stopSelf()`.
- Runs the full playback sequence (`AlarmPlaybackHelper.play()`) inside a `CoroutineScope(SupervisorJob() + Dispatchers.IO)`.
- **Absolute volume:** Before playback, saves the current `STREAM_ALARM` volume and sets it to the value derived from the alarm-event's `gongVolume` (or `voiceProfile.volume` for TTS); restores the original volume after playback completes.
- After playback completes, reschedules the alarm (weekday-recurring only; date-specific alarms are not rescheduled) and calls `stopSelf()`.
- `foregroundServiceType="mediaPlayback"` declared in the manifest (required on API 34+).

### Boot Persistence

- The app shall register a `BOOT_COMPLETED` receiver to reschedule all alarms after device reboot.
- Permission `RECEIVE_BOOT_COMPLETED` shall be declared in the manifest.

---

## Alarm Trigger Sequence

**Trigger Condition:** The alarm fires if `alarmSet.enabled = true` **and** `alarmEvent.enabled = true` and the current weekday/date matches. This check is performed at fire time inside `AlarmForegroundService`, not only at scheduling time.

1. The **gong sound** (via `MediaPlayer`, from `res/raw/`, if not `none`), played at `gongVolume`
2. The **duration announcement** via `TextToSpeech` (if `durationPlayback` is `true` **and** alarm-set `timeMode = "relative"` **and** `endEventName` is non-empty):
   *"Es sind noch \<offsetMinutes\> Minuten bis \<endEventName\>."*
3. The **time announcement** via `TextToSpeech` (if `timePlayback` is `true`), using the alarm-event's resolved `VoiceProfile`:
   *"Es ist \<H\> Uhr \<M\>."* — no leading zeros, minutes omitted if 0
   e.g. "Es ist 7 Uhr 30." / "Es ist 8 Uhr."
4. The **message** via `TextToSpeech` (if message is non-empty), using the same `VoiceProfile`

**Multiple Alarms at Same Time:** If two alarm-events trigger simultaneously, play sequentially. Order within the same alarm-set shall be defined by computed trigger time (ties by id).

**Empty Alarm Scenario:** If gong is `none` and message is empty but `timePlayback` is enabled, speak only the current time.

---

## Gong Sounds

| Identifier | Display Name | File |
|------------|--------------|------|
| `bikebell1x` | Fahrradklingel 1x | `res/raw/bikebell1x.mp3` |
| `bikebell2x` | Fahrradklingel 2x | `res/raw/bikebell2x.mp3` |
| `doorbell` | Türklingel | `res/raw/doorbell.mp3` |
| `kettle` | Pauke | `res/raw/kettle.mp3` |
| `gong1x` | Gong 1x | `res/raw/gong1x.mp3` |
| `gong2x` | Gong 2x | `res/raw/gong2x.mp3` |
| `gong3x` | Gong 3x | `res/raw/gong3x.mp3` |
| `gong4x` | Gong 4x | `res/raw/gong4x.mp3` |
| `none` | Kein Sound | — |
| `system:<uri>` | System-Alarmton (Android) | Android `RingtoneManager` URI |

**Unknown identifier fallback:** If a stored gong identifier is not in the above list (e.g. from an older data format), it is treated as `gong1x`.

All raw files must be shorter than 10 seconds. Format: MP3.
System ringtones are selected via `RingtoneManager.ACTION_RINGTONE_PICKER` (type `TYPE_ALARM`) and played via `MediaPlayer.setDataSource(context, uri)` with `isLooping = false`.
Because some system alarm URIs contain OGG Vorbis tracks with embedded loop tags that the codec honours regardless of `isLooping`, a `Handler.postDelayed` based on `MediaPlayer.duration` is used to guarantee the sound stops after exactly one play.

---

## User Interface

### Design Guidelines

- **UI language: German**
- Material 3 design system (dynamic color where available, static fallback)
- Neutral business-like style
- Designed for ~4 alarm-sets with ~5 alarm-events each
- Single `Activity` (`MainActivity`) hosting a `NavHost` with seven destinations

### Navigation

```
Dashboard  ←→  Config  →  VoiceList  →  VoiceProfile
                       →  SoundDevice
                       →  AlarmSetEditor  →  AlarmEventEditor
```

**Back navigation:**
- Every screen except the Dashboard has a **back arrow button in the TopAppBar (top left)**.
- Pressing the back arrow (or the device back gesture/button) **saves the current screen's data** and navigates to the previous screen.
- If validation fails (e.g. duplicate voice profile name): navigation is blocked and the inline error is shown. A dialog **"Änderungen verwerfen?"** with options **"Verwerfen"** and **"Weiter bearbeiten"** lets the user choose to discard and leave or stay and fix.
- **Save buttons are removed** from all bottom action bars. Only **Duplicate** (ContentCopy) and **Delete** (Delete) icon buttons remain at the bottom.

### Dashboard Screen

- **Purpose:** Display upcoming alarms in the next 24 hours
- **TopAppBar title:** App icon (32 dp) + text "Alarmissimo" in a Row
- **Section header:** "Alarme in den nächsten 24 Stunden" shown above the list
- **Content:** Ordered list (earliest first) of upcoming alarm-events where `alarmSet.enabled = true` and `alarmEvent.enabled = true`
- Each list item shows three lines:
  1. Alarm-set name (bold, secondary color)
  2. Alarm time (large, primary color). For date-specific alarms, also show the date: e.g. "Di, 25.03. 07:30"
  3. Message (grey, truncated if long)
- Remaining time shown in right column as **`in hh:mm:ss`** (e.g. "in 1:23:45"), updated every second
  - `DashboardViewModel` exposes a public `tickMillis: StateFlow<Long>` that ticks every second
- Pencil icon on each item → opens alarm-event editor directly
- Gear icon in toolbar → opens configuration screen

### Configuration Screen

- **TopAppBar:** Back arrow (ArrowBack icon, saves nothing — Config is a top-level nav destination) returns to Dashboard
- **Top buttons (before alarm-set list):**
  - **"Stimmprofile"** button → opens Voice List screen
  - **"Soundgerät"** button → opens Sound Device screen
- **Content:** List of all alarm-sets by name
- Each alarm-set card shows the **first 5 alarm-events** as preview rows (time + message); if more exist, a "+N weitere…" label is appended
- Each alarm-set card shows, on the right side: a **`LedChip`** enabled indicator, a Copy icon, and a Delete icon — all in a horizontal row.
  - **`LedChip`**: A filled 22 dp circle using M3 theme tokens — `primary` color when enabled, `surfaceVariant` fill + `outline` border when disabled. Tapping it toggles `alarmSet.enabled` and immediately reschedules/cancels alarms in `AlarmRepository`.
  - Pencil icon → opens alarm-set editor
  - Copy icon → duplicates alarm-set and **immediately opens its editor**
  - Trash icon → deletes with confirmation dialog
- FAB: "Neue Weckergruppe" — immediately **opens the alarm-set editor** for the new entry
- New alarm-sets have an **empty name** by default
- Duplicated alarm-sets have the name `"<original> (Kopie)"` appended
- Duplication logic is implemented **once** in `AlarmRepository.duplicateAlarmSet(id)` and called from both `ConfigViewModel` and `AlarmSetEditorViewModel` to avoid divergence
- **Build time** shown at the bottom of the screen

### Alarm-Set Editor Screen

- **TopAppBar:** Back arrow (saves and navigates back)
- Editable fields: name (text input, max 30 chars with counter), enabled (toggle switch)
- **Weekday / date section:**
  - Weekday selector: toggle chips in a `FlowRow`, Monday first (Mo Di Mi Do Fr Sa So); 1–7 (1=Mon … 7=Sun)
  - Date picker field below the chips. If a specific date is selected, weekday chips are greyed out (disabled). Selecting a weekday chip while a date is set clears the date. An **X icon** next to the date field clears it and re-enables the chips.
- **Time mode section:**
  - Toggle/switch: **"Absolute Zeiten"** ↔ **"Relative Zeiten"**
  - In relative mode, an **"Endzeit"** time field (HH:mm) is shown for the alarm-set.
  - When switching modes, existing alarm-event times/offsets are reset to default (time = current time, offsetMinutes = 0) with a one-time toast: "Zeiten der Alarm-Ereignisse wurden zurückgesetzt."
- **Lautstärke** slider (0–100, steps of 5): controls `audioVolume` — applies to both gong and TTS playback for all alarm-events in this set.
- **"Neuer Alarm" icon button shown inline in the "Alarme" section header**, above the alarm-event list
- List of alarm-events shows **computed trigger time** (primary color), **enabled toggle**, and **message** (muted, or "(keine Nachricht)") per row
  - Each row has pencil/copy/trash icon buttons
  - Copy → **opens alarm-event editor** for the duplicate
  - Trash → deletes event and **stays on this screen**
- "Neuer Alarm" button adds a new alarm-event and **opens its editor**
- Bottom action bar: **icon-only buttons**: Copy (`ContentCopy`), Delete (`Delete`). No Save button.

### Alarm-Event Editor Screen

- **TopAppBar:** Back arrow (saves and navigates back). The title area shows a two-line header: the alarm-event message (or "Alarm-Ereignis") as the primary line, and the parent alarm-set name as a tappable subtitle (`labelSmall`, `onSurfaceVariant`). Tapping the subtitle navigates to the parent alarm-set editor (pushes `alarm_set_editor/{alarmSetId}` onto the back stack).
- Editable fields:
  - **Enabled** toggle switch
  - **Time / Offset:**
    - In absolute mode: `TimePicker` (Material3, in `AlertDialog`)
    - In relative mode: numeric stepper field **"Minuten vor Endzeit"** (0–480) with +/− buttons
  - **Gong** dropdown (see Gong Sounds table for display names)
  - **Voice profile** dropdown (populated from all VoiceProfiles; displays name)
  - **Zeitansage** toggle switch (timePlayback)
  - **Nachricht** multi-line text, max 300 chars with counter
- Gong dropdown lists built-in sounds; a separate **"Systemton wählen…" button** opens the Android ringtone picker
  - Selected system ringtone is stored as `"system:<content-uri>"` in the `gong` field
  - Display name resolved via `RingtoneManager.getRingtone(context, uri)?.getTitle(context)`
- Bottom action bar: **icon-only buttons**: Play (`PlayArrow`), Duplicate (`ContentCopy`), Delete (`Delete`). No Save button.

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

- All alarm-sets (including their alarm-events) and voice profiles are serialized to JSON using `kotlinx.serialization` and stored in `DataStore<Preferences>` under separate keys.
- Sound device settings are stored under a third key.
- Configuration is **read at app start** and **saved on every change** (after each edit/delete/add operation).
- On **first startup** (empty DataStore), a default demo configuration shall be created:
  - Alarm-Set: "Demo", enabled=true, weekdays Monday–Friday (1–5), timeMode="absolute", specificDate=null
  - Alarm-Event: time "07:30", gong "gong1x", gongVolume=80, voiceProfileId=0 (Standard), timePlayback=true, message "John, es ist Zeit, die Schuhe anzuziehen.", enabled=true

### Data Model Classes

```kotlin
@Serializable
data class AlarmSet(
    val id: Long,
    val name: String,
    val enabled: Boolean,
    val weekdays: List<Int>,         // 1=Mon … 7=Sun
    val specificDate: String? = null, // "yyyy-MM-dd" or null
    val timeMode: String = "absolute", // "absolute" or "relative"
    val endTime: String? = null,      // "HH:mm", used only in relative mode
    val endEventName: String = "",    // human-readable name for end event (max 50 chars); used in durationPlayback TTS
    val alarmEvents: List<AlarmEvent>
)

@Serializable
data class AlarmEvent(
    val id: Long,
    val enabled: Boolean = true,
    val time: String,                 // "HH:mm", used in absolute mode
    val offsetMinutes: Int = 0,       // 0–480, used in relative mode
    val gong: String,                 // identifier or "none"
    val gongVolume: Int = 80,         // 0–100
    val timePlayback: Boolean,        // announce current time via TTS
    val durationPlayback: Boolean = false, // announce "Es sind noch X Minuten bis Y" (relative mode only)
    val message: String,
    val voiceProfileId: Long = 0L    // 0 = "Standard"
)

@Serializable
data class VoiceProfile(
    val id: Long,
    val name: String,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val pan: Float = 0.0f,
    val volume: Int = 80,
    val language: String = "system",
    val voiceName: String? = null,
    val enginePackage: String? = null
)

@Serializable
data class SoundDeviceConfig(
    val btWarningEnabled: Boolean = false,
    val btWarningAheadMinutes: Int = 10,
    val acceptedDevices: List<AcceptedDevice> = emptyList(),
    val btKeepAliveEnabled: Boolean = false,
    val btKeepAliveIntervalMinutes: Int = 10
)

@Serializable
data class AcceptedDevice(
    val mac: String,
    val name: String
)
```

### DataStore Keys

| Key | Type | Content |
|-----|------|---------|
| `alarm_sets` | String (JSON) | `List<AlarmSet>` |
| `voice_profiles` | String (JSON) | `List<VoiceProfile>` (user-defined only; "Standard" is hard-coded) |
| `sound_device_config` | String (JSON) | `SoundDeviceConfig` |

---

## App Version

The build time is injected at compile time via a `buildConfigField` in `build.gradle.kts`:

```kotlin
buildConfigField("String", "BUILD_TIME",
    "\"${java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}\"")
```

This value is displayed at the bottom of the Configuration screen as: **"Build: 21.03.2026 14:35"**

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
| `MODIFY_AUDIO_SETTINGS` | Set absolute system STREAM_ALARM volume before playback |
| `BLUETOOTH_CONNECT` | Read paired/connected BT devices (API 31+, runtime-dangerous) |

---

## Bluetooth Sound Device

Settings are stored in DataStore under the key `sound_device_config` (see `SoundDeviceConfig` data class).

### Sound Device Screen

**Route:** `sound_device`
**ViewModel:** `SoundDeviceViewModel`

Accessible from the Configuration screen via the **"Soundgerät"** button.

**Section 1 — Check Bluetooth Device:**

| Field (UI label) | Type | Default | Description |
|------------------|------|---------|-------------|
| Bluetooth-Gerät prüfen | Toggle | off | Master switch for the BT warning feature (`btWarningEnabled`) |
| Akzeptierte Geräte | Multi-select list | empty | Paired BT devices accepted as valid audio output (`acceptedDevices`) |
| Vorlaufzeit | Stepper | 10 min | 1–60 min warning window before alarm (`btWarningAheadMinutes`) |

**Section 2 — Keep Bluetooth Alive:**

| Field (UI label) | Type | Default | Description |
|------------------|------|---------|-------------|
| Bluetooth-Verbindung aufrechterhalten | Toggle | off | Master switch for the keep-alive feature (`btKeepAliveEnabled`) |
| Wachhalte-Intervall | Stepper | 10 min | 1–30 min between silent audio bursts (`btKeepAliveIntervalMinutes`) |

**Section 3 — Auto-Trennung (Auto-Disconnect):**

| Field (UI label) | Type | Default | Description |
|------------------|------|---------|-------------|
| Auto-Trennung aktivieren | Toggle | off | Master switch (`btAutoDisconnectEnabled`). When enabled, BT is disconnected after each alarm-event if the next alarm is further away than the lookahead window. |
| Vorlaufzeit | Stepper | 30 min | 1–120 min lookahead window (`btAutoDisconnectAheadMinutes`). BT A2DP is disconnected after playback if no alarm fires within this window. |

**Accepted Devices multi-select UI:**
- Shows currently paired BT devices (live from `BluetoothAdapter.bondedDevices`) merged with the stored `AcceptedDevice` list.
- Devices that are in DataStore but no longer paired are shown in a separate section **"Nicht mehr gekoppelt"** with their stored name and MAC; the user can deselect/remove them.
- Stored as `List<AcceptedDevice>` (mac + name).

`BLUETOOTH_CONNECT` is requested at runtime when the user opens this screen (or enables one of the BT toggles).

### BT Warning Notification

When any alarm-event is scheduled, a companion `AlarmManager` one-shot is scheduled at `(alarmTime − warningAheadMinutes)`. When this fires:
1. Check `SoundDeviceConfig.checkForSoundDevice`.
2. If enabled: check whether any device in `acceptedDevices` is currently connected (via `BluetoothManager.getConnectedDevices`).
3. If **none connected**: post a notification on channel `bt_warning_channel` with body **"Bitte geeignetes Bluetooth-Gerät verbinden."** (importance = HIGH).
4. The notification fires once per alarm event; it is not repeated.

Notification channel `bt_warning_channel`: importance HIGH, no sound, not `setBypassDnd`.

### BT Auto-Disconnect

When `btAutoDisconnectEnabled` is true, `AlarmForegroundService` performs the following check **immediately after each alarm-event finishes playing**:

1. Load all alarm-sets from the repository.
2. Compute the next trigger time (`TimeUtils.computeTriggerMillis`) for every enabled event of every enabled alarm-set.
3. Find the minimum trigger time that is still in the future (`nextAlarmMs`).
4. If `nextAlarmMs` is null (no future alarms exist) or `(nextAlarmMs − now) > btAutoDisconnectAheadMinutes * 60_000`, disconnect all connected A2DP devices via `BluetoothAdapter.getProfileProxy(A2DP)` and call the hidden `BluetoothA2dp.disconnect(device)` via reflection. Disconnect success/failure is logged individually per device.
5. Otherwise keep BT connected (alarm is imminent).

No periodic `AlarmManager` timer is used. Requires `BLUETOOTH_CONNECT` permission (already declared).

---

### BT Keep-Alive

When any alarm-event is within `keepAwakeAheadMinutes` minutes of its trigger time and `enforceBluetoothConnection = true`:
- A repeating `AlarmManager` (self-rescheduling `setExactAndAllowWhileIdle`) fires every `keepAwakeCycleMinutes`.
- Each firing plays **3 seconds of silent audio** via `AudioTrack` (USAGE_MEDIA, CONTENT_TYPE_MUSIC, stereo 44100 Hz PCM16, zero-sample buffer) so Android routes it through the A2DP path.
- The keep-alive repeating alarm is cancelled after the main alarm fires.

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

### Development Targets

The app can be run on either a physical Android device or an Android Virtual Device (AVD).

| Target | How to run |
|--------|-----------|
| Physical device | Enable USB debugging, connect via adb, use task `Android: Run Debug` |
| AVD (emulator) | Run `scripts/setup-avd.sh` once, then use task `Android: Run on AVD` |

**AVD configuration** (`Alarmissimo_API34`):

| Setting | Value |
|---------|-------|
| System image | `system-images;android-34;google_apis;x86_64` |
| Device profile | `pixel_6` |
| GPU mode | `angle_indirect` (ANGLE → host Vulkan/Intel ANV; true HW rendering) |
| CPU cores | all host cores (`nproc`) |
| RAM | 3072 MB |
| Device frame | disabled |
| Fast-boot snapshots | enabled (subsequent boots ~5 s) |
| Acceleration | KVM (`/dev/kvm`; user must be in `kvm` group) |

**One-time AVD setup:**
```bash
sudo usermod -aG kvm $USER        # then log out and back in
bash scripts/setup-avd.sh
```

**VS Code tasks added for AVD:**

| Task | Purpose |
|------|---------|
| `Android: Setup AVD (one-time)` | Installs emulator + system image, creates AVD |
| `Android: Start AVD` | Launches the emulator in the background |
| `Android: Run on AVD` | Builds APK, auto-boots AVD if needed, installs & runs app, streams logcat |
| `Android: Install & Launch Debug on AVD` | Same as above but starts app in JDWP debug-wait mode on port 5005 |

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


