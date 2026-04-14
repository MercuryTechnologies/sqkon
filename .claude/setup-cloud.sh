#!/bin/bash
set -euo pipefail

# Cloud environment setup script for Sqkon
# Installs Android SDK and fixes Gradle daemon SSL issues in Claude Code cloud environments.
# This script is idempotent and safe to run on every session (new + resumed).

# Only run in Claude Code cloud environments
if [ -z "${CLAUDE_CODE_REMOTE:-}" ] || [ "$CLAUDE_CODE_REMOTE" != "true" ]; then
  exit 0
fi

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_SDK_ROOT="$HOME/android-sdk"
SYSTEM_CACERTS="/etc/ssl/certs/java/cacerts"
GRADLE_PROPS="$HOME/.gradle/gradle.properties"
GOOGLE_REPO="https://dl.google.com/android/repository"

# ---------------------------------------------------------------------------
# Step 1: Fix SSL trust store for Gradle daemon
# ---------------------------------------------------------------------------
# The cloud environment uses an HTTPS-intercepting proxy whose CA is in the
# system cacerts but NOT in the auto-downloaded Azul Zulu JDK's cacerts.
# Tell all JVMs to use the system trust store.

export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=${SYSTEM_CACERTS} -Djavax.net.ssl.trustStorePassword=changeit"

mkdir -p "$HOME/.gradle"
if ! grep -q "javax.net.ssl.trustStore" "$GRADLE_PROPS" 2>/dev/null; then
  cat >> "$GRADLE_PROPS" <<EOF
systemProp.javax.net.ssl.trustStore=${SYSTEM_CACERTS}
systemProp.javax.net.ssl.trustStorePassword=changeit
EOF
fi

# Patch any already-downloaded Azul JDK cacerts with the system cacerts
for jdk_cacerts in "$HOME"/.gradle/jdks/*/lib/security/cacerts; do
  if [ -f "$jdk_cacerts" ] && [ ! -L "$jdk_cacerts" ]; then
    cp "$SYSTEM_CACERTS" "$jdk_cacerts"
  fi
done

# ---------------------------------------------------------------------------
# Step 2: Stop stale Gradle daemons + clear poisoned config cache
# ---------------------------------------------------------------------------
cd "$REPO_DIR"
./gradlew --stop 2>/dev/null || true
rm -rf "$REPO_DIR/.gradle/configuration-cache"

# ---------------------------------------------------------------------------
# Step 3: Install Android SDK
# ---------------------------------------------------------------------------
# sdkmanager (Java-based) cannot reach Google's servers through the cloud
# proxy, so we download SDK packages directly with curl and install them
# into the standard SDK directory structure.

install_sdk_package() {
  local package_dir="$1"   # e.g. "platforms/android-36"
  local zip_name="$2"      # e.g. "platform-36_r02.zip"
  local dest="$ANDROID_SDK_ROOT/$package_dir"

  if [ -d "$dest" ]; then
    return 0
  fi

  echo "Installing Android SDK: $package_dir ..."
  curl --retry 3 --retry-delay 2 -fsSL -o /tmp/sdk-pkg.zip "$GOOGLE_REPO/$zip_name"
  mkdir -p "$(dirname "$dest")"
  unzip -q -o /tmp/sdk-pkg.zip -d /tmp/sdk-pkg-tmp

  # The zip may contain a single directory — move its contents to dest
  local extracted
  extracted="$(find /tmp/sdk-pkg-tmp -mindepth 1 -maxdepth 1 -type d | head -1)"
  if [ -n "$extracted" ]; then
    mv "$extracted" "$dest"
  else
    mkdir -p "$dest"
    mv /tmp/sdk-pkg-tmp/* "$dest/"
  fi

  rm -rf /tmp/sdk-pkg.zip /tmp/sdk-pkg-tmp
}

# Install cmdline-tools (for license acceptance)
if [ ! -f "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "Installing Android SDK command-line tools..."
  mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
  curl --retry 3 --retry-delay 2 -fsSL -o /tmp/cmdline-tools.zip \
    "$GOOGLE_REPO/commandlinetools-linux-11076708_latest.zip"
  unzip -q -o /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-tmp
  mv /tmp/cmdline-tools-tmp/cmdline-tools "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-tools-tmp
fi

# Install SDK packages directly via curl
install_sdk_package "platforms/android-36" "platform-36_r02.zip"
install_sdk_package "build-tools/36.0.0"  "build-tools_r36-linux.zip"
install_sdk_package "platform-tools"      "platform-tools_r37.0.0-linux.zip"

# Accept licenses (write license hashes directly — sdkmanager needs network)
mkdir -p "$ANDROID_SDK_ROOT/licenses"
echo -e "\n24333f8a63b6825ea9c5514f83c2829b004d1fee" > "$ANDROID_SDK_ROOT/licenses/android-sdk-license"
echo -e "\n84831b9409646a918e30573bab4c9c91346d8abd" > "$ANDROID_SDK_ROOT/licenses/android-sdk-preview-license"

export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

# Persist environment variables for the Claude Code session
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export ANDROID_HOME=$ANDROID_SDK_ROOT" >> "$CLAUDE_ENV_FILE"
  echo "export PATH=$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:\$PATH" >> "$CLAUDE_ENV_FILE"
fi

# Also persist for subshells
if ! grep -q "ANDROID_HOME" "$HOME/.bashrc" 2>/dev/null; then
  cat >> "$HOME/.bashrc" <<'BASHRC'
export ANDROID_HOME="$HOME/android-sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
BASHRC
fi

# ---------------------------------------------------------------------------
# Step 4: Pre-warm Gradle (download dependencies, compile, run tests)
# ---------------------------------------------------------------------------
cd "$REPO_DIR"
./gradlew :library:jvmTest 2>&1 || true

echo "Cloud environment setup complete."
