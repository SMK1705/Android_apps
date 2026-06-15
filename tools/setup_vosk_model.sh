#!/usr/bin/env bash
# Convenience wrapper around setup_vosk_model.py for Unix shells.
# Verifies python3 + adb are available, then forwards all args to the Python installer.
#
# Usage:
#   tools/setup_vosk_model.sh                 # default small Indian-English model
#   tools/setup_vosk_model.sh --device R5CR   # target a specific adb serial
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! command -v python3 >/dev/null 2>&1; then
  echo "error: python3 not found on PATH." >&2
  exit 1
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "error: adb not found on PATH. Install Android platform-tools." >&2
  exit 1
fi

exec python3 "${script_dir}/setup_vosk_model.py" "$@"
