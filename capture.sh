#!/usr/bin/env bash
# Capture desktop and mobile screenshots of a running app URL.
#
# Required environment variables:
#   CAPTURE_URL  URL to open in the browser (exact URL is captured)
#   CAPTURE_DIR  directory where final-desktop.png and final-mobile.png are written
#
# Exit codes:
#   0   success
#   75  temporary navigation / browser infrastructure failure
#   1   script or rendering defect
set -euo pipefail

: "${CAPTURE_URL:?CAPTURE_URL environment variable is required}"
: "${CAPTURE_DIR:?CAPTURE_DIR environment variable is required}"

CHROME="${CHROME_BIN:-/usr/bin/chromium}"
WORK_DIR="$(mktemp -d)"
trap '/usr/bin/time -p rm -rf "$WORK_DIR"' EXIT

/usr/bin/time -p mkdir -p "$CAPTURE_DIR"

if [[ ! -x "$CHROME" ]]; then
  echo "[capture] chromium browser not found at $CHROME" >&2
  exit 75
fi

# Capture one viewport: waits for the page to load/render, then screenshots.
capture_view() {
  local name="$1" width="$2" height="$3" output="$4"
  local screenshot="$WORK_DIR/screenshot.png"
  rm -f "$screenshot"
  echo "[capture] $name view: ${width}x${height} <- $CAPTURE_URL"
  # Chrome writes screenshot.png to its cwd, so run inside WORK_DIR.
  if ! (cd "$WORK_DIR" && /usr/bin/time -p "$CHROME" \
       --headless=new \
       --no-sandbox \
       --disable-gpu \
       --disable-dev-shm-usage \
       --hide-scrollbars \
       --window-size="${width},${height}" \
       --virtual-time-budget=15000 \
       --screenshot \
       "$CAPTURE_URL" >/dev/null 2>&1); then
    echo "[capture] browser/navigation infrastructure failure for $name view" >&2
    exit 75
  fi
  if [[ ! -s "$screenshot" ]]; then
    echo "[capture] no rendered output produced for $name view" >&2
    exit 1
  fi
  /usr/bin/time -p mv "$screenshot" "$output"
  echo "[capture] saved $name view -> $output"
}

capture_view "desktop" 1440 900 "$CAPTURE_DIR/final-desktop.png"
capture_view "mobile" 390 844 "$CAPTURE_DIR/final-mobile.png"

echo "[capture] complete: final-desktop.png and final-mobile.png in $CAPTURE_DIR"
