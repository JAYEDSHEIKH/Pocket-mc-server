#!/usr/bin/env bash
# =============================================================================
# generate-keystore.sh
#
# Generates a release signing keystore for PocketCraft and prints the
# base64-encoded value to add to GitHub Secrets.
#
# Run ONCE locally.  Keep the output keystore and password PRIVATE.
# Never commit the keystore to version control.
#
# Usage:
#   bash scripts/generate-keystore.sh
# =============================================================================
set -euo pipefail

KEYSTORE_DIR="$(dirname "$(dirname "$0")")/signing"
KEYSTORE_FILE="$KEYSTORE_DIR/pocketcraft-release.keystore"

if [[ -f "$KEYSTORE_FILE" ]]; then
  echo "Keystore already exists at $KEYSTORE_FILE"
  echo "Delete it first if you want to regenerate."
  exit 0
fi

mkdir -p "$KEYSTORE_DIR"
echo ".gitignore" && echo "*.keystore" > "$KEYSTORE_DIR/.gitignore"

# Prompt for passwords
read -rsp "Enter keystore password (min 6 chars): " KS_PASS; echo
read -rsp "Confirm keystore password: "              KS_PASS2; echo
if [[ "$KS_PASS" != "$KS_PASS2" ]]; then echo "Passwords do not match." ; exit 1; fi

read -rsp "Enter key password (or press Enter to use same as keystore): " KEY_PASS; echo
[[ -z "$KEY_PASS" ]] && KEY_PASS="$KS_PASS"

keytool -genkeypair \
  -keystore "$KEYSTORE_FILE" \
  -alias pocketcraft \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storepass "$KS_PASS" \
  -keypass "$KEY_PASS" \
  -dname "CN=PocketCraft, OU=App, O=PocketCraft, L=Unknown, ST=Unknown, C=US"

echo
echo "╔══════════════════════════════════════════════════════════╗"
echo "║  ✅  Keystore generated!                                  ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo
echo "Add these 4 secrets to your GitHub repository:"
echo "  Settings → Secrets and variables → Actions → New repository secret"
echo
echo "  KEYSTORE_BASE64  =$(base64 -w 0 "$KEYSTORE_FILE")"
echo "  KEYSTORE_PASS    =$KS_PASS"
echo "  KEY_ALIAS        =pocketcraft"
echo "  KEY_PASS         =$KEY_PASS"
echo
echo "⚠️  IMPORTANT: Store the keystore file ($KEYSTORE_FILE) safely."
echo "   If you lose it, you cannot publish updates to the same app identity."
