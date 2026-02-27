#!/usr/bin/env bash
# =============================================================================
# setup-android-sdk.sh
# Installs Android command-line tools, SDK platform, and build tools.
# Run once to set up the Android development environment.
# =============================================================================

set -e

ANDROID_SDK_ROOT="$HOME/Android/Sdk"
CMDLINE_TOOLS_VERSION="11076708"  # cmdline-tools release 9.0 (latest stable)
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"

API_LEVEL="35"
BUILD_TOOLS_VERSION="35.0.0"

echo "=== Alarmissimo: Android SDK Setup ==="
echo "SDK root: $ANDROID_SDK_ROOT"

# ── 1. Java 21 ────────────────────────────────────────────────────────────────
echo ""
echo "--- Checking Java 21 ---"
JAVA_21="/usr/lib/jvm/java-21-openjdk-amd64"
if [ ! -d "$JAVA_21" ]; then
  echo "Java 21 not found at $JAVA_21. Installing..."
  sudo apt-get install -y openjdk-21-jdk
else
  echo "Java 21 found: $JAVA_21"
fi

export JAVA_HOME="$JAVA_21"
export PATH="$JAVA_HOME/bin:$PATH"
java -version

# ── 2. Create SDK directory ───────────────────────────────────────────────────
mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"

# ── 3. Download cmdline-tools (if not already present) ───────────────────────
echo ""
echo "--- Downloading Android cmdline-tools ---"
TOOLS_DIR="$ANDROID_SDK_ROOT/cmdline-tools/latest"
if [ -d "$TOOLS_DIR/bin" ]; then
  echo "cmdline-tools already installed at $TOOLS_DIR"
else
  TMP_ZIP="/tmp/cmdline-tools.zip"
  curl -fL "$CMDLINE_TOOLS_URL" -o "$TMP_ZIP"
  unzip -q "$TMP_ZIP" -d "$ANDROID_SDK_ROOT/cmdline-tools"
  # The zip extracts as "cmdline-tools", rename to "latest"
  mv "$ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools" "$TOOLS_DIR" 2>/dev/null || true
  rm -f "$TMP_ZIP"
  echo "cmdline-tools installed."
fi

export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$TOOLS_DIR/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

# ── 4. Accept licenses ────────────────────────────────────────────────────────
echo ""
echo "--- Accepting SDK licenses ---"
yes | sdkmanager --licenses > /dev/null 2>&1 || true

# ── 5. Install SDK components ─────────────────────────────────────────────────
echo ""
echo "--- Installing SDK platform android-${API_LEVEL} and build-tools ${BUILD_TOOLS_VERSION} ---"
sdkmanager \
  "platform-tools" \
  "platforms;android-${API_LEVEL}" \
  "build-tools;${BUILD_TOOLS_VERSION}" \
  "extras;google;m2repository" \
  "extras;android;m2repository"

# Install older platform for min SDK 23 (needed for lint)
sdkmanager "platforms;android-23"

echo ""
echo "=== Android SDK setup complete ==="
echo ""
echo "Add the following lines to your ~/.bashrc or ~/.zshrc:"
echo ""
echo "  export ANDROID_HOME=\"\$HOME/Android/Sdk\""
echo "  export JAVA_HOME=\"/usr/lib/jvm/java-21-openjdk-amd64\""
echo "  export PATH=\"\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$JAVA_HOME/bin:\$PATH\""
