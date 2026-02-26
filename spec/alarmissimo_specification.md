# Alarmissimo - Progressive Web App Specification

## Overview

This document serves as both a formal specification and a prompt for an AI agent to create the app.

**Target Application:** Create a progressive web app (PWA) named **"Alarmissimo"** that functions as an alarm clock for scheduling and triggering audio alarms.

**Prerequisites for Implementation:** Assume you are an experienced software developer with 20+ years of experience in building web applications.

### Use Case

The app shall be used in private situations, such as reminding children to prepare early for school or other regular activities.

Unlike a traditional alarm clock, **Alarmissimo** has the following distinct characteristics:
- The alarm sound plays only once (no snooze functionality)
- No user interaction is required to stop the alarm
- Combined notification: audio (gong sound) + spoken time (German) + text-to-speech message

### Technical Format

#### Project Structure

The web app shall consist of the following files in the `src` folder:
- `index.html`
- `alarmissimo.js`
- `alarmissimo.css`  
- `alarmissimo.webmanifest`
- `favicon.ico`

Within the `src` folder:
- The `icon` folder shall contain all images
- The `sound` folder shall contain predefined gong sound files:
  - At least 3 gong sounds of varying styles (e.g., temple bell, chime, door bell)
  - Audio format: MP3 or WAV
  - All sounds shall be shorter than 10 seconds

#### Framework & Technologies

- The app shall use the **Onsen UI 2** framework via unpkg CDN
- The `index.html` shall contain **HTML5**
- The `alarmissimo.js` shall contain **JavaScript**
- The `alarmissimo.css` shall contain **CSS**
- The app shall only support recent Chrome browser versions (no support for other browsers or older Chrome versions)

### Code Guidelines

The generated code shall comply with:
- **HTML5validator**
- **ESLint** (neostandard, latest ECMAScript, type: module)
- Comments in **Doxygen-style** with description of function, inputs, and outputs

## Features & Content

### Core Concept

The progressive web app shall provide the following core function:

- The app shall allow users to configure and manage **0 to n alarm-sets**

### Alarm-Set

An **alarm-set** is defined as a group of 1 to n alarm-events. The alarm-events shall be ordered by the time property of the alarm-events.

#### Properties

An alarm-set shall have the following properties:

| Property | Type | Constraints | Default |
|----------|------|-------------|---------|
| `name` | Text | 1 to 30 characters | empty |
| `enabled` | Boolean | true or false | true |
| `weekdays` | Selection | 1 to 7 weekdays (toggle chip buttons) | all selected |
| `audio volume` | Percentage | 0 to 100 % | 80 % |

### Alarm-Event

An **alarm-event** is a single timed alarm within an alarm-set.

#### Properties

An alarm-event shall have the following properties:

| Property | Type | Constraints | Default |
|----------|------|-------------|---------|
| `time` | Time of day | 0:00 to 23:59 | current time |
| `gong` | Audio | predefined gong sounds, device file, or none (no upload support) | none |
| `time playback` | Boolean | true or false | true |
| `message` | Text | 0 to 300 characters (with real-time character counter during editing) | empty |

### Alarm Functionality

The app shall execute alarms according to the following logic:

**Trigger Condition:** If the alarm-set is enabled and the selected weekdays match the actual weekday, then at the time of the alarm-event, the app shall play with the audio volume of the alarm-set:

1. The gong sound
2. The message as sound using the API `SpeechSynthesisUtterance`

**Time Announcement:** If the `time playback` property is enabled, the spoken announcement shall begin with the current time in German format: *"Es ist <time> Uhr."* (e.g., "Es ist 7 Uhr 30.") followed by the message text.

**Multiple Alarms:** If two alarm-events trigger at the same time, play sequentially (order does not matter).

**Empty Alarm Scenario:** If both `gong` is set to "none" and `message` is empty, but `time playback` is enabled, the app shall speak only the current time in German.

**Implementation Notes:**
- Minute precision is sufficient
- Chrome limits timers in background; try to keep it alive with a foreground wake approach (still limited)
- Automatically pick a German voice (de-DE)
- No "sleep resume" function needed
    
## User Interface & Layout

### Design Guidelines

- For rendering assume around **4 alarm-sets** and around **5 alarm-events** within each alarm-set
- Choose a **neutral business-like style**
- **The UI shall be in German**

