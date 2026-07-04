#!/usr/bin/env bash
# =============================================================================
# install-all.sh
#
# One-shot setup: verifies Java 21, installs Android SDK, downloads the
# bundled aarch64 JRE asset, then builds a debug APK.
#
# Usage:
#   bash scripts/install-all.sh              # full setup + build
#   bash scripts/install-all.sh --no-build  # setup only, skip Gradle
#   bash scripts/install-all.sh --ndk       # also install NDK (~2 GB)
#
# Requirements in Replit:
#   .replit → [nix] packages must include: jdk21_headless, curl, unzip, python3
#   (These are set by default — reopen the Shell if java -version shows wrong version)
# =============================================================================
set -euo pipefail

INSTALL_NDK=false
NO_BUILD=false
for arg in "$@"; do
  [[ "$arg" == "--ndk" ]]      && INSTALL_NDK=true
  [[ "$arg" == "--no-build" ]] && NO_BUILD=true
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

# ── Helpers ───────────────────────────────────────────────────────────────────
header() {
  echo
  echo "╔══════════════════════════════════════════════════════╗"
  printf  "║  ▶  %-50s║\n" "$*"
  echo "╚══════════════════════════════════════════════════════╝"
}
ok()   { echo "  ✅  $*"; }
info() { echo "  ℹ   $*"; }
err()  { echo "  ❌  $*" >&2; }

# ── Step 1: Java version check ────────────────────────────────────────────────
header "Step 1/4 — Java version"

if ! command -v java &>/dev/null; then
  err "Java not found."
  echo
  echo "  Fix: make sure .replit contains:"
  echo "    [nix]"
  echo "    packages = [\"jdk21_headless\", \"curl\", \"unzip\", \"python3\"]"
  echo
  echo "  Then close and reopen a Shell tab so Nix picks up the change."
  exit 1
fi

JAVA_VERSION_LINE=$(java -version 2>&1 | head -1)
echo "  Found: $JAVA_VERSION_LINE"

# Extract the major version number (handles both 1.x and 17/21 style)
JAVA_MAJOR=$(java -version 2>&1 | grep -oP '(?<=version ")[0-9]+' | head -1)
if [[ -z "$JAVA_MAJOR" ]]; then
  JAVA_MAJOR=$(java -version 2>&1 | grep -oP '[0-9]+' | head -1)
fi

if [[ "$JAVA_MAJOR" -lt 17 ]]; then
  err "Java $JAVA_MAJOR is too old. AGP 8.x requires Java 17+."
  echo
  echo "  Fix: set packages = [\"jdk21_headless\", ...] in .replit → [nix]"
  echo "  then reopen the Shell (Nix packages are loaded at shell startup)."
  exit 1
fi

ok "Java $JAVA_MAJOR — OK"

# ── Step 2: Android SDK ───────────────────────────────────────────────────────
header "Step 2/4 — Android SDK"

SDK_ARGS=()
[[ "$INSTALL_NDK" == "true" ]] && SDK_ARGS+=("--ndk")
bash "$SCRIPT_DIR/setup-android-sdk.sh" "${SDK_ARGS[@]}"
ok "Android SDK ready"

# ── Step 3: aarch64 JRE asset ─────────────────────────────────────────────────
header "Step 3/4 — aarch64 OpenJDK 21 asset (bundled inside the APK)"

JRE_ASSET_DIR="$ROOT_DIR/app/src/main/assets/jre"
JRE_ASSET="$JRE_ASSET_DIR/aarch64-jdk21.tar.gz"

if [[ -f "$JRE_ASSET" ]]; then
  JRE_SIZE=$(du -sh "$JRE_ASSET" | cut -f1)
  info "Already present ($JRE_SIZE) — skipping download."
  info "Delete '$JRE_ASSET' and re-run to force a refresh."
else
  mkdir -p "$JRE_ASSET_DIR"

  # ── Try Adoptium (Eclipse Temurin) JSON API first — always returns current LTS ──
  info "Querying Adoptium API for latest Temurin 21 JRE (linux/aarch64)..."
  JRE_URL=""
  if command -v python3 &>/dev/null; then
    JRE_URL=$(curl -fsSL \
      "https://api.adoptium.net/v3/assets/latest/21/jre?architecture=aarch64&image_type=jre&jvm_impl=hotspot&os=linux&vendor=eclipse" \
      | python3 -c "import sys,json; d=json.load(sys.stdin); print(d[0]['binary']['package']['link'])" 2>/dev/null || true)
  fi

  # ── Fallback: known-good Azul Zulu 21 URL ─────────────────────────────────
  if [[ -z "$JRE_URL" ]]; then
    info "Adoptium API unavailable — using pinned Azul Zulu 21 URL."
    JRE_URL="https://cdn.azul.com/zulu/bin/zulu21.42.19-ca-jre21.0.7-linux_aarch64.tar.gz"
  fi

  info "Downloading: $JRE_URL"
  curl -L --retry 3 --progress-bar -o "$JRE_ASSET" "$JRE_URL"
  JRE_SIZE=$(du -sh "$JRE_ASSET" | cut -f1)
  ok "JRE asset downloaded ($JRE_SIZE)"
fi

# ── Step 4: Build ─────────────────────────────────────────────────────────────
if [[ "$NO_BUILD" == "true" ]]; then
  echo
  echo "╔══════════════════════════════════════════════════════╗"
  echo "║  ✅  Setup complete!  (--no-build: Gradle skipped)   ║"
  echo "║                                                      ║"
  echo "║  To build the APK:  bash scripts/build.sh            ║"
  echo "╚══════════════════════════════════════════════════════╝"
else
  header "Step 4/4 — Build debug APK"
  bash "$SCRIPT_DIR/build.sh"
fi
