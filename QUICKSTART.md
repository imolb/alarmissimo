# Quick Start Guide - Alarmissimo PWA

## Step 1: Install Dependencies

```bash
npm install
```

This will install:
- `eslint` - Code quality validation
- `eslint-config-neostandard` - Modern ES6+ linting rules
- `html5validator` - HTML5 validation

## Step 2: Validate Your Code

Before starting development, validate that everything is set up correctly:

```bash
# Check for JavaScript issues
npm run lint

# Validate HTML structure
npm run validate-html
```

## Step 3: Start Development Server

```bash
npm run dev
```

The application will be available at: `http://localhost:8000`

### What the development server does:
- Serves all files statically
- Auto-reloads when you refresh the page
- Allows you to test PWA installation
- No build step required (vanilla JS with modules)

## Step 4: Test the Application

### Desktop Testing
1. Open `http://localhost:8000` in Chrome
2. Open DevTools (F12)
3. Check the "Application" tab for Service Worker and Local Storage
4. Play with creating and editing alarms

### Mobile/PWA Testing
1. Use Chrome DevTools to emulate Android (Device Mode - Ctrl+Shift+M)
2. Or use actual Android device:
   - Ensure your PC and phone are on the same network
   - Find your PC's IP address
   - Open `http://<your-ip>:8000` on your Android Chrome browser
   - Tap menu → "Install app" or "Add to Home screen"

## Step 5: Add Sound Files

The application expects gong sounds in `src/sound/`:

1. `temple-bell.mp3` - Soft, calming alarm sound
2. `chime.mp3` - Standard notification sound
3. `door-bell.mp3` - Attention-grabbing alarm sound

Requirements:
- **Format**: MP3 or WAV
- **Duration**: < 5 seconds
- **Sample rate**: 44.1 kHz or 48 kHz (recommended)
- **Bit rate**: 128 kbps or higher for MP3

**Note**: Placeholder README exists at `src/sound/README.md`

## Step 6: Customize Icons

Application icons are located in `src/icon/`:

- `icon-192x192.svg` - Standard icon
- `icon-512x512.svg` - High-res icon
- `icon-maskable-192x192.svg` - For adaptive icons
- `icon-maskable-512x512.svg` - For adaptive icons
- `icon-96x96.svg` - For shortcuts

Current icons are SVG placeholders. Replace them with your design.

## Development Workflow

1. **Edit code** in `src/` and `config/` folders
2. **Run validation** before committing:
   ```bash
   npm run lint && npm run validate-html
   ```
3. **Test in browser** - changes are visible on refresh (no build step)
4. **Check DevTools**:
   - Application tab → Local Storage (to see saved config)
   - Application tab → Service Workers (background execution)
   - Console tab (for debug logs)

## Project Structure Overview

```
alarmissimo/
├── src/                    # Source code and assets
│   ├── index.html          # Main HTML
│   ├── alarmissimo.js      # Core app logic
│   ├── alarmissimo.css     # Styling
│   ├── sw.js               # Service Worker
│   ├── alarmissimo.webmanifest  # PWA manifest
│   ├── favicon.ico         # App favicon
│   ├── icon/               # PWA icons
│   └── sound/              # Gong sounds
├── config/                 # Configuration files
│   ├── .eslint.config.mjs  # ESLint rules
│   └── .html5validator.yml # HTML5 validator
├── spec/                   # Documentation
│   └── alarmissimo_specification.md
├── package.json            # Dependencies
├── README.md              # Project documentation
├── LICENSE                # GPL 3.0 license
├── .gitignore             # Git exclusions
└── QUICKSTART.md          # This file
```

## Troubleshooting

### Service Worker not registering?
- Check console for errors
- Try clearing cache: DevTools → Application → Storage → Clear site data
- Ensure you're using HTTPS or localhost

### Sound not playing?
- Check file path in `src/sound/` directory
- Verify audio file format (MP3 or WAV)
- Test: Open file path directly in browser to ensure it's accessible
- Check console for errors

### ESLint errors?
```bash
npm run lint
```
Read the errors carefully. Common issues:
- Missing semicolons or improper indentation
- Unused variables
- Var instead of const/let

### HTML5 validation fails?
```bash
npm run validate-html
```
Review the output for specific issues. Usually:
- Unclosed tags
- Invalid attributes
- Proper nesting violations

## Key Features to Test

1. **Create Alarm-Set**: Add a new alarm collection
2. **Create Alarm-Event**: Add time-based alarms
3. **Edit Configuration**: Change alarm settings
4. **Save/Load**: Check that alarms persist after refresh
5. **Play Now**: Test alarm playback immediately
6. **Time Announcements**: Verify German time announcement format

## Next Steps

1. Enable Service Worker in production (currently basic caching)
2. Implement full UI with Onsen UI components
3. Add more gong sound options
4. Create comprehensive UI for all screens
5. Add German localization strings
6. Test on real Android devices
7. Deploy to production server

## Additional Resources

- **Specification**: `spec/alarmissimo_specification.md`
- **Web Audio API**: https://developer.mozilla.org/en-US/docs/Web/API/Web_Audio_API
- **Speech Synthesis**: https://developer.mozilla.org/en-US/docs/Web/API/Web_Speech_API
- **Onsen UI**: https://onsen.io/
- **PWA Basics**: https://web.dev/progressive-web-apps/
- **Service Workers**: https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API

## Need Help?

1. Check `README.md` for comprehensive documentation
2. Review `spec/alarmissimo_specification.md` for detailed requirements
3. Check browser console (F12) for error messages
4. Look at source code comments (Doxygen-style)

---

**Happy coding!** 🎯🔔