### Screen Layout

#### Main Screen (Dashboard)

- **Purpose:** Display upcoming alarms
- **Content:** List the upcoming ordered alarm-events within the next 24 hours (earliest event first on top)
- **Primary Action:** Gear-icon button opens the configuration screen

#### Configuration Screen

- **Content:** List all alarm-sets identified by the name
- **Actions:** Each alarm-set has chips: "edit", "delete", "duplicate"
- **Edit Action:** Clicking "edit" opens the alarm-set screen

#### Alarm-Set Screen

- **Content Display:** List the alarm-set properties (all properties shall be editable)
- **Alarm-Events:** Listed by time and message (truncated if long), each with chips: "edit", "delete", "duplicate"
- **Weekday Selection:** Toggle chip buttons for weekday selection
- **Volume Control:** Slider showing the audio volume value
- **Edit Action:** Clicking "edit" opens the alarm-event screen

#### Alarm-Event Screen

- **Content:** List all alarm-event properties (all editable)
- **Time Input:** Wheel-style timepicker
- **Test Function:** "Play now" button plays the alarm event immediately for testing purposes

### Deletion & Duplication

- The **"delete"** buttons shall remove the item after showing an additional confirmation dialog
- The **"duplicate"** button shall create an identical copy of the item

## Supporting Files & Environment

### GitHub Repository Structure

The project shall be stored in github.com with the following files:

- `README.md`
- `package.json`
- `LICENSE` (using GPL 3.0)
- `config/.html5validator.yml`
- `config/.eslint.config.mjs`

## Background Functions & Data Persistence

### Local Storage

- The configuration of alarm-sets and alarm-events shall be **stored automatically on change** in the browser using the `localStorage` API
- Configuration shall be **read at initialization** from local storage

### Configuration Management

- The app shall allow users to manage alarm-set and alarm-event configurations using modern, intuitive GUI elements (sliders, toggles, text inputs, pickers)

### Deployment & Installation

- The app shall be **installable on Android devices** as a standalone PWA (windowed/non-fullscreen mode)
- **Offline support is not required**; the app requires an active internet connection
- The app may be installed on desktop Chrome as well (Android is the primary target)

## Error Handling & Edge Cases

### Audio Playback Failures

- If the selected gong file cannot be loaded or played, the alarm shall still proceed to voice announcement
- If `SpeechSynthesisUtterance` fails or is not supported, the alarm shall be marked as triggered (data consistency)
- If no German voice (de-DE) is available, fall back to the system default voice

### Data Persistence Failures

- If `localStorage` quota is exceeded, show a user warning and continue without persisting new changes
- If `localStorage` is unavailable (private browsing in some browsers), the app shall operate in memory only during the session

### Browser Limitations

- PWAs cannot reliably trigger alarms when completely closed; users should keep the app in the background or pinned
- Background timers may be throttled or paused—this is a Chrome limitation beyond app control
- Minute-precision timers may drift slightly due to OS scheduling

## Timezone & Time Handling

- The app shall use the **device's local timezone** for all time calculations
- The "next 24 hours" calculation for the dashboard shall be relative to the device's current local time
- If an alarm is set for 03:00 and a daylight saving time transition occurs, behavior is undefined (user should verify alarms after timezone changes)

## Visual Feedback & Notifications

- When an alarm triggers, the app shall:
  - **Maintain focus** or bring the window to foreground if possible
  - **Play the audio notification** (gong + speech)
  - Visually indicate that an alarm has triggered by:
    - Adding a visual indicator (e.g., highlighting, timestamp, "recently triggered" badge) to the alarm-event in the dashboard
    - The indicator shall persist for at least 5 minutes or until the user navigates away from the dashboard
- No browser notification API is required (audio is the primary feedback mechanism)
- The "Play now" test button shall immediately play the selected gong and message without marking the alarm as triggered

## Data Management & Scalability

- The app shall support configurations with:
  - Minimum: 1 alarm-set with 1 alarm-event
  - Recommended: ~4 alarm-sets with ~5 alarm-events each (UI designed for this scale)
  - Maximum: No hard limit, but UI may scroll if >10 alarm-sets are configured
- No backup, export, or import functionality is required in this version
- Clearing browser data will permanently delete all alarm configurations

## Sanity Check

**Did you understand all instructions or is there any ambiguity to clarify or more details needed?**