#!/usr/bin/env bash
# Once-per-worker prerequisites for the automation-studio project.
# Only run the setup commands added here; do not start long-lived servers.
set -euo pipefail

echo "[startup] verifying worker prerequisites"

/usr/bin/time -p mkdir -p "${OPENCODE_WEB_DIR:=/home/runner/work/_temp/omgithub-web}"
/usr/bin/time -p mkdir -p "${RUNNER_TEMP:=/home/runner/work/_temp}"

command -v python3 >/dev/null 2>&1 || { echo "[startup] python3 is required to serve the project" >&2; exit 1; }
command -v chromium >/dev/null 2>&1 || command -v google-chrome >/dev/null 2>&1 || { echo "[startup] chromium/google-chrome is required for capture" >&2; exit 1; }

echo "[startup] prerequisites ready (python3 + chromium available)"
