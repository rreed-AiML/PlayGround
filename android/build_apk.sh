#!/usr/bin/env bash
# Build the Automa Android APK (debug).
set -euo pipefail
cd "$(dirname "$0")"

export ANDROID_HOME="${ANDROID_HOME:-/usr/local/lib/android/sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}"

echo "==> Assembling debug APK..."
gradle :app:assembleDebug --no-daemon

APK="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
  echo "==> APK built: $APK"
  ls -la "$APK"
else
  echo "==> APK not found at $APK"
  exit 1
fi
