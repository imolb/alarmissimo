#!/usr/bin/env bash
# =============================================================================
# setup-android-sdk.sh
# One-time post-install step after installing the following deb packages:
#
#   sudo apt-get install \
#     android-sdk \
#     android-sdk-platform-23 \
#     google-android-cmdline-tools-13.0-installer \
#     google-android-platform-34-installer \
#     google-android-build-tools-34.0.0-installer
#
# All SDK components are provided by the debs above.  This script only:
#   1. Verifies Java 21 is present
#   2. Accepts the Android SDK licenses via sdkmanager
# =============================================================================

set -e

ANDROID_SDK_ROOT="/usr/lib/android-sdk"
SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/13.0/bin/sdkmanager"

echo "=== Alarmissimo: Android SDK license acceptance ==="

# ── 1. Verify Java 21 ────────────────────────────────────────────────────────
JAVA_21="/usr/lib/jvm/java-21-openjdk-amd64"
if [ ! -d "$JAVA_21" ]; then
  echo "ERROR: Java 21 not found at $JAVA_21. Install it first:"
  echo "  sudo apt-get install openjdk-21-jdk"
  exit 1
fi
export JAVA_HOME="$JAVA_21"
export PATH="$JAVA_HOME/bin:$PATH"
echo "Java: $(java -version 2>&1 | head -1)"

# ── 2. Verify sdkmanager is present ──────────────────────────────────────────
if [ ! -f "$SDKMANAGER" ]; then
  echo "ERROR: sdkmanager not found at $SDKMANAGER"
  echo "  Install: sudo apt-get install google-android-cmdline-tools-13.0-installer"
  exit 1
fi

# ── 3. Accept SDK licenses ───────────────────────────────────────────────────
echo ""
echo "--- Accepting SDK licenses ---"
yes | sudo "$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" --licenses > /dev/null 2>&1 || true
echo "Licenses accepted."

echo ""
echo "=== Setup complete ==="
echo ""
echo "Add the following to your ~/.bashrc or ~/.zshrc:"
echo ""
echo "  export ANDROID_HOME=/usr/lib/android-sdk"
echo "  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64"
echo "  export PATH=\"\$ANDROID_HOME/cmdline-tools/13.0/bin:\$ANDROID_HOME/platform-tools:\$JAVA_HOME/bin:\$PATH\""
