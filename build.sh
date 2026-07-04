#!/usr/bin/env bash
# Convenience wrapper — delegates to scripts/build.sh
set -euo pipefail
exec bash "$(dirname "$0")/scripts/build.sh" "$@"
