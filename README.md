# Alarmissimo

A modern, progressive web app (PWA) alarm clock for scheduling and triggering audio alarms, designed for private use such as reminding children to prepare early for school or other regular activities.

## Features

- **Configure Multiple Alarm-Sets**: Manage 0 to n alarm-sets with multiple alarm-events
- **Flexible Scheduling**: Set alarms for specific times on selected weekdays
- **Multi-format Audio**: Combine gong sounds, spoken time announcements, and text-to-speech messages
- **German Language Support**: Full UI in German with German voice synthesis for time announcements
- **Automatic Playback**: Alarms play automatically without requiring user interaction
- **Local Storage**: All configurations are saved automatically to browser storage
- **PWA Installation**: Install as a standalone app on Android devices and desktop browsers
- **Business-Like Design**: Clean, professional interface with excellent usability

## Technical Stack

- **Frontend Framework**: Onsen UI 2 (via unpkg CDN)
- **Language**: HTML5 + JavaScript (ES6+, Module Type)
- **Styling**: CSS3 with responsive design
- **State Management**: Browser localStorage API
- **Audio**: Web Audio API + Speech Synthesis API (SpeechSynthesisUtterance)
- **PWA**: Service Worker for offline support

## Browser Support

- **Primary**: Chrome (latest versions)
- **Android**: Chrome mobile for PWA installation
- **Other Browsers**: No support

## Quick Start

### Prerequisites
- Node.js 14+
- Modern Chrome browser
- Python 3 (for dev server)

### Setup
```bash
git clone <repository-url>
cd alarmissimo
npm install
npm run dev
```

Open `http://localhost:8000` in your browser.

### Install as PWA
- Open app in Chrome mobile
- Tap menu → "Install app" or "Add to Home screen"

## Project Structure

```
src/
├── index.html              # Main entry point
├── alarmissimo.js          # Core logic (Doxygen-style comments)
├── alarmissimo.css         # Neutral business-like design
├── alarmissimo.webmanifest # PWA manifest
├── sw.js                   # Service Worker
├── favicon.ico             # App icon
├── icon/                   # PWA icons (192x192, 512x512, etc.)
└── sound/                  # Gong sounds (temple-bell, chime, door-bell)

config/
├── .eslint.config.mjs      # ESLint config (neostandard)
└── .html5validator.yml     # HTML5 validator config

spec/
└── alarmissimo_specification.md  # Complete specification
```

## Development

### Code Quality
```bash
npm run lint              # ESLint validation
npm run validate-html     # HTML5 validation
```

### Code Standards
- HTML5 + JavaScript ES6+
- Doxygen-style comments for all functions
- No external frameworks (only Onsen UI for components)
- ESLint + HTML5 validator

## Configuration

### Alarm-Set
- **Name**: 1-30 characters
- **Enabled**: Boolean (default: true)
- **Weekdays**: Multiple selection (default: all days)
- **Volume**: 0-100% (default: 80%)

### Alarm-Event
- **Time**: 24-hour format (0:00-23:59)
- **Gong**: Predefined sounds or none
- **Time Playback**: Announce time in German (default: true)
- **Message**: 0-300 characters with live counter

## How Alarms Work

When conditions are met (enabled + correct weekday + time), alarms play:
1. Gong sound (if selected) at configured volume
2. Time announcement: *"Es ist 7 Uhr 30."*
3. Message text via text-to-speech (German voice)

## Data Storage

- Automatic saving to browser `localStorage`
- Automatic loading on app startup
- Clearing browser data deletes all alarms

## Limitations

- **Background Timers**: Chrome throttles background timers; keep app visible or pinned
- **Offline**: Requires internet for Speech Synthesis
- **Timezone**: Uses device local time; verify after DST changes
- **Voice**: Falls back to system voice if German (de-DE) unavailable

## License

GNU General Public License v3.0 (GPL-3.0) - See LICENSE file

## Support

- Issues: Create a GitHub issue
- Questions: Review spec/alarmissimo_specification.md

---

**Version**: 1.0.0 | **Status**: Active Development | **Updated**: 26 February 2026
