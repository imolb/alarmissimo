# Alarmissimo Sound Files Directory

This directory should contain the predefined gong sounds for the Alarmissimo PWA.

## Required Sound Files

According to the specification, at least 3 gong sounds of varying styles should be provided:

### 1. temple-bell.mp3
- Style: Temple bell / Meditation chime
- Duration: < 5 seconds
- Format: MP3 or WAV
- Purpose: Soft, calming alarm sound

### 2. chime.mp3
- Style: Electronic chime
- Duration: < 5 seconds
- Format: MP3 or WAV
- Purpose: Standard notification sound

### 3. door-bell.mp3
- Style: Door bell / Buzzer
- Duration: < 5 seconds
- Format: MP3 or WAV
- Purpose: Attention-grabbing alarm sound

## How to Add Sound Files

1. Place your audio files in this directory
2. Name them according to the convention above (or update references in the application)
3. Ensure all files are MP3 or WAV format
4. All sounds must be shorter than 5 seconds in duration

## Audio File Specifications

- **Codec**: MP3 (recommended for web) or WAV
- **Sample Rate**: 44.1 kHz or 48 kHz
- **Channels**: Mono or Stereo
- **Bit Rate**: 128 kbps or higher for MP3
- **Duration**: < 5 seconds per specification

## Usage in Application

The application references these files programmatically. When a user selects a gong sound, they can choose from:
- Temple Bell
- Chime
- Door Bell
- None (no sound)
- Custom device file (user selects their own file)

Audio playback respects the alarm-set's configured volume level (0-100%).
