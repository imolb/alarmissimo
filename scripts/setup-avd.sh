#!/usr/bin/env bash
# =============================================================================
# setup-avd.sh
# One-time setup of an Android Virtual Device (AVD) for Alarmissimo.
#
# Prerequisites (must have run setup-android-sdk.sh first):
#   sudo apt-get install android-sdk google-android-cmdline-tools-13.0-installer \
#     google-android-platform-34-installer google-android-build-tools-34.0.0-installer
#
# This script:
#   1. Installs the Android Emulator via apt
#   2. Installs the API-34 x86_64 system image via sdkmanager
#   3. Creates an AVD named "Alarmissimo_API34" using avdmanager
#   4. Tweaks the AVD config for GPU and KVM acceleration
#
# KVM acceleration (strongly recommended):
#   Make sure your user is in the 'kvm' group:
#     sudo usermod -aG kvm $USER   # then log out and back in
#   Verify: ls -l /dev/kvm
# =============================================================================

set -e

ANDROID_SDK_ROOT="/usr/lib/android-sdk"
SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/13.0/bin/sdkmanager"
AVDMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/13.0/bin/avdmanager"
EMULATOR_BIN="$ANDROID_SDK_ROOT/emulator/emulator"
SYSTEM_IMAGE="system-images;android-34;google_apis;x86_64"
AVD_NAME="Alarmissimo_API34"

export JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/cmdline-tools/13.0/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

echo "=== Alarmissimo: AVD Setup ==="
echo ""

# ── 0. Sanity checks ─────────────────────────────────────────────────────────
if [ ! -d "$ANDROID_SDK_ROOT" ]; then
  echo "ERROR: Android SDK not found at $ANDROID_SDK_ROOT."
  echo "  Run scripts/setup-android-sdk.sh first."
  exit 1
fi

if [ ! -f "$SDKMANAGER" ]; then
  echo "ERROR: sdkmanager not found at $SDKMANAGER."
  echo "  Install: sudo apt-get install google-android-cmdline-tools-13.0-installer"
  exit 1
fi

if [ ! -d "$JAVA_HOME" ]; then
  echo "ERROR: Java 21 not found at $JAVA_HOME."
  echo "  Install: sudo apt-get install openjdk-21-jdk"
  exit 1
fi

# ── 1. Install Android Emulator ───────────────────────────────────────────────
echo "--- Step 1/4: Android Emulator ---"
if [ -f "$EMULATOR_BIN" ]; then
  echo "Emulator already installed at $EMULATOR_BIN — skipping."
else
  echo "Installing google-android-emulator-installer (requires internet)..."
  sudo apt-get install -y google-android-emulator-installer
  # The post-install script downloads & unpacks the emulator binary.
  if [ ! -f "$EMULATOR_BIN" ]; then
    echo "ERROR: Emulator binary still not found after install."
    echo "  Check: dpkg -l google-android-emulator-installer"
    exit 1
  fi
  echo "Emulator installed: $EMULATOR_BIN"
fi

# ── 2. Install system image ───────────────────────────────────────────────────
SYSIMG_DIR="$ANDROID_SDK_ROOT/system-images/android-34/google_apis/x86_64"
echo ""
echo "--- Step 2/4: System Image ($SYSTEM_IMAGE) ---"
if [ -d "$SYSIMG_DIR" ]; then
  echo "System image already present — skipping."
else
  # sdkmanager needs network access and SSL; running it under `sudo` breaks both
  # because sudo strips PATH/HOME/SSL env vars.  Solution: grant the current user
  # write access to the SDK root for the duration of this script, then restore.
  echo "Granting $USER write access to $ANDROID_SDK_ROOT for sdkmanager download..."
  sudo chown -R "$USER":"$USER" "$ANDROID_SDK_ROOT"

  echo "Downloading system image (may take several minutes)..."
  yes | "$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" "$SYSTEM_IMAGE"
  echo "System image installed."

  # Restore SDK ownership to root so other users/system tools aren't affected
  echo "Restoring $ANDROID_SDK_ROOT ownership to root..."
  sudo chown -R root:root "$ANDROID_SDK_ROOT"
  # Keep it group-readable/writable for the android group if present
  sudo chmod -R a+rX "$ANDROID_SDK_ROOT"
fi

