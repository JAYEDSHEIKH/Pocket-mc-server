#!/usr/bin/env bash
# =============================================================================
# build.sh
#
# Builds PocketCraft debug APK.  Runs setup-android-sdk.sh automatically if
# the SDK is not yet installed.
#
# Output:  output/PocketCraft-debug.apk
#
# Usage:
#   bash scripts/build.sh
#   bash scripts/build.sh --release   (requires keystore — see below)
# =============================================================================
set -euo pipefail

BUILD_TYPE="debug"
for arg in "$@"; do
  [[ "$arg" == "--release" ]] && BUILD_TYPE="release"
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
ANDROID_SDK_DIR="$ROOT_DIR/tools/android-sdk"
OUTPUT_DIR="$ROOT_DIR/output"

header() { echo; echo "▶ $*"; }

# ── Verify Java ───────────────────────────────────────────────────────────────
if ! command -v java &>/dev/null; then
  echo "ERROR: Java not found."
  echo "       Nix packages should provide jdk21_headless — try opening a new shell."
  exit 1
fi
echo "Java: $(java -version 2>&1 | head -1)"

# ── Auto-setup Android SDK if missing ─────────────────────────────────────────
if [ ! -f "$ANDROID_SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
  header "Android SDK not found — running setup first..."
  bash "$SCRIPT_DIR/setup-android-sdk.sh"
fi

# ── Export SDK env vars (needed by Gradle even when local.properties exists) ──
export ANDROID_HOME="$ANDROID_SDK_DIR"
export ANDROID_SDK_ROOT="$ANDROID_SDK_DIR"

# Ensure local.properties points at our SDK
if [ ! -f "$ROOT_DIR/local.properties" ]; then
  echo "sdk.dir=$ANDROID_SDK_DIR" > "$ROOT_DIR/local.properties"
fi

# ── Accept licenses (idempotent) ──────────────────────────────────────────────
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
yes 2>/dev/null | sdkmanager --licenses > /dev/null 2>&1 || true

# ── Build ─────────────────────────────────────────────────────────────────────
cd "$ROOT_DIR"

if [ "$BUILD_TYPE" = "release" ]; then
  header "Building RELEASE APK..."
  echo "  ⚠  Release builds require a signing keystore."
  echo "     Set KEYSTORE_PATH, KEYSTORE_PASS, KEY_ALIAS, KEY_PASS env vars."
  ./gradlew assembleRelease \
    -Pandroid.injected.signing.store.file="${KEYSTORE_PATH:-}" \
    -Pandroid.injected.signing.store.password="${KEYSTORE_PASS:-}" \
    -Pandroid.injected.signing.key.alias="${KEY_ALIAS:-}" \
    -Pandroid.injected.signing.key.password="${KEY_PASS:-}"
  APK_SRC="app/build/outputs/apk/release/app-release.apk"
else
  header "Building DEBUG APK..."
  ./gradlew assembleDebug --stacktrace
  APK_SRC="app/build/outputs/apk/debug/app-debug.apk"
fi

# ── Copy output ───────────────────────────────────────────────────────────────
mkdir -p "$OUTPUT_DIR"
DEST="$OUTPUT_DIR/PocketCraft-${BUILD_TYPE}.apk"
cp "$ROOT_DIR/$APK_SRC" "$DEST"

APK_SIZE=$(du -h "$DEST" | cut -f1)

echo
echo "══════════════════════════════════════════════════════"
echo "  ✅  Build successful!"
echo ""
echo "  APK  → $DEST"
echo "  Size → $APK_SIZE"
echo ""
echo "  Sideload with:"
echo "    adb install -r \"$DEST\""
echo "══════════════════════════════════════════════════════"
