#!/usr/bin/env bash
# =============================================================================
# run-on-avd.sh
# Builds Alarmissimo, boots the AVD if needed, installs & launches the app,
# then streams logcat.  Called by the VS Code task "Android: Run on AVD".
#
# Usage:
#   bash scripts/run-on-avd.sh           # normal run
#   bash scripts/run-on-avd.sh --debug   # start app in JDWP debug-wait mode
# =============================================================================

set -e

ANDROID_SDK_ROOT="/usr/lib/android-sdk"
EMULATOR_BIN="$ANDROID_SDK_ROOT/emulator/emulator"
AVD_NAME="Alarmissimo_API34"
APP_PACKAGE="com.alarmissimo"
MAIN_ACTIVITY="$APP_PACKAGE/.ui.MainActivity"
DEBUG_MODE=false

for arg in "$@"; do
  [[ "$arg" == "--debug" ]] && DEBUG_MODE=true
done

export JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/emulator:$ANDROID_SDK_ROOT/platform-tools:$PATH"

# ── 1. Ensure emulator is installed ──────────────────────────────────────────
if [ ! -f "$EMULATOR_BIN" ]; then
  echo "ERROR: Emulator not found at $EMULATOR_BIN."
  echo "  Run scripts/setup-avd.sh first."
  exit 1
fi

# ── 2. Ensure AVD exists ──────────────────────────────────────────────────────
if ! "$ANDROID_SDK_ROOT/cmdline-tools/13.0/bin/avdmanager" list avd 2>/dev/null | grep -q "Name: $AVD_NAME"; then
  echo "ERROR: AVD '$AVD_NAME' not found."
  echo "  Run scripts/setup-avd.sh first."
  exit 1
fi

# ── 3. Boot emulator if not already running ───────────────────────────────────
if adb devices 2>/dev/null | grep -qE "^emulator-[0-9]+\s+device"; then
  EMULATOR_SERIAL=$(adb devices | grep -E "^emulator-[0-9]+\s+device" | awk '{print $1}' | head -1)
  echo "Emulator already running: $EMULATOR_SERIAL"
else
  echo "Starting AVD '$AVD_NAME'..."
  # QEMU_AUDIO_DRV + PULSE_SERVER: required for emulator 36.x on PipeWire hosts.
  # PipeWire exposes a PulseAudio-compat socket at $XDG_RUNTIME_DIR/pulse/native.
  PULSE_SOCKET="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}/pulse/native"
  QEMU_AUDIO_DRV=pa \
  PULSE_SERVER="unix:${PULSE_SOCKET}" \
  "$EMULATOR_BIN" \
    -avd "$AVD_NAME" \
    -gpu angle_indirect \
    -no-snapshot-load \
    -no-boot-anim \
    -audio pa \
    > /tmp/emulator-avd.log 2>&1 &
  EMULATOR_PID=$!
  echo "Emulator process started (PID $EMULATOR_PID). Waiting for boot..."

  # Wait for adb to see a device
  adb wait-for-device

  # Wait for full boot (sys.boot_completed=1)
  echo "Waiting for Android to finish booting..."
  until adb shell getprop sys.boot_completed 2>/dev/null | grep -q "^1$"; do
    sleep 2
    # Check if the emulator process died
    if ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
      echo "ERROR: Emulator process exited unexpectedly. Check /tmp/emulator-avd.log"
      exit 1
    fi
  done
  echo "AVD booted successfully."
fi

# Dismiss keyguard
adb shell input keyevent 82 2>/dev/null || true

# ── 4. Build & install ────────────────────────────────────────────────────────
echo ""
echo "Building and installing app..."
./gradlew app:installDebug

# ── 5. Launch app ─────────────────────────────────────────────────────────────
echo "Launching $APP_PACKAGE..."
if $DEBUG_MODE; then
  adb shell am start -D -n "$MAIN_ACTIVITY"
  sleep 1
  adb forward tcp:5005 jdwp:$(adb shell pidof -s "$APP_PACKAGE")
  echo "App started in debug-wait mode. Attach debugger on port 5005."
else
  adb shell am start -n "$MAIN_ACTIVITY"
fi

# ── 6. Stream logcat ──────────────────────────────────────────────────────────
# Poll until the app process appears (am start is async; pidof may return empty
# for a second or two after the intent is fired).
echo ""
echo "--- Logcat ($APP_PACKAGE) ---"
APP_PID=""
for i in $(seq 1 20); do
  APP_PID=$(adb shell pidof -s "$APP_PACKAGE" 2>/dev/null | tr -d '\r' || true)
  [ -n "$APP_PID" ] && break
  sleep 1
done
if [ -z "$APP_PID" ]; then
  echo "WARNING: Could not resolve PID for $APP_PACKAGE after 20 s; using tag filter."
  adb logcat -s "AndroidRuntime" "System.err" "$APP_PACKAGE"
else
  adb logcat --pid="$APP_PID"
fi