# ── 3. KVM access check ───────────────────────────────────────────────────────
echo ""
echo "--- Step 3/4: KVM acceleration ---"
if [ -e /dev/kvm ]; then
  if groups "$USER" | grep -qw kvm || [ -w /dev/kvm ]; then
    echo "KVM: OK — hardware acceleration is available."
  else
    echo "WARNING: /dev/kvm exists but user '$USER' is not in the 'kvm' group."
    echo "  Fix (requires re-login):"
    echo "    sudo usermod -aG kvm \$USER"
    echo "  Continuing without guaranteed KVM access (emulator may be slow)."
  fi
else
  echo "WARNING: /dev/kvm not found. Emulator will run in software mode (slow)."
fi

# ── 4. Create AVD ─────────────────────────────────────────────────────────────
echo ""
echo "--- Step 4/4: AVD creation ---"
if "$AVDMANAGER" list avd 2>/dev/null | grep -q "Name: $AVD_NAME"; then
  echo "AVD '$AVD_NAME' already exists — skipping creation."
else
  echo "Creating AVD '$AVD_NAME'..."
  echo "no" | "$AVDMANAGER" create avd \
    --name "$AVD_NAME" \
    --package "$SYSTEM_IMAGE" \
    --device "pixel_6" \
    --force
  echo "AVD '$AVD_NAME' created."
fi

# ── 4a. Tune AVD config ────────────────────────────────────────────────────────
AVD_DIR="$HOME/.android/avd/${AVD_NAME}.avd"
if [ -d "$AVD_DIR" ] && [ -f "$AVD_DIR/config.ini" ]; then
  CONFIG="$AVD_DIR/config.ini"

  # GPU — angle_indirect: ANGLE → host Vulkan (Intel ANV), true HW rendering
  sed -i '/^hw\.gpu\.enabled/d' "$CONFIG"
  sed -i '/^hw\.gpu\.mode/d'    "$CONFIG"
  echo "hw.gpu.enabled = yes"          >> "$CONFIG"
  echo "hw.gpu.mode = angle_indirect" >> "$CONFIG"

  # CPU — use all available host cores
  HOST_CORES=$(nproc)
  sed -i "s/^hw\.cpu\.ncore\s*=.*/hw.cpu.ncore = $HOST_CORES/" "$CONFIG"

  # RAM — 3 GB for smooth Compose rendering and fewer GC pauses
  sed -i 's/^hw\.ramSize\s*=.*/hw.ramSize = 3072/' "$CONFIG"

  # Audio output (required for alarm sounds)
  sed -i '/^hw\.audioOutput/d' "$CONFIG"
  sed -i '/^hw\.audioInput/d'  "$CONFIG"
  # 200 ms latency prevents IAudioFlinger::createTrack timeouts caused by
  # QEMU audio stalls under PipeWire on Linux hosts.
  echo "hw.audioOutput = yes"          >> "$CONFIG"
  echo "hw.audioInput  = yes"          >> "$CONFIG"
  echo "hw.audioOutput.latency = 200"  >> "$CONFIG"

  # No cosmetic device frame (saves GPU composite work)
  sed -i 's/^showDeviceFrame\s*=.*/showDeviceFrame = no/' "$CONFIG"

  # Fast-boot snapshots: subsequent boots take ~5 s instead of ~60 s
  sed -i 's/^fastboot\.forceColdBoot\s*=.*/fastboot.forceColdBoot = no/'  "$CONFIG"
  sed -i 's/^fastboot\.forceFastBoot\s*=.*/fastboot.forceFastBoot = yes/' "$CONFIG"
  sed -i 's/^firstboot\.saveToLocalSnapshot\s*=.*/firstboot.saveToLocalSnapshot = yes/'   "$CONFIG"
  sed -i 's/^firstboot\.bootFromLocalSnapshot\s*=.*/firstboot.bootFromLocalSnapshot = yes/' "$CONFIG"

  echo "AVD config tuned (GPU=angle_indirect, CPU=$HOST_CORES cores, RAM=3072 MB, snapshots=on, audio=on)."
fi

# ── Done ───────────────────────────────────────────────────────────────────────
echo ""
echo "=== AVD setup complete ==="
echo ""
echo "You can now use the VS Code tasks:"
echo "  'Android: Start AVD'    — launches the emulator"
echo "  'Android: Run on AVD'   — builds, waits for boot, installs & runs the app"
echo ""
echo "Or start manually:"
echo "  ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT $EMULATOR_BIN -avd $AVD_NAME -gpu angle_indirect -no-boot-anim"
